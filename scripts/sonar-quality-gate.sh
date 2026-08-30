#!/usr/bin/env bash
set -euo pipefail

base_sha=${1:?base commit SHA is required}
sonar_url=${SONAR_HOST_URL:-http://localhost:9000}
project_key=implement-approved-plan-fixture
candidate_sha=$(git rev-parse HEAD)
analysis_root=$(mktemp -d)

cleanup() {
  rm -rf "$analysis_root"
}
trap cleanup EXIT

for attempt in $(seq 1 60); do
  status=$(curl --silent --fail "$sonar_url/api/system/status" | jq -r '.status') || true
  if [[ "$status" == "UP" ]]; then
    break
  fi
  if [[ "$attempt" == "60" ]]; then
    echo "SonarQube did not become ready" >&2
    exit 1
  fi
  sleep 2
done

token_name="fixture-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
token=$(curl --silent --fail --user admin:admin --request POST \
  --data-urlencode "name=$token_name" \
  "$sonar_url/api/user_tokens/generate" | jq -r '.token')

git clone --quiet --no-local . "$analysis_root/baseline"
git -C "$analysis_root/baseline" checkout --quiet --detach "$base_sha"

(
  cd "$analysis_root/baseline"
  ./mvnw --batch-mode clean verify sonar:sonar \
    -Dsonar.host.url="$sonar_url" \
    -Dsonar.token="$token" \
    -Dsonar.projectKey="$project_key" \
    -Dsonar.projectVersion="baseline-$base_sha" \
    -Dsonar.qualitygate.wait=true
)

curl --silent --fail --user "$token:" --request POST \
  --data-urlencode "project=$project_key" \
  --data-urlencode "type=PREVIOUS_VERSION" \
  "$sonar_url/api/new_code_periods/set" >/dev/null

./mvnw --batch-mode clean verify sonar:sonar \
  -Dsonar.host.url="$sonar_url" \
  -Dsonar.token="$token" \
  -Dsonar.projectKey="$project_key" \
  -Dsonar.projectVersion="candidate-$candidate_sha" \
  -Dsonar.qualitygate.wait=true

quality_status=$(curl --silent --fail --user "$token:" \
  "$sonar_url/api/qualitygates/project_status?projectKey=$project_key" |
  jq -r '.projectStatus.status')
new_issues=$(curl --silent --fail --user "$token:" \
  "$sonar_url/api/issues/search?componentKeys=$project_key&resolved=false&sinceLeakPeriod=true&ps=1" |
  jq -r '.total')
measures=$(curl --silent --fail --user "$token:" \
  "$sonar_url/api/measures/component?component=$project_key&metricKeys=new_coverage,new_duplicated_lines_density")
new_coverage=$(jq -r '.component.measures[] | select(.metric == "new_coverage") | .period.value' <<<"$measures")
new_duplication=$(jq -r '.component.measures[] | select(.metric == "new_duplicated_lines_density") | .period.value' <<<"$measures")

[[ "$quality_status" == "OK" ]] || {
  echo "Quality Gate is $quality_status" >&2
  exit 1
}
[[ "$new_issues" == "0" ]] || {
  echo "Expected zero new issues, observed $new_issues" >&2
  exit 1
}
awk -v value="$new_coverage" 'BEGIN { exit !(value == 100) }' || {
  echo "Expected 100% new coverage, observed $new_coverage" >&2
  exit 1
}
awk -v value="$new_duplication" 'BEGIN { exit !(value == 0) }' || {
  echo "Expected 0% new duplication, observed $new_duplication" >&2
  exit 1
}

printf 'Quality Gate=%s new_issues=%s new_coverage=%s new_duplication=%s\n' \
  "$quality_status" "$new_issues" "$new_coverage" "$new_duplication"

