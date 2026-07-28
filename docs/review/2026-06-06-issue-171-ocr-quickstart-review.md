# Issue 171 OCR Quickstart 검토

## 범위

- 이슈: #171 `feat: add basic images-ocr usage example`
- 브랜치: `feat/issue-171-images-ocr-example`
- 변경 영역: `images-ocr` test gate, native OCR quickstart test, README/README.ko quickstart docs, OCR CI gate alignment

## 계층별 검토

| 계층 | 판정 | 근거 |
|---|---|---|
| Tier 1 보안 | PASS | 외부 입력 surface, credential handling, network call을 추가하지 않았다. 생성된 임시 이미지는 로컬 테스트 데이터다. |
| Tier 2 아키텍처ure | PASS | production architecture나 public API contract는 바꾸지 않았다. 실행 가능한 예제는 issue scope에 따라 `images-ocr` 안에 머문다. |
| Tier 3 API Design | PASS | 기존 `OcrOptions`, `extractText`, `immutableImageOf(File)` API를 재사용하며 새 public API는 추가하지 않는다. |
| Tier 4 Implementation | PASS | `ocr.enabled` and `ocr.container.enabled` are passed through to the test JVM; quickstart uses explicit `tessdataPath`, language, PSM, and whitelist options. CI/Nightly run the container OCR gate while host-native CI compatibility is tracked separately in #175. |
| Tier 5 테스트 | PASS | `OcrQuickstartExampleTest` exercises a real generated PNG file and host Tesseract; existing native test now uses the same tessdata path helper. |
| Tier 6 Performance | PASS | 예제는 작은 이미지 하나와 OCR 호출 하나만 만든다. production hot path나 benchmark-sensitive code는 바꾸지 않았다. |
| Tier 7 Docs/Evidence | PASS | `README.md` and `README.ko.md` both document macOS Homebrew, Ubuntu packages, `TESSDATA_PREFIX`, command, and expected output shape. |

## 발견 사항

- P0: 0
- P1: 0
- P2/P3: 0

## 검증 근거

| 명령 | 결과 | 참고 |
|---|---|---|
| `./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.OcrQuickstartExampleTest' -Docr.enabled=true --no-daemon` | PASS | 처음에는 pending-test gate를 드러냈고, system property pass-through와 tessdata path 수리 뒤 통과했다. |
| `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true --no-daemon` | PASS | `13 passing`, `1 pending` for container-gated test. |
| `./gradlew :bluetape4k-images-ocr:test --no-daemon` | PASS | `10 passing`, `4 pending` for gated native/container tests. |
| `export TESSDATA_PREFIX="$(brew --prefix)/share/tessdata"; ./gradlew :bluetape4k-images-ocr:test --tests "io.bluetape4k.images.ocr.OcrQuickstartExampleTest" -Docr.enabled=true --no-daemon` | PASS | README macOS command shape 검증; `1 passing`. |
| `./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true --rerun-tasks --no-daemon` | PASS | #175 split 이후 CI-equivalent gate; `11 passing`, `3 pending` for host-native/local quickstart tests. |
| `git diff --check` | PASS | whitespace error 없음. |
| PR #174 CI `Test / images-ocr` | FAIL 후 SCOPED | Ubuntu 24.04 `liblept5` is incompatible with the current Lept4J symbol expectations; host-native CI restoration was split to #175, while this PR keeps container OCR CI enabled. |

## 검토 판정

PASS. P0=0 and P1=0. The implementation is scoped to #171 and has direct local OCR evidence on host Tesseract with Homebrew language packs. Host-native Ubuntu CI compatibility is tracked in #175.
