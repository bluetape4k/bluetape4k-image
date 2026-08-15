# 다중 페이지 TIFF structured OCR 설계

## 문서 상태

- 이슈: #492
- Epic: #508
- 마일스톤: 0.5.0
- Stack 단계: OCR-1
- 기준 브랜치: develop
- 기준 커밋: efc2411215dec522eacd46b2554719afa1775a66
- 설계 승인: 2026-08-15 사용자 승인
- 대상 모듈: bluetape4k-images-ocr, 기존 bluetape4k-images API 재사용

## 문제와 목표

현재 structured OCR 계약은 한 번에 하나의 ImmutableImage만 받습니다. OcrPage와
pageIndex 모델은 이미 존재하지만, Tesseract 구현은 호출마다 page index 0인 단일
page를 반환합니다. core는 SuspendTiffMultiPageWriter로 다중 페이지 TIFF를 만들 수
있지만 TIFF IFD를 순서대로 읽어 기존 structured OCR engine에 연결하는 경계가 없습니다.

이번 변경은 0.5.0에서 결정적인 3-page TIFF 문서 OCR 경계를 제공합니다.

- TIFF 문서 page를 순서대로 열거하고 기존 StructuredOcrEngine에 한 page씩 전달한다.
- 입력·페이지·픽셀 예산을 decode 전에 확인하고, 한계를 알 수 없으면 fail-closed 한다.
- 기존 단일 이미지 API와 Tesseract provider의 source/binary compatibility를 유지한다.
- 전체 페이지가 성공한 경우에만 하나의 OcrStructuredResult를 반환한다.

GIF animation frame OCR, PaddleOCR runtime/model 도입, 새로운 OCR provider 추가는 이
설계의 범위가 아니다. GIF는 지원하지 않는 입력으로 명시적으로 거부한다.

## 근거와 현재 계약

다음 소스는 설계 작성 시점의 develop 기준으로 확인했다.

| 근거 | 현재 계약 | 설계 영향 |
|---|---|---|
| images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt | StructuredOcrEngine.recognizeStructured는 단일 ImmutableImage를 받는다. | 기존 engine을 주입하고 호출 계약을 변경하지 않는다. |
| images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt | OcrStructuredResult는 pages, blocks, lines, words와 pageIndex를 제공한다. | aggregate result는 기존 모델을 재사용하고 page index만 입력 순서로 재지정한다. |
| images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt | Tesseract가 호출별 새 client를 만들고 기본 page index 0을 반환한다. | Tesseract를 다중 페이지 전용 provider로 수정하지 않는다. |
| images/src/main/kotlin/io/bluetape4k/images/ImageDecodeLimits.kt | 외부 입력의 encoded bytes, decoded pixels, decoded side 한계를 표현한다. | 다중 페이지 옵션은 기존 외부 입력 encoded/side 기준을 유지하고 문서 전체 예산을 추가한다. |
| images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt | immutableExternalImageOf는 decode 전 encoded/dimension 검증을 수행한다. | 페이지 decode 전 동일한 fail-closed 원칙을 따른다. |
| images/src/main/kotlin/io/bluetape4k/images/coroutines/SuspendTiffMultiPageWriter.kt | TwelveMonkeys ImageIO TIFF writer로 page sequence를 생성한다. | 테스트 fixture와 실제 입력은 동일한 ImageIO provider 계열을 사용한다. |
| images/src/main/kotlin/io/bluetape4k/images/ImageDimensionProbe.kt | 일반 probe는 첫 frame만 읽는다. | 다중 페이지 경계에서 첫 frame probe를 반복 호출하지 않고 TIFF ImageReader sequence를 직접 사용한다. |

## 선택한 API와 경계

### 입력

공개 entry point는 ByteArray 하나만 제공한다.

- 입력 바이트는 호출자가 소유하며 orchestrator가 수정하지 않는다.
- 내부 ImageInputStream은 orchestrator가 만들고 닫는다.
- Path와 InputStream overload는 이번 버전에 추가하지 않는다. 따라서 caller stream의
  close 또는 blocking I/O 소유권 계약을 새로 만들지 않는다.
- ByteArray는 #483 외부 입력 경계와 직접 연결되어 encoded byte 한계를 decode 전에
  적용할 수 있다.

### 주요 타입

구현 시 public API는 `io.bluetape4k.images.ocr` 패키지의 다음 타입과 시그니처를
사용한다. 아래의 의미와 인자 순서를 바꾸지 않는다.

~~~kotlin
data class TiffMultiPageOcrLimits(
    val maxEncodedBytes: Long = ImageDecodeLimits.DEFAULT_MAX_ENCODED_BYTES,
    val maxPages: Int = 16,
    val maxPixelsPerPage: Long = ImageDecodeLimits.DEFAULT_MAX_DECODED_PIXELS,
    val maxTotalPixels: Long = 64_000_000L,
    val maxDecodedSide: Int = ImageDecodeLimits.DEFAULT_MAX_DECODED_SIDE,
)

