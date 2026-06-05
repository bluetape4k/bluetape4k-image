# Issue 171 OCR Quickstart

## Context

`images-ocr`에 Tesseract OCR 기능은 있었지만, 사용자가 바로 실행해 볼 수 있는
파일 기반 quickstart가 부족했다. macOS Homebrew 환경에는 `tesseract`와
`tesseract-lang`만 보이고, 언어별 패키지는 Ubuntu와 다르게 분리되어 있지 않았다.

## Decision

새 example module을 만들지 않고 `images-ocr` 내부에 native-gated quickstart test를
추가했다. 이슈 범위를 좁게 유지하면서 `immutableImageOf(File)`,
`OcrOptions.languages`, `tessdataPath`, `TesseractPageSegmentationMode` 사용법을
실제 OCR 실행으로 검증했다.

## Outcome

`-Docr.enabled=true`가 test JVM으로 전달되지 않아 처음에는 quickstart가 pending으로
처리됐다. `images-ocr` test task에서 `ocr.enabled`와 `ocr.container.enabled`를
명시적으로 pass-through 하도록 고쳤다. 이후 Tess4J가 Homebrew tessdata를 자동 탐색하지
못해 실패했으므로, test helper에서 `TESSDATA_PREFIX`와 일반적인 macOS/Ubuntu tessdata
경로를 찾아 `OcrOptions.tessdataPath`로 넘기게 했다.

## Validation

- `./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.OcrQuickstartExampleTest' -Docr.enabled=true --no-daemon`: PASS
- `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true --no-daemon`: PASS
- `./gradlew :bluetape4k-images-ocr:test --no-daemon`: PASS
- README macOS command with `TESSDATA_PREFIX="$(brew --prefix)/share/tessdata"`: PASS
- `git diff --check`: PASS

## Future Guard

Native-gated OCR tests must prove that the gate actually reaches the test JVM.
When documenting macOS Homebrew Tesseract, mention `tesseract` + `tesseract-lang`
and either set `TESSDATA_PREFIX` or pass `OcrOptions.tessdataPath`.
