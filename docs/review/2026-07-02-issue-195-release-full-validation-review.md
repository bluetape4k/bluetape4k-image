# Issue #195 Release Full Validation 검토

## 범위

- 이슈: #195, `release: gate Maven Central publish on full image module validation`
- 검토 파일: `.github/workflows/release.yml`
- 검토일: 2026-07-02

## 발견 사항

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## 근거

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

## 남은 위험

- 이 PR은 live release workflow를 dispatch하거나 artifact를 publish하지 않는다.
- If a tag commit has no full Nightly run yet, release publication is intentionally blocked until validation evidence exists or an explicit manual override is used.
