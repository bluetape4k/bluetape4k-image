# Issue #194 Snapshot Full Validation Gate

## Context

`publish-snapshot.yml` was triggered by any successful `Nightly` workflow run.
The Nightly smoke schedule can skip OCR and VIPS jobs while still succeeding,
so snapshot publishing could proceed without proving native/OCR modules.

## Decision

Move snapshot publish eligibility into `publish-snapshot.yml` by checking the
triggering Nightly run's job conclusions before the publish job starts.

## Outcome

Snapshot publishing now requires full OCR and VIPS jobs to have succeeded in
the triggering or manually supplied Nightly run. Manual dispatch can still use
an explicit override, but that bypass is visible in workflow inputs.

## Verification

- `actionlint .github/workflows/publish-snapshot.yml`
- `git diff --check`
- `rg -n -F "\\'" .github/workflows/publish-snapshot.yml`
- Shell simulation for skipped OCR and all-success OCR/VIPS job results

## Future Guard

Do not use a whole-workflow `workflow_run.conclusion == success` as a publish
gate when the upstream workflow has smoke and full scopes. Check the required
job conclusions or pass an explicit publish-eligible signal.
