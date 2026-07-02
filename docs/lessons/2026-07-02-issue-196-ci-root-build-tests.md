# Issue #196 CI Root Build Test Coverage

## Context

The CI workflow detected module path changes but did not treat root Gradle,
version catalog, Gradle wrapper, or `buildSrc` changes as requiring module
tests. The final status accepted skipped module jobs as success.

## Decision

Introduce a `build-logic` path filter and fan it out to all module test jobs.
Keep skipped jobs acceptable only when the module is genuinely unaffected.

## Outcome

Root build logic changes now require all image module test jobs. The final
status step fails when a required module test is skipped for workflow dispatch,
build-logic changes, or the matching module path.

## Verification

- `actionlint .github/workflows/ci.yml`
- `git diff --check`
- `rg -n -F "\\'" .github/workflows/ci.yml`
- Local shell simulation for required skip and unaffected skip cases

## Future Guard

When CI uses path-filtered module jobs, add a broad build-logic filter for root
Gradle/catalog/buildSrc changes and make the aggregate status distinguish
required skips from unaffected skips.
