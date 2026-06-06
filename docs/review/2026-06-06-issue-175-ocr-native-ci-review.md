# Issue #175 OCR Native CI Review

- Issue: [#175](https://github.com/bluetape4k/bluetape4k-image/issues/175)
- Scope: CI/Nightly OCR runtime strategy, README locale pair, research and
  lesson artifacts.

## Pre-PR Review

### Findings

- P0: 0
- P1: 0
- P2: 0
- P3: 0

### Evidence Checked

- #174 failed CI logs for `ubuntu-24.04` host-native OCR failures.
- `dependencyInsight` for Tess4J/Lept4J runtime versions.
- `javap` proof that Lept4J registers `pixAddMultipleBlackWhiteBorders`.
- Ubuntu Noble `liblept5` package version evidence.
- CI and Nightly OCR jobs both use `-Docr.container.enabled=true`.
- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml`: PASS.
- `rg -n --fixed-strings "\\'" .github/workflows`: PASS, no escaped single
  quotes.
- `./gradlew :bluetape4k-images-ocr:test --no-daemon`: PASS.
- `./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true --rerun-tasks --no-daemon`:
  PASS, 11 passing and 3 pending.
- `./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.OcrQuickstartExampleTest' -Docr.enabled=true --rerun-tasks --no-daemon`:
  PASS, 1 passing.

### Verdict

APPROVE. The chosen strategy is narrower and more maintainable than
source-building native OCR libraries in every CI run. The acceptance criteria are
met by documenting the strategy, aligning CI/Nightly behavior, and preserving
host-native OCR as a local/manual check.
