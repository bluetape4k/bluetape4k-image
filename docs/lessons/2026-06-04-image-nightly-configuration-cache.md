# Lessons Learned — image Nightly configuration cache (2026-06-04)

**Related issue**: #148
**Affected workflow**: `.github/workflows/nightly-tests.yml`

## Context

Post-merge Nightly smoke run 26962025345 failed in `Test / images-ktor`.
The GitHub runner resolved BOM-managed dependencies with empty versions,
including `io.github.bluetape4k:bluetape4k-core:.` and
`org.jetbrains.kotlinx:kotlinx-coroutines-core:.`.

## Decision

Keep `--refresh-dependencies`, and disable Gradle configuration cache for all
Nightly Gradle commands that compile, test, or generate Kover reports. The
failure is runner/cache-state specific rather than a source test failure.

## Verification

- `actionlint .github/workflows/nightly-tests.yml`
- `git diff --check`
- Nightly Gradle audit: every `./gradlew` run block includes
  `--refresh-dependencies` and `--no-configuration-cache`.

## Future Rule

When a Nightly job resolves BOM-managed dependencies with empty versions on a
GitHub runner, audit all workflow Gradle calls in that repo. Do not limit the
fix to the single failing module unless a post-merge Nightly rerun proves it.
