# Implement Approved Plan Fixture

Public Apache-2.0 fixture for exercising the `$implement-approved-plan` skill against a real Java delivery workflow.

## Laboratory baseline

The immutable `fixture-v1` tag contains a deliberately small Picocli report command. Its behavior is covered completely, while its implementation retains two documented design risks for the pilot to evaluate:

- the report method is intentionally long enough for the Habit Java/PMD sensor;
- counters are represented as mutable array state inside the command.

The pilot must not assume either risk requires a change. Habit and the independent structural reviewer classify the observed code and may justify a no-op when the TDD implementation has already removed the risk.

## Local checks

Requirements: Java 25 and Docker for the SonarQube validation.

```bash
./mvnw clean verify
habit-hooks --all --no-snooze
```

The Maven gate enforces 100% line and branch coverage for included production classes. GitHub Actions also starts a real SonarQube Community Build, analyzes the pull request base and candidate as consecutive versions, waits for Compute Engine and the Quality Gate, and verifies new issues, new coverage, and new duplication.

