# Issue #492 다중 페이지 TIFF OCR lesson

## 배경

기존 `StructuredOcrEngine`은 `ImmutableImage` 한 장을 처리하지만 OCR 모델은
이미 `pages`와 `pageIndex`를 제공했습니다. 이번 작업은 provider 구현을 바꾸지
않고 하나의 ImageIO TIFF session에서 metadata를 먼저 검증한 뒤 page를 순서대로
decode·OCR하는 orchestration을 추가했습니다.

## 결정

- public 입력은 `ByteArray` 하나로 고정하고, `Path`/`InputStream` caller는
  동일한 encoded-byte 예산으로 먼저 bounded read를 수행하게 했습니다.
- `getNumImages(false)`와 모든 page dimension을 첫 decode/engine 호출 전에
  확인하고, 같은 reader/stream을 payload phase로 전환했습니다.
- page·pixel·metadata·결과 text/entry 예산을 안정적인 reason enum으로 분류하고,
  partial aggregate와 native/provider cause를 public surface에 노출하지 않았습니다.
- suspend session open은 `NonCancellable`로 resource 소유권을 확정한 뒤 metadata와
  page 작업만 `runInterruptible`로 실행했습니다. native provider가 interrupt를
  무시할 수 있는 경계는 README와 KDoc에 명시했습니다.

## 결과와 검증

- fake engine 기반 TIFF contract test: 10 passing.
- `images-ocr` module: 29 passing, 6 pending.
- host-native Tesseract gate: 32 passing, 3 pending.
- Tesseract Testcontainers 3-page smoke: 1 passing.
- 기존 `images` module: 675 passing, 18 pending.
- Java fixture compile, `javap` public API inspection, unchanged `OcrEngine`/`OcrOptions`
  diff, root `detekt` (`NO-SOURCE`), `git diff --check`: PASS.

## 놓친 점과 수정

초기 구현은 `maxMetadataBytes=1`처럼 TIFF header보다 작은 예산만 실제 reader
경로에서 검증했습니다. 예산을 정확히 8바이트로 제한하자 TwelveMonkeys provider가
내부 `IOException`을 자체적으로 삼켜 `ImageIO.getImageReaders`가 빈 reader 목록을
반환했고, public reason이 `READER_UNAVAILABLE`로 잘못 매핑되었습니다. 이 경계는
provider의 예외 표면만 확인해서는 발견할 수 없었습니다.

`MetadataBudgetInputStream`의 budget 소진 상태를 session에 보존하고 reader 탐색의
`READER_UNAVAILABLE`를 metadata limit으로 재분류했으며, 실제 TwelveMonkeys
`maxMetadataBytes=8` fixture가 `METADATA_LIMIT_EXCEEDED`와 engine 0회를 보장하도록
회귀 테스트를 강화했습니다(`c5b8037`).

## 다음 guard

ImageIO/TwelveMonkeys 같은 provider가 보안 예외를 축약하거나 재분류할 수 있으므로,
새 bounded input API는 fake reader 테스트만으로 완료하지 않습니다. 최소 header
예산, 정상 metadata 경계, provider가 예외를 삼키는 경계를 각각 실제 provider
fixture로 검증하고, public reason은 session의 관찰 상태와 함께 결정합니다.

## Writer DoD

- `SPW-01`: PASS — 독자, 결정, source/test/명령 근거와 현재 구현 경계를 고정했습니다.
- `SPW-02`: PASS — context, decision, outcome, verification, miss, future guard를
  모두 포함했습니다.
- `SPW-03`: PASS — 한국어 기술 문체와 `ByteArray`, `TwelveMonkeys`, reason,
  command, SHA 같은 식별자를 보존했습니다.
- `SPW-04`: PASS — 결과 수치와 `c5b8037` 수정 근거를 현재 source/test와 대조했습니다.
- `SPW-05`: PASS — 최종 Markdown을 다시 읽고 heading·인라인 코드·수치·불확실한
  live CI 경계를 확인했습니다.
