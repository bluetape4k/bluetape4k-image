# Issue #544 corpus v2 harness 교훈

## 맥락

기존 OCR benchmark manifest v1은 image bytes·dimensions·SHA-256과 expected token만
검증했다. #544의 비교 계약은 generator·font provenance, 정답 text, geometry,
malformed input을 분리해야 하므로 v1 위에 확장 경계가 필요했다.

## 결정

production OCR API와 PaddleOCR dependency를 건드리지 않고 benchmark 모듈에
`OcrBenchmarkCorpusV2` 내부 loader를 추가했다. loader는 resource를 검증한 동일
byte sequence를 decoder에 전달하고, `ocr-boxes-v1`의 schema·contiguous reading
order·pixel bounds·line text 일치를 fail-closed로 검사한다. malformed bytes는 정상
`TEXT` fixture가 아닌 `DECODE_FAILED` negative receipt로 분리하고 실제 image decode
실패도 확인한다.

## 결과

기존 `clean-text.png`를 재사용한 대표 v2 fixture와 별도 text·boxes·schema·generator
receipt를 추가했다. generator는 기존 v1의 ImageMagick 7.1.2-27 historical provenance를
정정해 기록하며 replay는 `PENDING`으로 명시한다. 현재 구현은 계약 harness의 최소 slice이며, 9개 시나리오·최소
27개 fixture와 실제 Tesseract/Paddle 비교 수치는 아직 후속 benchmark train의
PENDING gate다.

## 검증

- `./gradlew :bluetape4k-images-benchmark:test --tests 'io.bluetape4k.images.benchmark.OcrBenchmarkCorpusV2Test' --console=plain`: 7/7 PASS
- `./gradlew :bluetape4k-images-benchmark:test --console=plain`: 103/103 PASS
- `ktlint` on the two touched Kotlin files: PASS
- `./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain`: OCR benchmark task 이름 확인 PASS
- `./gradlew detekt --console=plain`: `detekt` NO-SOURCE, BUILD SUCCESSFUL
- `git diff --check`: PASS

## 다음 guard

새 fixture를 추가할 때 image·text·boxes·generator·font receipt와 license/notice를
같은 변경에 포함하고, malformed/limit 입력은 negative manifest에 둔다. 실제
quality·latency·RSS 비교를 시작하기 전에는 이 loader와 v2 manifest를 PR required
no-network contract로 실행하고, 전체 corpus는 scheduled/nightly artifact로
분리한다.
