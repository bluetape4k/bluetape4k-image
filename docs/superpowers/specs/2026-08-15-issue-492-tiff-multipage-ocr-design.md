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
- 입력·페이지·픽셀 예산을 모든 page metadata preflight에서 확인한 뒤 첫 decode를 시작하고, 한계를 알 수 없으면 fail-closed 한다.
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
| images/build.gradle.kts | TwelveMonkeys `imageio-tiff`와 metadata가 `bluetape4k-images`에 API로 정렬되어 있다. | OCR 모듈은 기존 transitive 계약을 재사용하고 새 native/runtime dependency를 추가하지 않는다. |

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
    val maxMetadataBytes: Long = 2L * 1024L * 1024L,
    val maxResultTextChars: Int = 1_000_000,
    val maxResultEntries: Int = 100_000,
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

enum class TiffMultiPageOcrFailureReason {
    INPUT_TOO_LARGE,
    READER_UNAVAILABLE,
    UNSUPPORTED_FORMAT,
    PAGE_COUNT_UNKNOWN,
    PAGE_LIMIT_EXCEEDED,
    DIMENSIONS_UNAVAILABLE,
    SIDE_LIMIT_EXCEEDED,
    PIXELS_PER_PAGE_LIMIT_EXCEEDED,
    TOTAL_PIXELS_LIMIT_EXCEEDED,
    METADATA_LIMIT_EXCEEDED,
    DECODE_FAILED,
    ENGINE_FAILED,
    RESULT_LIMIT_EXCEEDED,
}

