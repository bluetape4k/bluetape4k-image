# Issue 175 OCR Native CI

## Context

PR #174 showed that `images-ocr` host-native tests are not portable on GitHub
Actions `ubuntu-24.04`. The runner package set provides an older Leptonica line
than the native API surface expected by current Lept4J/Tess4J.

## Decision

Keep GitHub CI and Nightly on the container-backed OCR gate and document
host-native OCR as a local/manual check. Do not paper over the problem with a
library-name symlink; it only moves the failure from load-time to symbol lookup.

## Outcome

The workflow no longer installs host Tesseract for the OCR job because the job
runs `-Docr.container.enabled=true`. The module README pair now states that
`-Docr.enabled=true` requires compatible host Tesseract, language packs, and
Leptonica, while CI/Nightly use the portable container gate.

## Validation To Keep

- Run `actionlint` after workflow edits.
- Run `rg -n "\\'" .github/workflows` before push.
- Run `./gradlew :bluetape4k-images-ocr:test`.
- Run `./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true`.
- Run host-native `-Docr.enabled=true` only on machines where Tesseract and
  Leptonica are known compatible.

## Future Guard

If host-native OCR CI is restored later, first prove the runner's Leptonica
symbol set against the selected Lept4J/Tess4J line. Treat source-building native
runtime libraries as a separate CI maintenance issue, not a quick workflow tweak.
