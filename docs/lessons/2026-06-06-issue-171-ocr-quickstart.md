# Issue 171 OCR 빠른 시작

## 배경

`images-ocr`에 Tesseract OCR 기능은 있었지만, 사용자가 바로 실행해 볼 수 있는
파일 기반 빠른 시작 예제가 부족했다. macOS Homebrew 환경에는 `tesseract`와
`tesseract-lang`만 보이고, 언어별 패키지는 Ubuntu와 다르게 분리되어 있지 않았다.

## 결정

새 example 모듈을 만들지 않고 `images-ocr` 내부에 native gate 빠른 시작 테스트를
추가했다. 이슈 범위를 좁게 유지하면서 `immutableImageOf(File)`,
`OcrOptions.languages`, `tessdataPath`, `TesseractPageSegmentationMode` 사용법을
실제 OCR 실행으로 검증했다.

## 결과

`-Docr.enabled=true`가 테스트 JVM으로 전달되지 않아 처음에는 빠른 시작 테스트가
대기 상태로 처리됐다. `images-ocr` test task에서 `ocr.enabled`와
`ocr.container.enabled`를 명시적으로 전달하도록 고쳤다. 이후 Tess4J가 Homebrew
tessdata를 자동 탐색하지 못해 실패했으므로, test helper에서 `TESSDATA_PREFIX`와 일반적인 macOS/Ubuntu tessdata
경로를 찾아 `OcrOptions.tessdataPath`로 넘기게 했다.

PR CI에서는 Ubuntu 24.04의 `liblept5` 패키지가 현재 Lept4J/Tess4J가 요구하는
native symbol을 제공하지 않아 host-native OCR 테스트가 실패했다. 이 문제는 #171의
간단한 예제 범위를 넘어 Leptonica/Tess4J 호환성 평가가 필요하므로 후속 이슈 #175로
분리했다. CI와 Nightly는 `ocr.container.enabled=true`만 사용해 container OCR
gate와 일반 단위 테스트를 유지하도록 조정했다.

## 검증

- `./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.OcrQuickstartExampleTest' -Docr.enabled=true --no-daemon`: PASS
- `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true --no-daemon`: PASS
- `./gradlew :bluetape4k-images-ocr:test --no-daemon`: PASS
- `./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true --rerun-tasks --no-daemon`: PASS
- `TESSDATA_PREFIX="$(brew --prefix)/share/tessdata"`를 지정한 README macOS 명령: 통과
- `git diff --check`: PASS
- PR #174 CI `Test / images-ocr`: Ubuntu Leptonica/Lept4J symbol 불일치로 실패.
  host-native CI 복구는 #175 범위로 분리

## 이후 주의 사항

native gate OCR 테스트는 gate가 실제로 테스트 JVM에 전달되는지 입증해야 한다.
macOS Homebrew Tesseract를 문서화할 때는 `tesseract`와 `tesseract-lang`을 명시하고
`TESSDATA_PREFIX`를 설정하거나 `OcrOptions.tessdataPath`를 전달한다. Ubuntu CI에서는
패키지 이름뿐 아니라 native library 버전과 symbol도 확인한다. host-native OCR CI
복구에 의존성 또는 runtime 평가가 필요하다면 빠른 시작 PR의 범위를 넓히지 말고 후속
이슈를 만든다.
