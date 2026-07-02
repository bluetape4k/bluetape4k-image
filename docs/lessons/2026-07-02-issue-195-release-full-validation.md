# Issue #195 Release Full Validation Gate

## Context

The release workflow verified release metadata and then published Maven Central
artifacts without proving that the tag commit had passed the full image module
validation set.

## Decision

Add a release preflight job that verifies the release commit has a successful
Nightly run with all required image, OCR, and VIPS jobs completed successfully.

## Outcome

Maven Central release publication now depends on `release-validation`. Tag
pushes look up a successful Nightly run for the tag commit. Manual dispatch can
provide a validation run ID or use an explicit override.

## Verification

- `actionlint .github/workflows/release.yml`
- `git diff --check`
- `rg -n -F "\\'" .github/workflows/release.yml`
- Shell simulation for skipped OCR and all-success image job results

## Future Guard

Stable publication workflows must not treat metadata checks as a substitute for
runtime validation. Native/OCR/VIPS-heavy releases need job-level validation
evidence for the exact release commit before Maven Central publication.