class TiffMultiPageOcr(
    private val engine: StructuredOcrEngine = TesseractOcrEngine(),
) {
    fun recognize(
        bytes: ByteArray,
        options: OcrOptions = OcrOptions(),
        limits: TiffMultiPageOcrLimits = TiffMultiPageOcrLimits(),
    ): OcrStructuredResult

    suspend fun suspendRecognize(
        bytes: ByteArray,
        options: OcrOptions = OcrOptions(),
        limits: TiffMultiPageOcrLimits = TiffMultiPageOcrLimits(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): OcrStructuredResult
}

~~~

TiffMultiPageOcr는 StructuredOcrEngine을 구현하지 않는다. 기존 단일 이미지
인터페이스에 byte-array 문서 입력을 억지로 추가하면 provider와 호출자 모두의 계약이
혼합되기 때문이다. 기존 extractOcr, suspendExtractOcr, recognize,
recognizeStructured 시그니처는 그대로 둔다.

### 제한값

TiffMultiPageOcrLimits 생성자는 모든 제한값이 양수인지 검증한다.

- maxEncodedBytes: TIFF 입력 ByteArray.size의 최대값. decode나 reader 생성보다 먼저 확인한다.
- maxPages: TIFF ImageReader.getNumImages(true) 결과의 최대값. 결과가 0 또는 음수로
  알려지면 page 수를 신뢰할 수 없으므로 거부한다.
- maxPixelsPerPage: 각 page의 header width/height 곱에 적용한다.
- maxTotalPixels: 이미 확인한 page pixel 합계에 적용한다. total + next를 직접 계산하지
  않고 next > maxTotalPixels - total 비교로 overflow를 방지한다.
- maxDecodedSide: 각 page의 width와 height에 적용한다. 이는 #483 외부 입력의 side
  제한과 같은 의미를 유지한다.

기본값은 보수적인 외부 입력 한계로 문서화한다. `maxPages` 기본값은 `16`,
`maxTotalPixels` 기본값은 `64_000_000L`로 고정하고 `maxPages * maxPixelsPerPage`를
계산해 만들지 않는다.

## 처리 흐름

1. bytes.size가 maxEncodedBytes를 넘으면 즉시 IllegalArgumentException을 던진다.
2. ImageIO.createImageInputStream(ByteArrayInputStream(bytes))를 만들고 reader를 찾는다.
   reader가 없거나 format name이 TIFF/TIF가 아니면 입력을 거부한다. GIF reader를
   TIFF 문서로 처리하지 않는다.
3. reader의 getNumImages(true)를 호출한다. page 수가 양수이고 maxPages 이하인지
   확인한다. unknown page count는 fail-closed 한다.
4. 각 page index에 대해 다음을 순서대로 수행한다.
   - getWidth(index)와 getHeight(index)로 pixel을 디코드하기 전에 dimensions를 읽는다.
   - width/height가 maxDecodedSide, page pixel, total pixel 예산을 넘지 않는지 확인한다.
   - reader.read(index)로 한 page만 decode하고 ImmutableImage.fromAwt로 변환한다.
   - decoded image의 실제 width/height를 다시 확인한다.
   - 현재 page에서 engine.recognizeStructured(image, options)를 호출한다.
   - 반환된 pages, blocks, lines, words의 page index를 현재 TIFF index로 매핑해
     aggregate 목록에 추가한다.
   - 다음 page로 이동하기 전에 suspend 경로는 ensureActive()를 호출한다.
5. 모든 page가 성공한 뒤 page text를 \n\n으로 결합하고 aggregate
   OcrStructuredResult를 만든다. 중간 실패에서는 aggregate를 반환하지 않는다.
6. ImageReader.dispose()와 ImageInputStream.close()는 성공·실패·취소 모든 경로에서
   실행한다. CancellationException은 catch해서 일반 OCR 오류로 바꾸지 않고 그대로
   재전파한다.

## 결과 계약

- aggregate text는 입력 page 순서와 동일한 순서로 \n\n separator를 사용한다.
- 각 입력 page의 structured entry는 TIFF page index를 pageIndex로 가진다. Tesseract가
  반환한 기본값 0을 그대로 노출하지 않는다.
- confidence, bounding box, source region은 원래 값과 null 상태를 보존한다.
- options는 모든 page 호출에 전달한 동일한 인스턴스를 결과에 기록한다.
- page OCR engine이 반환한 page entry 개수는 provider별 metadata 차이를 보존하되, 모든
  entry의 page index를 현재 입력 page로 정규화한다.
- fail-fast 정책에서는 부분 결과나 실패한 page까지의 목록을 public API로 반환하지 않는다.

## 오류와 취소

입력 형식·제한 위반은 IllegalArgumentException으로 분류하고, ImageIO decode 실패와
engine 실패는 page index를 포함한 sanitized OcrException으로 감싼다. 원인 예외는
diagnostic cause로 연결하되 파일 경로, native path, 입력 payload를 오류 메시지에 넣지
않는다.

- reader 없음, non-TIFF, unknown page count/dimension, malformed/truncated page는 decode
  이전 또는 해당 page 경계에서 거부한다.
- CancellationException은 모든 broad catch보다 먼저 재전파한다.
- blocking API는 호출 스레드에서 순차 처리한다. suspend API는 dispatcher에서 blocking
  ImageIO/OCR 작업을 수행하고 페이지 사이에서 취소를 확인한다.
- OCR engine이 실패하면 재시도하지 않는다. fail-fast가 기본이며 partial result batch
  계약은 후속 이슈로 남긴다.

## 호환성과 비목표

- 기존 OcrEngine, StructuredOcrEngine, OcrOptions, OcrStructuredResult 및 기존
  extension 함수의 source/binary compatibility를 유지한다.
- Tesseract native 초기화, traineddata 탐색, provider별 structured metadata 의미는
  변경하지 않는다.
- GIF animation frame OCR은 TIFF document page와 의미가 다르므로 이 이슈에서 구현하지
  않는다. GIF 호출은 명시적 unsupported input으로 종료한다.
- PaddleOCR, GPU/model download, page 병렬 처리, partial-result streaming, Path/InputStream
  overload, 새로운 published module은 이번 범위에 포함하지 않는다.

## 검증 계획

### 항상 실행되는 단위 테스트

- 기존 SuspendTiffMultiPageWriter로 3-page TIFF ByteArray fixture를 만들고 fake
  StructuredOcrEngine이 호출 순서 0, 1, 2를 받는지 확인한다.
- aggregate text separator와 page/block/line/word의 page index를 확인한다.
- confidence와 bounding box가 null인 entry가 그대로 유지되는지 확인한다.
- maxEncodedBytes, maxPages, maxPixelsPerPage, maxTotalPixels,
  maxDecodedSide 초과가 engine 호출 전에 거부되는지 확인한다.
- page count/dimension을 알 수 없는 입력, malformed/truncated TIFF, GIF 입력이 fail-closed
  하는지 확인한다.
- page 1 engine 실패 시 page 0 결과가 반환되지 않고, page 2가 호출되지 않는지 확인한다.
- suspend 경로에서 page 사이 취소가 발생하면 CancellationException이 전파되고 이후
  page가 호출되지 않는지 확인한다.
- 기존 단일 이미지 engine/extension 테스트를 변경 없이 통과시킨다.

### 모듈 및 문서 검증

- ./gradlew :bluetape4k-images-ocr:test --no-build-cache
- ./gradlew :bluetape4k-images:test --no-build-cache
- ./gradlew detekt
- git diff --check
- images-ocr/README.md와 README.ko.md에 ByteArray TIFF 예제, 제한값, GIF 제외를
  동일한 구조로 반영한다.
- native Tesseract와 Testcontainers OCR 테스트는 이번 pure-JVM orchestration의 필수
  gate가 아니며, 기존 환경 gate를 유지하고 별도 순차 실행 여부를 결과에 기록한다.

## 대안과 기각 근거

| 대안 | 기각 근거 |
|---|---|
| InputStream 전용 entry point | caller stream close 방지, bounded read, ImageInputStream lifecycle 계약이 추가되어 0.5.0 핵심 범위를 넓힌다. |
| Path 전용 entry point | HTTP 업로드 호출자가 임시 파일을 관리해야 하며 #483 ByteArray 외부 입력 경계와 직접 연결되지 않는다. |
| StructuredOcrEngine 시그니처에 page list 추가 | 기존 provider의 source/binary compatibility를 깨뜨리고 단일 이미지와 문서 입력 책임을 섞는다. |
| page 병렬 OCR | native provider 동시성·메모리 배수·결정적 순서 보장이 이번 이슈의 bounded safety 목표와 충돌한다. |
| GIF frame을 TIFF page와 같은 API로 처리 | animation frame과 문서 page의 의미·metadata·재생 순서 계약이 다르므로 후속 capability로 분리한다. |

## DoD

- [ ] 공개 ByteArray TIFF entry point와 제한값 계약이 구현·KDoc·양 locale README에 반영된다.
- [ ] 3-page TIFF fixture가 순서·결정적 separator·structured page index를 증명한다.
- [ ] 입력/페이지/픽셀 budget, unknown/malformed/truncated/GIF, fail-fast, cancellation 테스트가 통과한다.
- [ ] 기존 단일 이미지 API와 Tesseract provider 테스트가 회귀 없이 통과한다.
- [ ] 독립 6-lane spec/plan/code review에서 P0/P1이 0으로 수렴한다.
- [ ] PR #492 링크, milestone 0.5.0, assignee debop, labels parity, ## DoD Status가 확인된다.
