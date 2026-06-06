# Snapshot Cache Actions

## Context

Nightly already uses a one-day changing-module cache TTL, but the workflow still
disabled Gradle dependency caching for jobs.

## Decision

Remove `cache-disabled: true` from Nightly Gradle setup steps.

## Outcome

Nightly keeps its existing task structure, but Gradle cache write/read behavior
is no longer explicitly disabled in the workflow.

## Verification

- `actionlint .github/workflows/*.yml`
- `rg -n -- '--refresh-dependencies|cache-disabled: true' .github/workflows` -> no matches
- `./gradlew help --no-daemon`
- `git diff --check`

## Future Guidance

Use explicit dependency refresh only in dedicated post-publish freshness checks.
Ordinary CI, Nightly, and Examples workflows should rely on cached changing-module
metadata plus targeted warm-up when a test-only SNAPSHOT dependency needs it.
