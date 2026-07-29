# Issue #175 OCR Native CI 검토

- 이슈: [#175](https://github.com/bluetape4k/bluetape4k-image/issues/175)
- 범위: CI/Nightly OCR runtime strategy, README locale pair, research and
  lesson artifacts.

## PR 전 검토

### 발견 사항

- P0: 0
- P1: 0
- P2: 0
- P3: 0

### 근거 Checked

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

### 판정

APPROVE. 선택한 전략은 매 CI 실행마다 native OCR library를 source-build하는 방식보다 좁고 유지보수하기 쉽다. 전략을 문서화하고 CI/Nightly 동작을 정렬하며 host-native OCR을 local/manual check로 보존하므로 acceptance criteria를 충족한다.
