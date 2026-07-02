# Issue #194 Snapshot Full Validation Review

## Scope

- Issue: #194, `ci: require full OCR and VIPS validation before snapshot publishing`
- Files reviewed: `.github/workflows/publish-snapshot.yml`
- Review date: 2026-07-02

## Findings

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## Evidence

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

## Residual Risk

- This PR does not change the Nightly workflow itself; it blocks the snapshot publish workflow from publishing when the triggering run is not full-validation eligible.
- Release publish remains a separate gate in issue #195.
