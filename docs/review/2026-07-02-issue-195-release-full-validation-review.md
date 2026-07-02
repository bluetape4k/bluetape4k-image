# Issue #195 Release Full Validation Review

## Scope

- Issue: #195, `release: gate Maven Central publish on full image module validation`
- Files reviewed: `.github/workflows/release.yml`
- Review date: 2026-07-02

## Findings

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## Evidence

- `release-validation` now runs before Maven Central publication.
- Tag-triggered releases resolve the tag commit and look for a successful Nightly validation run for that commit.
- Manual dispatch can provide `validation_run_id`, or use explicit `override_full_validation`.
- Release eligibility requires these jobs to conclude `success`:
  - `Test / images`
  - `Test / images-captcha`
  - `Test / images-ocr`
  - `Test / images-ktor`
  - `Test / images-spring-boot`
  - `Test / images-vips-api`
  - `Test / images-vips-java21`
  - `Test / images-vips-java25`
- `publish` now depends on both `resolve-version` and `release-validation`.
- `actionlint .github/workflows/release.yml`: PASS.
- `git diff --check`: PASS.
- `rg -n -F "\\'" .github/workflows/release.yml`: no escaped expression quotes.
- Shell simulation:
  - skipped OCR job: release validation rejects publication.
  - all required image jobs successful: release validation accepts publication.

## Residual Risk

- This PR does not dispatch a live release workflow or publish artifacts.
- If a tag commit has no full Nightly run yet, release publication is intentionally blocked until validation evidence exists or an explicit manual override is used.
