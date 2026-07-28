# Issue #194 Snapshot Full Validation 검토

## 범위

- 이슈: #194, `ci: require full OCR and VIPS validation before snapshot publishing`
- 검토 파일: `.github/workflows/publish-snapshot.yml`
- 검토일: 2026-07-02

## 발견 사항

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## 근거

- Snapshot publishing now depends on `validate-full-nightly`.
- `validate-full-nightly` checks the triggering Nightly run jobs through the GitHub Actions API.
- Publish eligibility requires these jobs to conclude `success`:
  - `Test / images-ocr`
  - `Test / images-vips-api`
  - `Test / images-vips-java21`
  - `Test / images-vips-java25`
- Smoke Nightly runs that skip full-only OCR/VIPS jobs remain non-publish-eligible.
- Manual dispatch requires a full Nightly `validation_run_id` unless `override_full_validation` is explicitly true.
- `actionlint .github/workflows/publish-snapshot.yml`: PASS.
- `git diff --check`: PASS.
- `rg -n -F "\\'" .github/workflows/publish-snapshot.yml`: no escaped expression quotes.
- Shell simulation:
  - skipped OCR job: non-publish-eligible.
  - all OCR/VIPS jobs successful: publish-eligible.

## 남은 위험

- 이 PR은 Nightly workflow 자체를 바꾸지 않는다. triggering run이 full-validation eligible이 아니면 snapshot publish workflow가 publish하지 못하게 막는다.
- Release publish remains a separate gate in issue #195.
