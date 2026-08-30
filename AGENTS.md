# Repository Guidelines

## Purpose

This public fixture validates the `$implement-approved-plan` skill with a real Java 25, Maven, Habit, SonarQube, and GitHub Actions workflow. Preserve `main` and the immutable `fixture-v1` tag as the laboratory baseline.

## Build and validation

Run from the repository root:

```bash
./mvnw clean verify
habit-hooks --all --no-snooze
```

Use the Maven Wrapper; do not assume a global Maven installation. Production code uses Java 25, Picocli, and two-space indentation. Tests use JUnit 5 and must cover observable command behavior. The JaCoCo gate requires 100% line and branch coverage for included production classes.

## Workflow boundaries

Each pilot starts from `fixture-v1`, creates a dedicated base branch and feature branch, and opens a pull request against that base branch. Do not rewrite history, merge pull requests, delete branches, create labels implicitly, or commit credentials. Keep corrective and refactoring commits separate as directed by the approved plan.