class TiffMultiPageOcrValidationException(
    val reason: TiffMultiPageOcrFailureReason,
    val pageIndex: Int?,
    message: String,
) : IllegalArgumentException(message) {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

class TiffMultiPageOcrException(
    val reason: TiffMultiPageOcrFailureReason,
    val pageIndex: Int?,
    message: String,
) : OcrException(message) {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

~~~

TiffMultiPageOcr는 StructuredOcrEngine을 구현하지 않는다. 기존 단일 이미지
인터페이스에 byte-array 문서 입력을 억지로 추가하면 provider와 호출자 모두의 계약이
혼합되기 때문이다. 기존 extractOcr, suspendExtractOcr, recognize,
recognizeStructured 시그니처는 그대로 둔다.

테스트 가능성과 reader lifecycle 검증을 위해 구현체 내부에는 `TiffImageReaderFactory`
seam을 둔다. 이는 `internal` 범위이며 published ABI에 노출하지 않는다. 기본 factory는
`IIORegistryUtils.registerApplicationClasspathSpis()` 이후 `ImageIO.getImageReaders`에서
   format name이 `tiff` 또는 `tif`인 reader만 선택한다. reader가 없으면
   `READER_UNAVAILABLE`, reader는 있지만 format name이 TIFF/TIF가 아니면
   `UNSUPPORTED_FORMAT`으로 거부한다. production 경로는 TwelveMonkeys ImageIO TIFF 3.14.0
catalog entry를 사용하고, test fake는 unknown count·invalid dimension·cleanup failure를
결정적으로 재현한다. `MetadataBudgetInputStream`은 metadata phase에서
maxMetadataBytes를 넘는 read를 거부한 뒤, 동일 stream의 payload phase에서만 전체
encoded bytes를 허용한다.

### 제한값

TiffMultiPageOcrLimits 생성자는 모든 제한값이 양수인지 검증한다.

- maxEncodedBytes: TIFF 입력 ByteArray.size의 최대값. decode나 reader 생성보다 먼저 확인한다.
- maxPages: TIFF ImageReader.getNumImages(false) 결과의 최대값. reader가 page 수를
  아직 알 수 없어 음수를 반환하면 전체 IFD를 무제한 탐색하지 않고 거부한다. 결과가
  0이면 page 수를 신뢰할 수 없으므로 거부한다.
- maxPixelsPerPage: 각 page의 header width/height 곱에 적용한다.
- maxTotalPixels: 이미 확인한 page pixel 합계에 적용한다. total + next를 직접 계산하지
  않고 next > maxTotalPixels - total 비교로 overflow를 방지한다.
- maxDecodedSide: 각 page의 width와 height에 적용한다. 이는 #483 외부 입력의 side
  제한과 같은 의미를 유지한다.
- maxMetadataBytes: TIFF IFD/page metadata preflight가 읽을 수 있는 최대 byte 수이다.
  TwelveMonkeys reader가 `getNumImages(false)`에서도 전체 IFD를 탐색할 수 있으므로,
  `maxPages`만으로 metadata 비용을 제한한다고 가정하지 않는다. 한계를 넘는 reader는
  `METADATA_LIMIT_EXCEEDED`로 fail-closed 한다.
- maxResultTextChars와 maxResultEntries: provider가 반환한 aggregate text와
  structured page/block/line/word entry를 저장할 수 있는 상한이다. provider 내부 native
  작업의 byte 상한을 의미하지 않으며, orchestration 결과 집계 memory bomb를 방지한다.

기본값은 보수적인 외부 입력 한계로 문서화한다. `maxPages` 기본값은 `16`,
`maxTotalPixels` 기본값은 `64_000_000L`로 고정하고 `maxPages * maxPixelsPerPage`를
계산해 만들지 않는다.

## 처리 흐름

1. bytes.size가 maxEncodedBytes를 넘으면 즉시
   `TiffMultiPageOcrValidationException(INPUT_TOO_LARGE)`를 던진다.
2. `maxMetadataBytes`를 넘지 않도록 감싼 preflight ImageInputStream을 만들고 reader를
   찾는다. reader가 없거나 format name이 TIFF/TIF가 아니면
   입력을 거부한다. GIF reader를 TIFF 문서로 처리하지 않는다.
3. preflight reader의 `getNumImages(false)`를 호출한다. reader가 아직 page 수를 알 수
   없어 음수를 반환하거나 metadata byte 상한을 넘으면 전체 IFD를 신뢰하지 않고
   fail-closed 한다. page 수가 양수이고 maxPages 이하인지 확인한다. `getNumImages(false)`
   구현이 allowSearch를 무시하는 provider도 있으므로 bounded stream이 비용 경계를
   담당한다.
4. 첫 `read` 또는 engine 호출 전에 모든 page index에 대해 metadata preflight를
   순서대로 수행한다.
   - getWidth(index)와 getHeight(index)로 pixel을 디코드하지 않고 dimensions를 읽는다.
   - width와 height가 양수인지 확인하고, `Math.multiplyExact(width.toLong(), height.toLong())`
     로 page pixel을 계산한다. 곱셈 overflow는 제한 위반으로 fail-closed 한다.
   - width/height가 maxDecodedSide, page pixel, total pixel 예산을 넘지 않는지 확인한다.
     total은 `next > maxTotalPixels - total` 비교를 사용한다.
   - 모든 page metadata와 누적 pixel이 확인된 뒤에만 decode phase로 진입한다.
5. metadata preflight가 성공하면 같은 reader와 stream의 metadata cache를 재사용하고,
   bounded input의 `allowPayloadReads()` phase를 연다. decode phase는 preflight 목록의
   순서대로 한 page씩 수행한다. 새 reader를 만들지 않으므로 `read()`가 metadata를 다시
   탐색해 maxMetadataBytes를 우회하지 않는다.
   - reader.read(index)로 한 page만 decode하고 ImmutableImage.fromAwt로 변환한다.
   - decoded image의 실제 width/height를 다시 확인한다.
   - 현재 page에서 engine.recognizeStructured(image, options)를 호출한다.
   - 반환 text 길이와 pages/blocks/lines/words entry 수를 누적 합계에 더하기 전에
     `maxResultTextChars - accumulatedTextChars` 및
     `maxResultEntries - accumulatedEntryCount`와 overflow-safe 비교를 수행한다.
     page별 값이 한도 이내여도 누적 합계가 넘으면 `RESULT_LIMIT_EXCEEDED`로 실패하고
     aggregate에 추가하지 않는다.
   - 반환된 pages, blocks, lines, words의 page index를 현재 TIFF index로 매핑해
     aggregate 목록에 추가한다.
   - page의 decoded image와 임시 AWT 자원은 다음 page로 이동하기 전에 참조를 해제한다.
   - 다음 page로 이동하기 전에 suspend 경로는 ensureActive()를 호출한다.
6. 모든 page가 성공한 뒤 page text를 \n\n으로 결합하고 aggregate
   OcrStructuredResult를 만든다. 중간 실패에서는 aggregate를 반환하지 않는다.
7. preflight와 decode의 각 ImageReader.dispose()와 ImageInputStream.close()는 성공·실패·취소 모든 경로에서
   실행한다. 주 예외가 있으면 cleanup 예외는 `Throwable.addSuppressed`로 보존하고,
   주 예외가 없을 때만 cleanup 예외를 전파한다. createImageInputStream의 `null`, reader
   부재, reader의 `getNumImages/getWidth/getHeight/read` 실패는 각각 안정적인 입력/metadata/
   decode 단계 메시지로 분류한다. CancellationException은 catch해서 일반 OCR 오류로
   바꾸지 않고 그대로 재전파한다.

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

입력 형식·제한 위반은 `TiffMultiPageOcrValidationException`으로 분류하고, 이 예외도
nullable page index를 가진다. ImageIO
decode·engine·aggregate result 실패는 page index와 안정적인
`TiffMultiPageOcrFailureReason`을 가진 `TiffMultiPageOcrException`으로 감싼다. 전자는
`IllegalArgumentException` 하위 타입이고 후자는 `OcrException` 하위 타입이다. 모든
reason·retryability·외부 응답 매핑은 다음과 같이 고정한다: 입력/metadata/format/page/pixel
한계는 non-retryable reject, decode/engine은 page index를 포함한 sanitized failure,
cancel은 `CancellationException` 그대로 재전파한다. raw cause는 public exception에
연결하지 않고 신뢰 경계 내부 로그에서만 redacted context로 기록한다. `INPUT_TOO_LARGE`,
`READER_UNAVAILABLE`, `UNSUPPORTED_FORMAT`, `PAGE_COUNT_UNKNOWN`, `PAGE_LIMIT_EXCEEDED`,
`DIMENSIONS_UNAVAILABLE`, `SIDE_LIMIT_EXCEEDED`, `PIXELS_PER_PAGE_LIMIT_EXCEEDED`,
`TOTAL_PIXELS_LIMIT_EXCEEDED`, `METADATA_LIMIT_EXCEEDED`, `DECODE_FAILED`,
`ENGINE_FAILED`, `RESULT_LIMIT_EXCEEDED` reason code와 nullable page index는 caller가
재시도·관찰 정책을 결정할 수 있는 최소 계약으로 고정한다.

- reader 없음, non-TIFF, unknown page count/dimension, malformed/truncated page는 decode
  이전 또는 해당 page 경계에서 거부한다.
- CancellationException은 모든 broad catch보다 먼저 재전파한다.
- blocking API는 호출 스레드에서 순차 처리한다. suspend API는 dispatcher에서 blocking
  ImageIO/OCR 작업을 수행하고, preflight와 각 page 작업을 `runInterruptible` 경계로
  감싸며 페이지 사이에서 취소를 확인한다. ImageIO 또는 provider가 interrupt를 무시하면
  취소 지연은 최악의 단일 page decode/OCR 시간까지일 수 있으므로 caller는 coroutine
  timeout을 함께 설정해야 한다. 이 API는 강제 native abort를 보장하지 않는다.
- OCR engine이 실패하면 재시도하지 않는다. fail-fast가 기본이며 partial result batch
  계약은 후속 이슈로 남긴다.

## 호환성과 비목표

- 기존 OcrEngine, StructuredOcrEngine, OcrOptions, OcrStructuredResult 및 기존
  extension 함수의 source/binary compatibility를 유지한다.
- Tesseract native 초기화, traineddata 탐색, provider별 structured metadata 의미는
  변경하지 않는다.
- TIFF 지원은 현재 `bluetape4k-images`가 제공하는 TwelveMonkeys ImageIO TIFF reader의
  지원 범위에 따른다. BigTIFF, 특수 압축/tiling, orientation 보정은 별도 capability로
  약속하지 않으며 reader가 거부하면 안정적인 unsupported/decode 오류로 종료한다.
- GIF animation frame OCR은 TIFF document page와 의미가 다르므로 이 이슈에서 구현하지
  않는다. GIF 호출은 명시적 unsupported input으로 종료한다.
- PaddleOCR, GPU/model download, page 병렬 처리, partial-result streaming, Path/InputStream
  overload, 새로운 published module은 이번 범위에 포함하지 않는다.
- ByteArray-only 선택은 #483 외부 업로드 경계에서 encoded byte를 decode 전에 검사하고
  caller-owned stream lifecycle을 만들지 않기 위한 것이다. 대용량 파일/stream caller는
  application boundary에서 동일한 `maxEncodedBytes`로 bounded read 후 이 entry point를
  호출하며, zero-copy streaming은 후속 이슈로 분리한다.

### 운영·릴리스 증적

- PR merge gate는 `test-images-ocr` CI job의 exact commit check와
  `-Docr.container.enabled=true` 실행 결과를 필수로 기록한다. container smoke는
  3-page TIFF를 실제 container Tesseract CLI에 전달하고 page별 OCR text와 aggregate
  separator를 검증하는 테스트 경로를 갖는다.
- release candidate gate는 exact commit SHA, workflow run URL, test-result artifact URL,
  `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true --no-daemon` native 결과를
  release checklist에 함께 기록한다. native gate 실패 시 publish를 중단하고 이전
  catalog/artifact pin으로 복귀하며, 호출자는 기존 single-image API로 되돌린다.
- 운영 integration이 metric을 붙일 때 event 이름은
  `images.ocr.tiff.accepted|rejected|failed|cancelled`로 고정하고 reason code는
  label cardinality를 제한한다. payload·파일 경로·tessdata 경로는 기록하지 않는다.

## 검증 계획

### 항상 실행되는 단위 테스트

- 기존 SuspendTiffMultiPageWriter로 3-page TIFF ByteArray fixture를 만들고 fake
  StructuredOcrEngine이 호출 순서 0, 1, 2를 받는지 확인한다.
- aggregate text separator와 page/block/line/word의 page index를 확인한다.
- confidence와 bounding box가 null인 entry가 그대로 유지되는지 확인한다.
- maxEncodedBytes, maxPages, maxPixelsPerPage, maxTotalPixels,
  maxDecodedSide, maxMetadataBytes 초과가 engine 호출 전에 거부되는지 확인한다.
- maxResultTextChars와 maxResultEntries의 page별·누적 초과가 aggregate에 추가되기
  전에 `RESULT_LIMIT_EXCEEDED`로 종료되는지 확인한다.
- page count/dimension을 알 수 없는 입력, malformed/truncated TIFF, GIF 입력이 fail-closed
  하는지 확인한다.
- no-reader, non-TIFF reader, unknown count, page 0/page N dimension·pixel·side limit의
  reason과 nullable pageIndex가 각각 `READER_UNAVAILABLE`, `UNSUPPORTED_FORMAT`,
  `PAGE_COUNT_UNKNOWN`, 해당 page index로 매핑되는지 확인한다.
- TwelveMonkeys reader가 allowSearch를 무시하는 경우에도 bounded preflight stream이
  metadata byte 상한을 지키고, decode phase에서 새 reader 없이 같은 stream budget을
  재사용하는지 확인한다.
- page 1 engine 실패 시 page 0 결과가 반환되지 않고, page 2가 호출되지 않는지 확인한다.
- suspend 경로에서 page 사이 취소가 발생하면 CancellationException이 전파되고 이후
  page가 호출되지 않는지 확인한다.
- reader/stream cleanup failure가 주 예외를 덮지 않고 suppressed로 보존되는지, public
  exception cause/message에 path·payload·tessdata 경로가 노출되지 않는지 확인한다.
- 기존 단일 이미지 engine/extension 테스트를 변경 없이 통과시킨다.

### 모듈 및 문서 검증

- ./gradlew :bluetape4k-images-ocr:test --no-build-cache
- ./gradlew :bluetape4k-images:test --no-build-cache
- ./gradlew detekt
- git diff --check
- images-ocr/README.md와 README.ko.md에 ByteArray TIFF 예제, 제한값, GIF 제외를
  동일한 구조로 반영한다.
- pure-JVM orchestration 단위 테스트는 항상 실행한다. 저장소 CI의 `test-images-ocr`
  job은 `-Docr.container.enabled=true`로 Testcontainers Tesseract smoke를 필수 실행하며,
  이 issue는 그 기존 gate를 유지하고 3-page TIFF fixture를 container smoke 입력으로
  추가한다. Ubuntu host의 native Tesseract는 Leptonica ABI 차이로 CI 필수 gate로 만들지
  않고, release 후보에서 `-Docr.enabled=true`를 별도 순차 실행해 결과를 기록한다.
- 운영 integration은 payload·경로를 남기지 않고 reason code, page index, 입력 byte 수,
  preflight pixel 합계, elapsed time, reject/failure/cancel 카운터만 기록해야 한다.

## 대안과 기각 근거

| 대안 | 기각 근거 |
|---|---|
| InputStream 전용 entry point | caller stream close 방지, bounded read, ImageInputStream lifecycle 계약이 추가되어 0.5.0 핵심 범위를 넓힌다. |
| Path 전용 entry point | HTTP 업로드 호출자가 임시 파일을 관리해야 하며 #483 ByteArray 외부 입력 경계와 직접 연결되지 않는다. |
| StructuredOcrEngine 시그니처에 page list 추가 | 기존 provider의 source/binary compatibility를 깨뜨리고 단일 이미지와 문서 입력 책임을 섞는다. |
| page 병렬 OCR | native provider 동시성·메모리 배수·결정적 순서 보장이 이번 이슈의 bounded safety 목표와 충돌한다. |
| GIF frame을 TIFF page와 같은 API로 처리 | animation frame과 문서 page의 의미·metadata·재생 순서 계약이 다르므로 후속 capability로 분리한다. |
| `getNumImages(true)` 전체 스캔 | 전체 IFD를 탐색하는 동안 maxPages/취소 경계를 보장할 수 없으므로 `false` 조회에서 unknown을 거부한다. |

## DoD

- [ ] 공개 ByteArray TIFF entry point와 제한값 계약이 구현·KDoc·양 locale README에 반영된다.
- [ ] 3-page TIFF fixture가 순서·결정적 separator·structured page index를 증명한다.
- [ ] 입력/페이지/픽셀 budget, unknown/malformed/truncated/GIF, fail-fast, cancellation 테스트가 통과한다.
- [ ] 모든 page metadata preflight가 decode/engine보다 먼저 수행되고, late budget 초과도 engine 0회로 종료된다.
- [ ] 기존 단일 이미지 API와 Tesseract provider 테스트가 회귀 없이 통과한다.
- [ ] Testcontainers Tesseract가 실제 3-page TIFF를 받아 page별 text와 aggregate를 검증한다.
- [ ] CI Testcontainers Tesseract smoke와 release 후보 native Tesseract 결과가 별도 기록된다.
- [ ] 독립 6-lane spec/plan/code review에서 P0/P1이 0으로 수렴한다.
- [ ] validation/OCR exception의 reason·retryability·serialVersionUID·KDoc과 Java
  explicit-argument 호출 경로가 문서화된다.
- [ ] release candidate checklist에 exact SHA, workflow run, artifact, native 결과와
  rollback trigger가 기록된다.
- [ ] PR #492 링크, milestone 0.5.0, assignee debop, labels parity, ## DoD Status가 확인된다.
