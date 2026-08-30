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
  "$sonar_url/api/measures/component?component=$project_key&metricKeys=new_coverage,new_duplicated_lines_density,new_lines,new_lines_to_cover,new_uncovered_lines,new_uncovered_conditions,new_duplicated_lines,new_duplicated_blocks")

metric_value() {
  local metric=$1
  jq -r --arg metric "$metric" '
    [.component.measures[] | select(.metric == $metric)] |
    if length == 0 then "__MISSING__"
    elif length > 1 then "__DUPLICATE__"
    elif .[0].period.value == null then "__NO_VALUE__"
    else (.[0].period.value | tostring)
    end
  ' <<<"$measures"
}

require_metric_value() {
  local metric=$1
  local value=$2
  local expected=$3
  if [[ "$value" == "__MISSING__" ]]; then
    echo "Missing Sonar metric $metric" >&2
    exit 1
  fi
  if [[ ! "$value" =~ ^-?[0-9]+([.][0-9]+)?$ ]]; then
    echo "Expected numeric Sonar metric $metric, observed $value" >&2
    exit 1
  fi
  awk -v value="$value" -v expected="$expected" \
    'BEGIN { exit !(value == expected) }' || {
    echo "Expected $metric=$expected, observed $value" >&2
    exit 1
  }
}

new_coverage=$(metric_value new_coverage)
new_duplication=$(metric_value new_duplicated_lines_density)
new_lines=$(metric_value new_lines)
new_lines_to_cover=$(metric_value new_lines_to_cover)
new_uncovered_lines=$(metric_value new_uncovered_lines)
new_uncovered_conditions=$(metric_value new_uncovered_conditions)
new_duplicated_lines=$(metric_value new_duplicated_lines)
new_duplicated_blocks=$(metric_value new_duplicated_blocks)

[[ "$quality_status" == "OK" ]] || {
  echo "Quality Gate is $quality_status" >&2
  exit 1
}
[[ "$new_issues" == "0" ]] || {
  echo "Expected zero new issues, observed $new_issues" >&2
  exit 1
}

if [[ "$new_coverage" == "__MISSING__" ]]; then
  require_metric_value new_lines_to_cover "$new_lines_to_cover" 0
  require_metric_value new_uncovered_lines "$new_uncovered_lines" 0
  require_metric_value new_uncovered_conditions "$new_uncovered_conditions" 0
  new_coverage="N/A (0/0)"
else
  require_metric_value new_coverage "$new_coverage" 100
fi

if [[ "$new_duplication" == "__MISSING__" ]]; then
  require_metric_value new_lines "$new_lines" 0
  require_metric_value new_duplicated_lines "$new_duplicated_lines" 0
  require_metric_value new_duplicated_blocks "$new_duplicated_blocks" 0
  new_duplication="N/A (0/0)"
else
  require_metric_value new_duplicated_lines_density "$new_duplication" 0
fi

printf 'Quality Gate=%s new_issues=%s new_coverage=%s new_duplication=%s new_lines=%s new_lines_to_cover=%s new_uncovered_lines=%s new_uncovered_conditions=%s new_duplicated_lines=%s new_duplicated_blocks=%s\n' \
  "$quality_status" "$new_issues" "$new_coverage" "$new_duplication" \
  "$new_lines" "$new_lines_to_cover" "$new_uncovered_lines" \
  "$new_uncovered_conditions" "$new_duplicated_lines" \
  "$new_duplicated_blocks"
