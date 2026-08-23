# Issue #546 provider-neutral OCR API 및 PaddleHTTP adapter 경계 계획

## 계획 상태

| 항목 | 값 |
|---|---|
| 상위 Epic | [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) PaddleOCR backend 도입 검증 |
| 공통 정책 | [#543](https://github.com/bluetape4k/bluetape4k-image/issues/543) AI/ML 모델 공급망·offline cache·license·CI |
| 선행 연구 | [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544) OCR corpus·benchmark, [#545](https://github.com/bluetape4k/bluetape4k-image/issues/545) PaddleOCR service·container |
| 대상 issue | [#546](https://github.com/bluetape4k/bluetape4k-image/issues/546) provider-neutral OCR API 및 PaddleHTTP adapter 경계 |
| 후속 decision gate | [#547](https://github.com/bluetape4k/bluetape4k-image/issues/547) PaddleOCR adoption gate |
| train 유형 | Type-E 설계 문서, 후속 Type-A 구현을 위한 stacked train |
| 기준 base | `docs/issue-544-benchmark` (`32c8f09abf08d36885e07436ba5b8acb04c71616`) |
| 작업 branch | `plan/issue-546-ocr-api` |
| milestone | `1.0.0` |
| 구현 상태 | 이 issue에서는 Kotlin production source, dependency, model, container를 변경하지 않음 |

이 문서는 현재 Tess4J/Tesseract API를 바로 PaddleOCR API로 바꾸는 구현 문서가
아니다. 기존 호출자가 가진 plain text·structured OCR·coroutine 동작을 보존하면서도
provider가 `ITessAPI`, Python, PaddleX, HTTP client 타입을 공통 public 계약으로
전파하지 않도록 다음 Type-A train의 경계와 검증 증거를 고정한다.

## 1. 목표와 완료 조건

### 목표

1. provider-neutral OCR request/result/geometry/error/cancellation/limit 계약을 정한다.
2. 현재 `images-ocr`의 Tesseract 옵션과 결과를 공통 계약에 매핑하는 호환 경계를 정한다.
3. PaddleOCR는 JVM 안에 삽입하지 않고 별도 self-hosted HTTP adapter 후보로만 연결한다.
4. 기존 Kotlin/Java 호출자, Ktor/Spring 예제, serialization·binary/source compatibility를
   추적할 migration matrix와 deterministic compatibility fixture 설계를 남긴다.
5. module graph, BOM/catalog, CI tier, 보안·운영 gate를 구현 PR 단위로 분해한다.

### 이 계획의 완료 조건

- [x] 기존 Tesseract public API와 실제 호출자에 대한 migration matrix를 만들었다.
- [x] 공통 API와 provider-specific option/transport의 경계를 결정했다.
- [x] fake provider, legacy caller, structured geometry, error/cancellation을 포함한
  compatibility fixture 계약을 고정했다.
- [x] Type-A PR 분해, module graph, dependency/BOM/CI 영향, rollback 순서를 명시했다.
- [ ] #544의 실제 Tesseract/Paddle 비교 실행이 끝났다.
- [ ] #545의 model/container receipt·offline smoke·HTTP 보안 검증이 끝났다.
- [ ] #547이 PaddleOCR `ADOPT` 또는 `DEFER`를 결정했다.
- [ ] 후속 Type-A 구현 PR과 hosted CI가 완료됐다.

미완료된 세 항목은 이 문서의 설계를 자동 승인하지 않는다. 비교 실행이나
공급망 증거가 없으면 Tesseract를 0.5.0 기본 provider로 유지하고 PaddleOCR는
`DEFER`한다.

## 2. 현재 저장소의 source ledger

| 근거 | 현재 의미 | 설계에 미치는 영향 |
|---|---|---|
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt:13-23` | `ImmutableImage`와 Tess4J 중심 `OcrOptions`를 받는 blocking `fun interface` | 새 공통 provider는 이미지 객체와 provider 설정을 분리하고, 기존 interface는 compatibility facade로 유지 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt:25-44` | `StructuredOcrEngine`이 plain text surface와 선택적 page/block/line/word를 함께 제공 | 공통 result는 plain text와 structured document를 같은 response에 담고, 없는 geometry/confidence는 `null`로 유지 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt:46-68` | `OcrException`이 raw `cause`를 보유할 수 있음 | public error에는 stable reason만 남기고 raw cause는 내부 log/context로만 제한 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt:26-57` | languages, `tessdataPath`, Tess4J engine/page mode, variables/configs, trim, detail, regions | 이 필드는 common request로 그대로 승격하지 않고 Tesseract adapter option으로 격리. languages/detail/regions/trim만 공통 후보 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt:67-71` | `PLAIN_TEXT`, `LINE`, `WORD` 구조화 수준 | provider capability와 요청 detail을 비교하되, 지원하지 않는 detail은 silent downgrade하지 않음 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt:81-155` | pixel-space `OcrBoundingBox`와 caller region | 공통 geometry는 pixel 좌표·정수·양수 width/height·optional region id를 유지 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt:163-197` | `OcrResult`와 `OcrStructuredResult`가 `Serializable`이며 pages가 비어 있지 않음 | 새 wire DTO는 Java serialization을 transport 계약으로 사용하지 않고 versioned JSON/fixture 계약을 별도로 고정 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt:202-281` | page/block/line/word가 pageIndex, text, optional box/confidence/region을 보유 | 공통 `OcrTextNode` 또는 동등한 계층 result로 매핑하고 list 순서·confidence 범위를 명시 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt:26-84` | 매 recognition마다 새 client를 만들고 plain/structured를 별도 호출 | Tesseract adapter는 call-local native state와 deterministic result를 유지 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt:86-97` | Tess4J datapath, language expression, engine/page mode, configs, variables를 call마다 적용 | `tessdataPath`, enum, variables, configs는 Tesseract 전용 config로 남김 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt:99-165` | native link/class failure와 Tess4J/runtime failure를 configuration/failure message로 mapping하며 broad `RuntimeException` catch가 cancellation도 가릴 수 있음 | 현재 동작을 compatibility fixture로 기록하고, 후속 adapter에서는 `CancellationException`을 broad catch보다 먼저 재전파하며 common reason mapping과 sanitized message를 추가 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt:168-216` | 내부 `TesseractClient`가 Tess4J 호출을 감쌈 | provider-specific dependency가 `images-ocr-api`로 누출되지 않는 adapter seam으로 활용 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/ImmutableImageOcrExtensions.kt:19-81` | blocking extension와 `Dispatchers.IO` 기반 suspend extension 제공 | 기존 source-compatible facade를 유지하고 새 API에는 명시적 async bridge와 dispatcher 계약을 추가 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TiffMultiPageOcr.kt:35-218` | encoded/metadata/page/total-pixel/result limits, stable failure reason, blocking/suspend entrypoint, preflight와 cleanup을 이미 제공 | common provider migration에서 TIFF coordinator를 별도 호환 surface로 보존하고 page order·partial result 금지·cancellation cleanup·오류 mapping을 fixture로 고정 |
| `images-ocr/build.gradle.kts:12-16` | core images api, coroutines implementation, Tess4J implementation | common API에서 Tess4J/coroutines 선택을 분리하고 provider 모듈만 Tess4J를 소유 |
| `examples/ktor-ocr-api/src/main/kotlin/io/bluetape4k/images/examples/ktor/ocr/KtorOcrApiApplication.kt:58-144` | Ktor multipart/body/decode limit 후 `OcrOptions`와 `suspendExtractText` 호출 | route의 body/decode limit과 provider timeout을 분리해 adapter에 전달 |
| `examples/spring-boot-ocr-api/src/main/kotlin/io/bluetape4k/images/examples/spring/ocr/SpringBootOcrApiApplication.kt:69-177` | Spring bean으로 `TesseractOcrEngine`를 주입하고 upload byte/pixel/side limit을 적용 | bean은 provider-neutral interface를 주입하고 provider 설정은 configuration boundary에서만 소유 |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/TesseractOcrExtractionBenchmark.kt:5-53` 및 `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/OcrBenchmarkFixtures.kt:12-206` | manifest/hash-pinned fixture가 `OcrOptions`와 `ImmutableImage.extractText`를 직접 호출하고 host language/tessdata를 검증 | benchmark caller를 provider 선택·common request 변환·no-network/scheduled evidence와 함께 후속 Type-A migration 대상으로 고정 |
| `settings.gradle.kts:195-216` | published OCR module과 benchmark가 별도 project로 등록 | 후속 train에서 `images-ocr-api` project를 추가하되 example/benchmark와 publish graph를 분리 |
| `bom/build.gradle.kts:12-33` | non-published project를 제외하고 published subproject를 platform constraint로 수집 | API/provider artifact의 BOM 포함 여부와 optional Paddle provider의 명시적 채택 gate를 검증 |

## 3. 결정 요약

| 질문 | 결정 | 이유 |
|---|---|---|
| common API가 `ImmutableImage`를 직접 받을까? | 받지 않는다. immutable encoded image input을 공통 `OcrImage`로 정의하고 compatibility facade가 변환한다. | HTTP adapter는 image object를 만들지 않고 bytes를 전송하며, API module이 scrimage와 transport를 동시에 강제하지 않음 |
| 공통 engine은 `suspend`만 제공할까? | 1차 contract는 Java-friendly blocking `OcrProvider.recognize`; `suspend` bridge를 별도 제공한다. | 기존 `fun interface`/Java caller의 source 호환성을 유지하고 blocking native/HTTP의 cancellation 한계를 숨기지 않음 |
| Tesseract artifact를 즉시 rename할까? | 0.5.0에서는 기존 `bluetape4k-images-ocr` coordinate를 유지하고 새 `bluetape4k-images-ocr-api`를 추가한다. | artifact rename은 binary/dependency migration 위험이 크며, provider 분리 목적은 dependency graph로 달성 가능 |
| PaddleOCR JVM in-process/Python/JNI/CLI를 허용할까? | 거부한다. self-hosted HTTP만 조건부 후보로 둔다. | #545의 Python ABI, native allocator, cold-start, process cleanup 및 egress 위험을 JVM public API에 끌어들이지 않음 |
| adapter가 임의 provider options를 JSON으로 전달할까? | 공통 request에는 common fields만 둔다. provider-specific config는 adapter constructor/config object로 격리하고, per-request 확장은 provider module의 typed API로만 제공한다. | `Map<String, Any>`/default typing은 schema drift·보안·source compatibility를 만들고 알 수 없는 option을 조용히 무시할 수 있음 |
| structured detail 미지원 provider의 fallback은? | 요청한 detail을 silent downgrade하지 않는다. capability mismatch는 stable `UNSUPPORTED_CAPABILITY`로 실패한다. | benchmark와 caller가 품질/geometry 차이를 누락으로 오인하지 않도록 함 |
| serialization 형식은? | Java `Serializable`은 legacy in-process compatibility만 유지한다. 새 HTTP/wire의 기본 구현 mapper는 Jackson 3 implementation-only로 고정하고, versioned JSON fixture와 private codec 경계로 검증한다. Jackson/Kotlinx mapper 타입은 public API에 노출하지 않는다. | provider와 transport가 바뀌어도 wire schema와 security limits를 독립적으로 검증해야 함 |

## 4. Provider-neutral API 계약

후속 구현 PR에서 package 이름과 concrete class 이름을 확정하되, 아래 의미론과
불변식은 먼저 변경하지 않는다. 예시 이름은 설계 식별자이며 이 issue에서 source를
추가하지 않는다.

### 4.1 입력과 요청

```kotlin
interface OcrProvider {
    val providerId: String
    fun capabilities(): OcrCapabilities
    fun recognize(request: OcrRequest): OcrResult
}

data class OcrRequest(
    val image: OcrImage,
    val languages: List<String> = listOf("eng"),
    val detail: OcrDetail = OcrDetail.PLAIN_TEXT,
    val regions: List<OcrRegion> = emptyList(),
    val trimText: Boolean = true,
    val limits: OcrLimits = OcrLimits.Default,
)

data class OcrImage(
    val bytes: ByteArray,
    val mediaType: String?,
)
```

위 코드는 실제 구현을 의미하지 않는 wire-level shape 예시다. 구현 시 다음 불변식을
테스트로 고정한다.

- `bytes`는 생성자와 getter에서 방어적으로 복사하며 빈 값, 최대 encoded byte 초과,
  허용하지 않은 media type은 요청 단계에서 거부한다.
- `languages`는 순서를 보존하되 blank/중복 정책을 명시하고 provider가 이해하지 못한
  language를 성공으로 위장하지 않는다. 기본값 `eng`는 기존 `OcrOptions.DEFAULT_LANGUAGE`
  의미를 보존한다.
- `regions`는 non-negative pixel coordinate, positive size, optional nonblank id를
  사용하고 image boundary 밖의 region을 clipping하지 않는다. clipping이 필요하면
  caller가 명시적으로 재계산한다.
- `limits`는 encoded bytes, decoded pixels, page count, wall-clock timeout, concurrency
  budget을 common contract로 두되, transport body limit과 native engine 내부 limit을
  같은 값으로 가정하지 않는다. 작은 caller limit만 허용하고 common hard cap을 늘릴 수
  없게 한다.
- `OcrRequest`는 provider-specific path, URL, credential, Python object, Tess4J enum,
  `ITessAPI`, `ImmutableImage`, `Throwable`를 보유하지 않는다.

DTO 불변성과 Java/JSON 직렬화 경계도 이 계약의 일부다.

- public DTO의 `ByteArray`는 생성 시와 getter에서 방어 복사하고, `List`는 검증된
  immutable 기준 데이터로 저장한다. `copy()`가 내부 배열·collection을 다시 노출하지 않는지
  Kotlin fixture와 Java consumer fixture로 확인한다.
- `OcrRequest`/`OcrResult`의 common DTO는 JSON-only value contract로 두고 Java
  `Serializable`을 구현하지 않는다. 기존 `OcrOptions`·legacy result/exception만 기존
  `serialVersionUID`와 in-process stream round-trip을 유지한다. 새 wire가 Java object
  stream을 허용하는 것은 명시적으로 금지한다.
- Jackson 3는 adapter/codec 모듈의 implementation-only 기본 mapper다. version catalog에서
  alias와 version을 중앙 고정하고, public signature·generated POM의 API/runtime 노출을
  막는다. codec은 `schemaVersion=1`만 허용하고 unknown field, duplicate field, trailing
  token, 깊이·문자열·배열·body 크기 초과를 모두 fail closed한다. default typing과
  polymorphic class name deserialization은 사용하지 않는다.
- canonical JSON fixture는 UTF-8, 고정 field order, 명시적 `null` 정책, stable number
  format을 사용한다. Jackson 3 codec 변경은 fixture SHA-256과 schema version을 함께
  갱신해야 하며, Kotlinx serialization을 common wire의 대체 기본값으로 추가하지 않는다.

### 4.1.1 입력 이미지와 codec 경계

기존 `ImmutableImage`를 곧바로 bytes로 재인코딩하면 orientation, alpha, metadata와
좌표가 변해 OCR 결과가 달라질 수 있다. 따라서 후속 implementation은 다음 두 경계를
분리한다.

- legacy Tesseract facade는 가능한 경우 `ImmutableImage`를 provider adapter의 내부
  decoded-image 경계로 직접 전달해 불필요한 re-encode를 피한다. 이 overload는 common
  API public surface가 아니라 legacy/provider module 내부 seam이다.
- bytes가 반드시 필요한 HTTP path는 `OcrImageEncodingPolicy`를 내부 설정으로 사용한다.
  기본 정책은 deterministic lossless PNG, `image/png`, sRGB 색 공간, alpha 보존,
  orientation 정규화 완료, EXIF/외부 metadata 비전달이다. 품질·압축·원격 URL은 request가
  정하지 못한다. multipage TIFF는 페이지마다 임의 PNG로 합치지 않고 TIFF coordinator가
  preflight한 원본 page source를 순서대로 전달한다.
- encoding policy와 decode owner는 common request의 hard cap과 분리한다. edge는 encoded
  bytes를, decoder는 metadata/pixel/side를, provider는 result/concurrency/deadline을
  검증한다. 원본 `ImmutableImage`와 deterministic round-trip의 dimensions, alpha,
  orientation, text/geometry를 golden fixture로 비교하고, 변환 실패는
  `INPUT_DECODE_FAILED`로 매핑한다.

region 실행 의미론은 공통 계약에서 “요청 순서가 보존되는 독립 extraction window”로
정한다. provider는 각 region을 입력 순서대로 실행하고 page→region→provider reading
order를 결과 순서로 사용한다. overlap은 중복 결과를 합치지 않으며, empty region은
`INVALID_REQUEST`, geometry가 없는 provider 결과는 region을 임의로 추정하지 않고
`sourceRegion=null`로 보존한다. 기존 Tesseract facade는 plain text에서는 Tess4J의
region 호출 의미를 유지하고, structured word에서는 현재 `OcrBoundingBox.intersects`
필터를 유지한다. 이 legacy 차이는 공통 API의 silent equivalence로 주장하지 않으며,
overlap/disjoint/empty/geometry-null fixture에서 명시적으로 비교한다.

limit 적용 순서와 책임은 다음처럼 고정한다.

1. **encoded bytes** — Ktor/Spring edge와 `OcrImage` 생성자가 먼저 body와 byte hard cap을
   검사한다.
2. **decode metadata** — decoder가 media type, metadata bytes, page count, dimensions를
   읽되 pixel buffer를 만들기 전에 metadata/page/side hard cap을 적용한다.
3. **decoded pixels/pages/total pixels** — multipage coordinator가 각 page와 합계 cap을
   preflight하고 모든 page가 통과하기 전에는 OCR을 시작하지 않는다. `TiffMultiPageOcr`
   의 기존 partial-result 금지와 failure reason을 그대로 fixture로 보존한다.
4. **result size** — provider adapter가 text characters, entries, geometry depth/body
   limits를 bounded result builder에서 적용한다.
5. **concurrency/deadline** — service/adapter가 bounded executor, queue, connect/write/read/
   total deadline을 적용한다. caller가 전달한 limit은 common hard cap보다 커질 수 없다.

직접 `OcrProvider`를 호출하는 사용자는 edge를 거치지 않을 수 있으므로 provider adapter
또는 공통 `OcrPreflight` seam이 1–4의 hard cap을 재검증해야 한다. 같은 제한을 여러 계층에
두더라도 먼저 실패한 계층의 stable reason을 보존하고, `PAGE_COUNT_UNKNOWN` 같은 넓은
매핑으로 실제 `DECODE_FAILED`·`ENGINE_FAILED`를 숨기지 않는지 negative fixture로 확인한다.

### 4.2 결과와 geometry

`OcrResult`는 항상 `text`와 `document`를 가지며, `detail=PLAIN_TEXT`인 경우
document의 lower-level list가 비어 있을 수 있다. structured result의 권장 shape은
다음 의미를 가진다.

| 필드 | 계약 |
|---|---|
| `text` | provider post-processing 후 plain text; `trimText` 정책 적용 결과 |
| `appliedRequest` | provider가 실제로 적용한 common language/detail/trim/region 식별자 요약; provider-specific config와 secret은 포함하지 않음 |
| `languages` | provider가 실제로 사용했거나 보고한 language; 요청값과 다를 수 있으면 명시적 metadata로 기록 |
| `script` | provider가 확정할 때만 optional BCP-47/script tag; 추정 불가 시 `null` |
| `pages` | 단일 이미지도 page index 0인 하나의 page를 만들며, 다중 page는 후속 input contract에서만 허용 |
| `blocks`, `lines`, `words` | 요청 detail과 provider capability에 따라 채우며 order는 reading order로 고정 |
| `boundingBox` | source pixel coordinate의 `x`, `y`, positive `width`, `height`; 없으면 `null` |
| `confidence` | 0.0..100.0 범위의 provider confidence; scale 변환은 adapter에서 수행하고 불명확하면 `null` |
| `sourceRegion` | request region id를 복사한 metadata; provider result의 arbitrary path/URL은 넣지 않음 |
| `providerId` | stable lowercase identifier; version, model digest, endpoint URL은 result public text에 섞지 않음 |

`appliedRequest`는 현재 `OcrResult.options`를 공통 계약에서 안전하게 대체하는
read-only 요약이다. Tesseract legacy facade는 이 요약과 adapter의 검증된 config를
기존 `OcrOptions`로 복원하고, common API가 `tessdataPath`나 `ITessAPI`를
serialization graph로 끌어오지 않도록 한다. provider가 요청값을 변환하거나 일부를
지원하지 못하면 성공 응답 대신 capability/error contract를 적용한다.

geometry가 polygon/rotated box뿐인 provider는 공통 rectangle을 임의로 만들어 품질을
과장하지 않는다. 공통 API에 polygon을 추가할 필요가 생기면 별도 Type-A API review와
fixture version bump를 거친다. PaddleHTTP adapter가 반환하는 좌표계·origin·resize
변환은 response metadata와 golden fixture에 함께 기록해야 한다.

### 4.3 capability와 provider 설정

`OcrCapabilities`에는 `PLAIN_TEXT`, `LINE`, `WORD`, `PAGE`, `REGION`, `CONFIDENCE`,
`GEOMETRY`, `LANGUAGE_DETECTION`처럼 common capability만 포함한다. capabilities는
실행 전 조회 가능해야 하지만, 실제 response가 capability를 충족하지 못하면
`MALFORMED_RESPONSE` 또는 `UNSUPPORTED_CAPABILITY`로 실패한다.

Tesseract의 `tessdataPath`, `TesseractEngineMode`, `TesseractPageSegmentationMode`,
`variables`, `configs`와 PaddleHTTP의 endpoint URI, authentication, model id, request
schema, retry, JSON fields는 각각 `TesseractProviderConfig`와 `PaddleHttpProviderConfig`
처럼 provider module 안에 둔다. common module은 이 concrete type을 import하지 않는다.
configuration 객체는 다음을 준수한다.

- secret, absolute model path, arbitrary URL query, raw HTTP headers를 `toString`,
  result, exception message, metrics label에 넣지 않는다.
- provider id와 supported capability를 시작 시 검증하고, unknown option은 ignore하지
  않고 deterministic configuration error로 거부한다.
- request마다 mutable global map이나 singleton mapper를 바꾸지 않는다. call-local
  immutable capability record를 사용한다.
- provider-specific typed options를 JSON으로 직렬화할 필요가 생기면 해당 provider
  module의 versioned schema와 별도 compatibility fixture를 만든다. common wire schema에
  discriminator를 추가하는 것은 이 issue의 결정 범위를 넘는다.

### 4.4 오류, sanitization, cancellation

공통 exception은 stable `reasonCode`, `providerId`, optional opaque `requestId`만
공개한다. 권장 reason set은 다음과 같다.

| reason | HTTP/route mapping 예 | raw cause 처리 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 입력값·path·payload를 메시지에 재출력하지 않음 |
| `INPUT_DECODE_FAILED` | 400 | image parser/native stack은 내부 log에만 |
| `LIMIT_EXCEEDED` | 413 또는 422 | 초과한 값의 일부만 allowlisted metric으로 기록 |
| `UNSUPPORTED_CAPABILITY` | 422 | provider capability record를 내부 evidence로 남김 |
| `PROVIDER_UNAVAILABLE` | 503 | endpoint, credential, model path를 공개하지 않음 |
| `UPSTREAM_TIMEOUT` | 504 | retry/attempt/timeout profile만 기록 |
| `UPSTREAM_FAILURE` | 502/503 | upstream body·stack·secret을 전파하지 않음 |
| `MALFORMED_RESPONSE` | 502 | raw response를 log/artifact에 남기지 않고 hash/reason만 보존 |
| `CONFIGURATION` | startup failure 또는 503 | model/cache path와 license receipt는 sanitized id만 |
| `INTERNAL` | 500 | caller-facing message는 고정 문구 |

`CancellationException`은 provider failure로 매핑하지 않고 즉시 재전파한다. 현재
`images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt:110-118`의
broad `catch (RuntimeException)`은 JVM의 `CancellationException`도 감쌀 수 있으므로,
후속 adapter에서는 반드시 `catch (CancellationException) { throw e }`를 모든 broad catch보다
앞에 둔다. 이 차이를 기존 동작과 새 동작의 regression fixture로 기록한다. blocking
Tesseract/HTTP call이 cancellation을 실제 중단하지 못할 수 있으므로, `suspend` bridge는
`withContext(Dispatchers.IO)`와 caller-owned timeout을 사용하고, adapter는 socket/read
timeout 및 bounded executor를 별도로 가진다. timeout이 지나도 native process/thread가
계속 실행될 수 있는 경우 이를 “취소 성공”으로 문서화하지 않는다.

## 5. Transport 및 PaddleHTTP 경계

### 5.1 선택지 비교

| 경계 | 장점 | 단점/위험 | 판정 |
|---|---|---|---|
| Python/Paddle in-process embedding | 호출 overhead가 작을 가능성 | Python ABI, native allocator, model cache, JVM lifecycle·license를 한 process에 결합 | 거부 |
| JNI 직접 binding | JVM 호출 표면 | JNI ABI, native crash, platform matrix, model/preprocess fidelity를 library 사용자가 부담 | 거부 |
| 호출당 CLI | 장애 격리와 구현 단순성 | cold start·model reload·stderr/exit code·timeout·child cleanup가 매 요청 비용 | 거부 |
| persistent local process | model warm 상태와 process 격리 | supervisor·health·auth·upgrade·port lifecycle을 별도 운영 | 보류 |
| self-hosted HTTP adapter | 언어 중립 provider boundary, JVM artifact와 Python runtime 분리 | network timeout·auth/TLS·body/RSS·SSRF·schema drift를 모두 검증해야 함 | 조건부 후보 |
| gRPC 전용 | streaming/schema tooling | #545에서 조사한 기본 stable serving contract가 HTTP 중심이며 protocol 추가 운영비가 큼 | 현재 거부 |
| hosted external API | setup 편의 | 민감 이미지 egress, residency, quota, outage, credential | 기본 거부 |

`#545`의 결론을 계승하여 `images-ocr-paddle-http`는 #547이 `ADOPT`로 결정하고
실제 model/container receipt와 #544 benchmark가 준비된 뒤에만 생성한다. #547이
`DEFER`이면 이 module과 dependency를 만들지 않고 문서 상태를 닫는다.

### 5.2 HTTP adapter 계약

Paddle adapter는 common `OcrRequest`를 provider schema로 변환하고, response를
common `OcrResult`로 검증·정규화한다. 기본 transport 계약은 다음과 같다.

- endpoint는 operator configuration으로만 지정하며 caller image/metadata가 URL을
  결정하지 않는다. URL input mode와 arbitrary remote fetch는 비활성화한다.
- connect, write, read, total deadline을 각각 두고 common `limits.timeout`보다 큰
  transport timeout을 허용하지 않는다. retry는 idempotent request에만 bounded budget을
  적용하고, timeout 후 무한 retry를 하지 않는다.
- request body, response body, page/pixel/word 수, JSON nesting/string size를 streaming
  또는 bounded decode 단계에서 제한한다. 전체 response를 무제한 문자열로 만들지 않는다.
- authentication/TLS/CA 설정은 adapter config에만 두고 log, exception, metric tag에
  secret·Authorization·endpoint query를 남기지 않는다. private network/loopback 접근과
  SSRF 방어 정책은 deployment와 adapter 양쪽에서 검증한다.
- upstream status, content-type, schema version, provider id, geometry coordinate
  system을 확인한다. unknown field가 의미를 바꾸면 fail closed한다.
- health check는 model/cache receipt와 분리한다. startup 성공이 quality/benchmark
  성공을 의미하지 않으며, `READY`, `UNAVAILABLE`, `FAILED_SMOKE`를 구별해 기록한다.

0.5.0의 내부 HTTP dialect는 Jackson 3로 codec을 구현하는 version 1 JSON envelope로
고정한다. 외부 public API가 아니라 `PaddleHttpProvider`와 self-hosted service 사이의
private contract다.

| 방향 | 필수 필드 | 규칙 |
|---|---|---|
| request | `schemaVersion`, `requestId`, `image.mediaType`, `image.base64`, `languages`, `detail`, `regions`, `limits` | UTF-8 JSON, base64는 bounded decode, request ID는 opaque UUID, URL·credential·provider option은 body에 넣지 않음 |
| response | `schemaVersion`, `providerId`, `appliedRequest`, `document` 또는 `error` | `document`와 `error`는 배타적이며, coordinate system·confidence scale을 metadata로 검증 |
| error | `code`, `retryable`, `requestId` | upstream body/stack/path/secret은 금지하고 allowlisted code만 허용 |

codec은 field order를 canonicalize하고 UTF-8 BOM, unknown/duplicate field, trailing token,
unsupported schema version을 거부한다. base64 image와 response text/entry/중첩 깊이는
streaming 또는 bounded parser에서 제한한다. JSON fixture는 정상/unknown/duplicate/trailing/
version/body-limit/geometry-invalid 각각의 SHA-256을 보유한다.

관측성은 payload가 아닌 저-cardinality 상태만 기록한다. 구현 PR에서 다음 이름과 label
allowlist를 고정한다.

| metric | 허용 label | 값/금지 사항 |
|---|---|---|
| `bluetape4k_ocr_requests_total` | `provider`, `outcome`, `detail` | text, language 조합, endpoint, request ID를 label로 사용하지 않음 |
| `bluetape4k_ocr_request_duration_seconds` | `provider`, `outcome` | 전체 latency와 timeout 여부를 기록하되 image 크기·text를 tag로 넣지 않음 |
| `bluetape4k_ocr_queue_depth` | `provider` | bounded executor의 현재 queue만 관측 |
| `bluetape4k_ocr_retries_total` | `provider`, `reason` | retry count는 metric 값, upstream body는 저장하지 않음 |
| `bluetape4k_ocr_cancellations_total` | `provider`, `phase` | `preflight`, `transport`, `decode`, `engine`만 허용 |

`requestId`는 log correlation용 opaque 값으로만 사용하고 metric label에는 넣지 않는다.
duration/queue/retry/cancel은 sanitized error code와 provider id만 함께 기록하며, raw
OCR text, image bytes, Authorization, endpoint query와 local/model path는 log/artifact에
남기지 않는다.

### 5.3 WebFlux/Ktor/Spring 호출 경계

현재 저장소에는 OCR WebFlux route/module이 없다(`rg -n -i "webflux|WebFlux"` 결과는 이
계획 문서뿐). 따라서 WebFlux는 현재 migration 대상이 아닌 N/A surface로 기록하되,
후속 adapter가 추가될 경우 다음 계약을 반드시 따른다.

- WebFlux `Mono` 또는 Kotlin `suspend` bridge는 event-loop에서 blocking OCR을 실행하지
  않고 bounded scheduler/`Dispatchers.IO`로 격리한다.
- downstream backpressure를 무시해 무제한 upload/response buffer를 만들지 않으며,
  encoded/decoded/result limit을 route와 provider 양쪽에서 재검증한다.
- subscriber cancellation은 provider timeout/cancel phase로 전파하고, native/HTTP가
  실제 중단되지 않는 경우 orphan work와 bounded cleanup을 관측한다.
- `INVALID_REQUEST`, `LIMIT_EXCEEDED`, `UPSTREAM_TIMEOUT`, `PROVIDER_UNAVAILABLE`를
  WebFlux response status와 stable JSON error로 매핑하고, Ktor/Spring과 동일한
  sanitization fixture를 재사용한다.
- WebFlux 모듈·scheduler·CI job을 추가하는 것은 별도 Type-A issue로 분리하며, 현재
  #546에서는 dependency나 route를 생성하지 않는다.

웹 프레임워크 route는 common provider를 직접 만들거나 HTTP body를 provider-specific
JSON으로 조립하지 않는다.

1. Ktor/Spring edge에서 multipart content type, encoded byte, decoded pixel/side/page를
   먼저 제한한다.
2. `OcrImage`로 변환한 뒤 provider-neutral request를 생성한다.
3. service-level timeout/cancellation을 적용하고 provider exception reason을 public
   response code로 mapping한다.
4. raw upload, OCR text, upstream body, local path는 기본 log/metric에 남기지 않는다.
5. response DTO는 공통 result에서 필요한 text/geometry만 선택해 반환하고 provider id,
   model path, endpoint를 외부 계약으로 노출하지 않는다.

현재 quickstart의 `maxInputBytes`, `maxInputPixels`, `maxInputSide`, `Dispatchers.IO`
경계를 유지하되, `OcrLimits`와 web multipart limit이 서로 다른 오류를 내지 않도록
negative fixture와 route test에서 우선순위를 고정한다.

## 6. 기존 호출자 migration matrix

| 현재 surface | 현재 의존 | 0.5.0 compatibility plan | 후속 Type-A 검증 |
|---|---|---|---|
| `OcrEngine.recognize(ImmutableImage, OcrOptions)` | Tess4J-coupled option, blocking | 기존 signature 유지; legacy/provider 내부 seam은 가능한 경우 `ImmutableImage` decoded input을 직접 전달하고, HTTP path에서만 deterministic PNG `OcrImage`로 변환 | Kotlin source compile + Java `javap`/binary smoke, direct-vs-encoded round-trip/plain text golden |
| `StructuredOcrEngine.recognizeStructured` | detail enum, page/block/line/word list | 기존 result를 유지; 공통 document를 legacy DTO로 변환하고 missing box/confidence를 `null`로 보존 | WORD/LINE/PLAIN_TEXT fixture, list order/confidence/region assertion |
| `OcrOptions.languages` | `+` language expression | common request language list로 복사; Tesseract adapter만 `+` 표현식으로 변환 | `eng+kor` language fixture와 invalid blank/duplicate case |
| `tessdataPath`, engine/page mode, variables, configs | Tess4J/`ITessAPI` | common API에서 제거; Tesseract config builder가 소유. 기존 constructor는 deprecation 기간 유지 | public API leak check가 `ITessAPI`를 `images-ocr-api`에서 발견하지 않음 |
| `trimText`, `structuredDetail`, `regions` | provider-independent semantics 후보 | common request fields로 승격; unsupported detail은 error, region id/geometry는 deterministic | trim, region intersection, unsupported capability negative fixture |
| `OcrException`/`OcrConfigurationException` | raw cause 전달 가능 | legacy exception은 호환 유지하되 new exception은 reason code + sanitized message; route는 원인 공개 금지 | raw path/stack/secret leakage assertion; `CancellationException` rethrow |
| `ImmutableImage.extractText` | default new `TesseractOcrEngine` | 기존 default 동작 유지. 신규 provider 선택은 explicit provider parameter/config로만 | existing extension tests + fake provider injection |
| `ImmutableImage.extractOcr` | structured Tesseract default | 기존 return type 유지; 공통 result 변환 facade 추가 | structured fixture equality |
| `suspendExtractText`/`suspendExtractOcr` | `withContext(Dispatchers.IO)` | dispatcher overload 유지; common provider async bridge를 별도 제공 | pre-dispatch cancellation, timeout, blocking provider isolation |
| `TiffMultiPageOcr.recognize`/`suspendRecognize` | encoded/metadata/page/total-pixel/result limits, stable `TiffMultiPageOcrFailureReason`, page preflight와 cleanup | TIFF coordinator를 common provider의 내부 orchestration으로 유지하고, 원본 TIFF page 순서와 기존 blocking/suspend entrypoint를 보존 | page order, 모든 page preflight 전 OCR 금지, partial result 0건, limit별 reason, cancellation cleanup |
| Ktor OCR quickstart | multipart + `OcrOptions` + Tess4J bean/default | route/request decoding을 common request로 바꾸는 별도 example PR; 0.5.0 기본 provider는 Tesseract | route body/pixel/timeout/error contract test |
| Spring Boot OCR quickstart | `OcrEngine` bean + properties + `OcrException` handler | provider-neutral bean contract과 Tesseract config properties를 분리하는 별도 example PR | context startup, 400/413/503/504 mapping |
| WebFlux OCR surface | 현재 저장소에 route/module 없음 | #546에서는 N/A로 고정하고, 추가 시 event-loop blocking·backpressure·cancellation 계약을 별도 Type-A issue로 분리 | `Mono`/suspend bridge와 bounded scheduler/response mapping 설계 review |
| README/manual/KDoc | `images-ocr`가 OCR contract와 provider를 모두 설명 | API module, Tesseract default, conditional Paddle HTTP, migration/deprecation을 EN/KO 동등 구조로 갱신 | link/heading/term audit, old provider claim drift 0 |
| benchmark `TesseractOcrExtractionBenchmark` | hash-pinned manifest fixture, `OcrOptions`, `ImmutableImage.extractText`, host tessdata/language preflight | common request/provider 선택을 benchmark harness에 additive로 도입하고 기존 Tesseract baseline을 유지 | same corpus/manifest, provider/model/environment hash, no-network scheduled run, CER/WER/geometry/cold-warm/RSS artifact |
| Java serialization callers | current DTO `Serializable` + serialVersionUID | legacy in-process round-trip만 compatibility fixture로 보존; new wire는 versioned JSON, serialized bytes를 HTTP contract로 사용하지 않음 | old/new stream round-trip and explicit non-goal fixture |
| BOM consumers | `bluetape4k-image-bom` published subprojects | `images-ocr-api`는 common contract artifact로 BOM 후보; Paddle HTTP는 #547 ADOPT 후에만 포함 | generated POM/GMM, API/runtime dependency graph, isolated consumer compile |

### 6.1 compatibility fixture 계약

후속 implementation train은 다음 fixture를 repository-owned deterministic test resource로
추가한다. 이 계획에서는 fixture identity와 expected evidence만 고정한다.

| fixture id | 입력/행동 | 고정 assertion |
|---|---|---|
| `ocr-legacy-plain-text-v1` | 기존 `ImmutableImage.extractText`와 fake Tesseract provider | text, trim, default `eng`, exception type/source compatibility |
| `ocr-legacy-structured-word-v1` | `extractOcr(OcrStructuredDetail.WORD)` | page 0, block/line/word order, confidence, null geometry |
| `ocr-common-request-v1` | byte copy, media type, language/region/limit validation | defensive copy, blank/oversize/negative rejection, canonical JSON fixture |
| `ocr-image-immutable-roundtrip-v1` | same `ImmutableImage` through legacy direct seam and deterministic PNG byte path | dimensions, alpha, orientation, media type, text/geometry equivalence; metadata stripping and decode failure policy |
| `ocr-provider-capability-v1` | fake provider가 PLAIN_TEXT만 지원하는데 WORD 요청 | `UNSUPPORTED_CAPABILITY`, silent downgrade 없음 |
| `ocr-error-sanitization-v1` | Tess4J/HTTP error에 path, URL, secret, stack가 포함된 fake cause | public reason/message에서 민감 token 0건 |
| `ocr-cancellation-v1` | blocking fake provider + pre-dispatch/active coroutine cancellation | pre-dispatch no invocation, `CancellationException` 재전파, retry 없음 |
| `ocr-tiff-multipage-v1` | ordered multi-page TIFF, page/total-pixel/metadata/result limits, blocking/suspend cancellation | preflight-before-OCR, page order, partial result 0건, stable `TiffMultiPageOcrFailureReason`, cleanup and broad-catch mapping |
| `ocr-http-contract-v1` | fixed Jackson 3 request/response JSON, unknown/duplicate field/version/trailing data/body limit | schema version, coordinate system, bounded decode, malformed response mapping, canonical field order/SHA-256 |
| `ocr-region-semantics-v1` | overlap/disjoint/empty/geometry-null regions across fake and Tesseract-compatible provider | request-order extraction, page/region reading order, no silent merge/clipping, legacy intersection distinction |
| `ocr-observability-v1` | success/timeout/retry/cancel/error with opaque request ID | metric names and low-cardinality labels, duration/queue/retry/cancel, zero payload/secret leakage |
| `ocr-java-consumer-v1` | Java compile against published API and legacy artifact | Java collection/readable signature, no Tess4J type in common API, binary smoke |
| `ocr-serialization-legacy-v1` | current `Serializable` DTO old bytes | only documented legacy in-process round-trip; HTTP never accepts Java bytes |

fixture bytes와 expected JSON은 synthetic image/text만 사용하고, 실제 개인정보·외부
dataset·검증되지 않은 model을 repository에 복사하지 않는다. fixture manifest에는
size, SHA-256, generator/font, license/NOTICE, schema version, expected reason을 기록한다.
#544의 full corpus benchmark가 완료되기 전에는 이 fixture 결과를 Paddle 품질 우위의
증거로 해석하지 않는다.

## 7. Module graph, dependency, BOM, CI 영향

### 7.1 목표 graph

```text
bluetape4k-images
        ^
bluetape4k-images-ocr-api  <--- common request/result/error/limits only
        ^             ^
bluetape4k-images-ocr  bluetape4k-images-ocr-paddle-http (conditional)
   Tess4J only          HTTP client + no Paddle/Python in JVM
        ^             ^
 examples/ktor-ocr-api  examples/spring-boot-ocr-api
```

- `images-ocr-api`는 `bluetape4k-images`를 직접 의존하지 않는 것을 기본안으로 한다.
  encoded image input을 사용해 scrimage와 provider runtime을 분리한다. API가 이미지
  dimensions/decoder 결과를 필요로 하면 최소한의 value contract를 별도 review한다.
- 기존 `images-ocr`는 `images-ocr-api`와 `bluetape4k-images`를 사용하고 Tess4J를
  `implementation`으로만 소유한다. `images-ocr-api`의 public signature에는 Tess4J,
  Ktor, Spring, Paddle, Jackson/Kotlinx mapper 타입이 없어야 한다.
- `images-ocr-paddle-http`는 #547 `ADOPT` 후에만 생성한다. Paddle/Python/model은
  Gradle dependency가 아니며, HTTP client와 common API만 JVM graph에 들어간다.
- examples는 provider artifact와 API artifact를 명시적으로 선택한다. default provider를
  자동 발견하는 classpath scan이나 remote model download를 추가하지 않는다.

### 7.2 BOM/catalog 영향

후속 PR에서 다음 순서로 검증한다.

1. `settings.gradle.kts` project registration과 module directory를 추가한다.
2. version catalog는 기존 alias와 중앙 version source를 재사용하고, new provider
   dependency version을 임의로 module build file에 hard-code하지 않는다.
3. `bom/build.gradle.kts`가 published `images-ocr-api`와 `images-ocr`를 정확히
   constraint하는지 확인한다. `images-ocr-paddle-http`는 adoption decision 전에
   BOM에 넣지 않는다.
4. `apiElements`, `runtimeClasspath`, generated POM/GMM에서 common API가 Tess4J를
   runtime dependency로 끌어오지 않는지 확인한다.
5. isolated JVM/Java consumer가 BOM만 import해 common request/result를 compile하고,
   Tesseract/Paddle provider를 선택적으로 추가할 수 있는지 확인한다.

### 7.3 CI tier

| tier | 범위 | required evidence | 금지 사항 |
|---|---|---|---|
| PR required | API compile, fake provider, legacy facade, JSON golden, malformed/oversize/cancel/error fixture | test report, dependency graph, sanitized log, no-network assertion | model download, external endpoint, GPU, host Tesseract 의존 |
| native opt-in | Tesseract host runtime, tessdata, JNI/native configuration | explicit `-Docr.enabled=true` 결과와 failure reason | native failure를 retry로 숨김 |
| service opt-in/nightly | pinned Paddle service/container small CPU smoke | image digest, model manifest, SBOM/license, offline startup, auth/TLS/limits receipt | mutable tag, first-use network, hosted API |
| scheduled benchmark | #544 corpus와 동일 host/profile의 CER/WER/geometry, cold/warm p50/p95/p99, RSS | raw attempt, run manifest, summary, environment/model hash | vendor marketing metric을 repository baseline으로 사용 |
| release gate | selected provider의 final provenance/NOTICE/SBOM/rollback + compatibility consumer | immutable release checklist | #544/#545 미완료 상태의 provider 기본 승격 |

Tesseract native와 Paddle service 검증은 서로 다른 failure domain이므로 한 job의
`green`이 다른 provider의 지원을 증명하지 않는다. PR에서 Docker image pull이나
model registry connectivity가 발생하면 policy gate 위반으로 분류하고 구현을 중단한다.

## 8. Type-A stacked PR train 분해

모든 branch는 semantic prefix를 사용하고, 각 PR은 직전 train head를 base로 삼는다.
아래 순서에서 하나라도 contract test 또는 metadata gate가 실패하면 다음 PR을 만들지
않는다.

| 순서 | issue/PR 역할 | base → head | 주요 변경 | 완료 gate |
|---|---|---|---|---|
| 0 | #543 policy (선행 merged) | `develop` → `docs/issue-543-policy` | provenance/offline/license/CI 공통 정책 | policy receipt와 source ledger |
| 1 | #544 benchmark contract (선행) | `docs/issue-543-policy` → `docs/issue-544-benchmark` | corpus/metric/run manifest 설계 | 실제 비교는 PENDING으로 명시 |
| 2 | #545 service research (선행 merged) | `docs/issue-544-benchmark` → `docs/issue-545-service` | HTTP/service/security/CI 조건 | Paddle provider DEFER, HTTP conditional |
| 3 | #546 design (현재) | `docs/issue-544-benchmark` → `plan/issue-546-ocr-api` | common API, migration, fixture, graph/CI 계획 | 문서 SPW gate + issue read-back |
| 4 | #547 provider adoption gate | `plan/issue-546-ocr-api` → decision PR/issue | #544 실제 결과 + #545 receipt + #546 설계만 입력으로 사용; production API/dependency/model/container 변경 금지 | fresh `ADOPT`/`DEFER`/`REJECT`와 unresolved risk disposition |
| 5a | Type-A API contract (ADOPT 또는 명시적 Tesseract-only re-scope 후) | decision head → `feat/ocr-api-contract` | `images-ocr-api`, common DTO/provider/error/limits, Jackson 3 private codec, fake fixture | API compile, JSON golden, no Tess4J/Jackson public leak |
| 5b | Tesseract compatibility | `feat/ocr-api-contract` → `feat/ocr-tesseract-adapter` | current `images-ocr` facade/adapter, options mapping, coroutine/cancellation bridge | old caller/source/binary/legacy fixture, `CancellationException` rethrow |
| 5c | benchmark caller migration | `feat/ocr-tesseract-adapter` → `feat/ocr-benchmark-provider` | manifest/hash-pinned harness, provider selection, baseline-preserving scheduled lane | same corpus, environment/model hash, no-network and quality/performance artifacts |
| 6a | conditional Paddle HTTP | `feat/ocr-benchmark-provider` → `feat/ocr-paddle-http` | only if #547 `ADOPT`: HTTP v1 codec, bounded JSON, auth/TLS, receipt | offline CPU smoke, schema/security/benchmark gate |
| 6b | deferred closeout | `feat/ocr-benchmark-provider` → docs closeout | if #547 `DEFER`/`REJECT`: no Paddle provider dependency; document re-evaluation and any Tesseract-only re-scope | explicit decision, no unresolved public API assumption |
| 7 | examples/BOM/release consumer | accepted provider head → `feat/ocr-ocr-examples-bom` | Ktor/Spring migration, WebFlux N/A/optional issue link, BOM/catalog, docs, Java consumer | generated POM/GMM, route/manual parity, consumer compile |

`#546`은 #544/#545의 설계 branch를 누적해 포함할 필요가 없으므로 현재 base는
`docs/issue-544-benchmark`로 정한다. #545의 merged commit이 이 base에 포함되어
있고, #553 등 열린 review branch는 #546의 production dependency 선행 조건으로
간주하지 않는다. #547 결정 전에는 public API/provider/benchmark implementation PR을
생성하지 않는다. #547이 `ADOPT`이면 표의 5a부터, `DEFER`/`REJECT`이면 명시된
Tesseract-only re-scope 또는 문서 closeout만 별도 승인 후 진행한다. PR creation 후에는
exact head, checks, review, metadata를 fresh-read하고 merge는 별도 사용자 승인 없이는
수행하지 않는다.

### 각 Type-A PR 공통 gate

- RED fixture를 먼저 추가하고 현재 behavior를 기록한 뒤 implementation을 시작한다.
- public API에서 provider-specific package/import가 보이지 않는지 `javap`, Kotlin
  signature, dependency report로 확인한다.
- `CancellationException` 재전파, timeout, body/pixel/page/concurrency limit,
  raw cause/path/secret sanitization을 negative test로 고정한다.
- Korean README/KDoc/manual과 EN/KO 구조 parity를 후속 문서 PR에서 유지한다.
- native/service/benchmark 결과는 network-free PR evidence와 분리하고, skipped 또는
  `IN_PROGRESS` job을 성공으로 보고하지 않는다.

## 9. 실행 순서와 테스트 계획

이 issue의 문서 작업에서는 다음을 실행한다.

```bash
git diff --check
rg -n '미완료|PENDING|DEFER' \
  docs/superpowers/plans/2026-08-23-issue-546-ocr-api-boundary.md
node /Users/debop/.codex/skills/bluetape-writer/scripts/audit-korean-terms.mjs \
  docs/superpowers/plans/2026-08-23-issue-546-ocr-api-boundary.md
```

후속 Type-A implementation에서 최소한 다음 순서로 검증한다.

```bash
./gradlew :bluetape4k-images-ocr-api:test
./gradlew :bluetape4k-images-ocr:test --tests '*Ocr*' --tests '*ImmutableImageOcrExtensions*'
./gradlew :bluetape4k-images-ocr-api:compileKotlin :bluetape4k-images-ocr:compileKotlin
./gradlew :bluetape4k-images-ocr-api:dependencies --configuration apiElements
./gradlew :bluetape4k-images-ocr-api:dependencies --configuration runtimeClasspath
./gradlew detekt
./gradlew :bluetape4k-image-bom:generatePomFileForBluetapeImagePublication
git diff --check
```

조건부 Paddle train은 위 검증 뒤에만 `-Docr.container.enabled=true`와 pinned CPU
smoke를 순차 실행한다. #544 benchmark는 PR required가 아니라 scheduled/manual lane으로
동일 corpus·host·limit profile·model digest를 사용한다.

## 10. 위험, rollback, 재평가 조건

### 위험과 완화

| 위험 | 완화 |
|---|---|
| common API가 Tesseract semantics를 이름만 바꿔 복제 | `ITessAPI`, tessdata, configs, variables를 common module에서 금지하고 API leak test를 required로 둠 |
| provider마다 geometry/score scale이 다른데 동일 수치로 비교 | coordinate system/confidence scale을 metadata와 fixture로 고정하고 불명확한 값은 `null` |
| blocking cancellation이 실제 native/HTTP 작업을 중단하지 않음 | `CancellationException` rethrow, caller timeout, bounded executor/socket timeout을 별도 계약으로 명시 |
| HTTP response/model path/secret leak | sanitized reason, no-log, bounded decode, auth/TLS, endpoint allowlist, raw body 미보존 |
| Java `Serializable`/Kotlin list mutation으로 호환성 손상 | legacy stream fixture, defensive copy, new versioned JSON fixture와 separate wire contract |
| Paddle model/runtime가 PR network를 시작 | #543 offline cache/provenance gate와 no-network PR test, model/container receipt 없으면 DEFER |
| benchmark가 API 설계를 과도하게 최적화 | #544 metric은 동일 workload의 evidence로만 사용하고 vendor metric·미실행 결과를 결정에 사용하지 않음 |

### rollback

- 이 issue는 plan artifact만 변경하므로 `plan/issue-546-ocr-api` commit을 revert하면
  저장소 동작에는 영향이 없다.
- 후속 API PR은 `images-ocr` 기존 facade와 Tesseract default를 먼저 유지한 상태에서
  additive로 병합한다. common contract가 깨지면 새 module/feature flag를 되돌리고
  기존 extension을 복구한다.
- Paddle provider가 #547 이후에도 quality, receipt, offline, security 중 하나라도
  실패하면 adapter dependency/branch/BOM entry를 merge하지 않고 `DEFER` 문서와
  재평가 issue만 남긴다.
- model/cache rollback은 파일명 변경이 아니라 검증된 manifest+SHA-256 receipt를 이전
  승인 identity로 다시 선택하는 방식만 허용한다.

### 재평가 전 필수 evidence

1. #544의 실제 Tesseract/Paddle 동일 corpus 결과와 raw run manifest.
2. #545의 pinned service/container digest, model/source/license/SBOM, offline CPU smoke.
3. #546 fixture가 Java/Kotlin source/binary/legacy stream/JSON/route 경계를 모두 통과.
4. #547의 명시적 `ADOPT`/`DEFER` 결정과 unresolved risk disposition.
5. upstream release/model/schema가 바뀐 경우 exact version·URL·digest 재조회.

## 11. Source-to-claim ledger

| 주장 | 저장소 근거 | 후속 검증 |
|---|---|---|
| 현재 API가 `ImmutableImage`와 Tess4J option에 결합 | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt:3-22`, `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt:8,26-35` | `images-ocr-api` public signature/dependency report |
| structured level과 geometry가 현재 결과 의미론 | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt:25-43`, `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt:67-281` | WORD/LINE/PLAIN_TEXT golden fixture |
| Tesseract가 call-local native state와 region/structured extraction을 사용 | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt:26-139,168-216` | Tesseract fake client + opt-in host smoke |
| coroutine wrapper는 `Dispatchers.IO`에서 blocking call 실행 | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/ImmutableImageOcrExtensions.kt:43-81` | pre-dispatch cancellation/timeout test |
| TIFF coordinator가 preflight·page order·partial-result 금지를 보유 | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TiffMultiPageOcr.kt:35-218` | multipage/limit/cleanup/catch-mapping fixture |
| Ktor/Spring이 body/pixel/side limit 후 OCR을 호출 | `examples/ktor-ocr-api/src/main/kotlin/io/bluetape4k/images/examples/ktor/ocr/KtorOcrApiApplication.kt:58-144`, `examples/spring-boot-ocr-api/src/main/kotlin/io/bluetape4k/images/examples/spring/ocr/SpringBootOcrApiApplication.kt:69-177` | route integration and 400/413/503/504 matrix |
| WebFlux OCR surface가 현재 없음 | repository-wide `rg -n -i "webflux|WebFlux"`에서 이 계획 문서 외 결과 없음 | future WebFlux adapter 별도 Type-A issue와 bounded scheduler/cancellation review |
| Tess4J dependency가 현재 `images-ocr` 구현에만 있음 | `images-ocr/build.gradle.kts:12-16` | API module POM/GMM and `apiElements` check |
| BOM이 published subproject를 자동 constraint | `bom/build.gradle.kts:12-33` | generated POM/GMM and isolated Java consumer |
| benchmark caller가 Tesseract public surface를 직접 사용 | `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/TesseractOcrExtractionBenchmark.kt:5-53`, `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/OcrBenchmarkFixtures.kt:12-206` | provider migration, manifest/environment hash, scheduled no-network evidence |
| Paddle in-process/CLI 거부, HTTP 조건부 | `docs/superpowers/research/2026-08-19-issue-545-paddleocr-service-security-ci.md:24-42` | #547 adoption gate and service receipt |
| offline/provenance/license/CI gate | `docs/superpowers/research/2026-08-19-issue-543-ai-ml-supply-chain-policy.md:10-21,124-202` | policy receipt, no-network and artifact manifest |
| benchmark 비교 실행은 아직 PENDING | `docs/superpowers/research/2026-08-19-issue-544-ocr-benchmark-corpus.md:8-34,649-672` | raw run manifest and quality/performance gate |

이 ledger의 local line은 base `32c8f09...` 기준이다. 구현 PR을 시작하기 전에 해당
파일이 이동·변경되면 line number를 새로 읽고 claim과 fixture를 갱신한다. local research
문서의 upstream URL·release·license는 구현 직전에 다시 검증하며, 오래된 `latest` 문서를
채택 증거로 재사용하지 않는다.

## 12. SPW writer gate

- **SPW-01 독자·목적: PASS** — #169/#546 구현자·reviewer가 기존 caller를 깨뜨리지 않고
  provider boundary를 구현할 수 있도록 대상과 비범위를 첫 절에서 고정했다.
- **SPW-02 구조·결정: PASS** — source ledger → common contract → transport 비교 →
  migration fixture → graph/CI → stacked train → rollback 순서로 결정과 실행 경계를
  분리했다.
- **SPW-03 한국어 기술 문체: PASS** — 설명·결정·위험·권고는 자연스러운 한국어로
  작성하고 API 이름, command, path, URL, SHA, reason code는 원문 token을 보존했다.
- **SPW-04 source-to-evidence: PASS** — local source line, 선행 research, 후속 test/
  receipt를 claim ledger와 acceptance table에 연결했다.
- **SPW-05 최종 read-back: PASS** — 현재 문서에는 구현 완료를 가장하는 숫자나 외부
  benchmark 성공 주장이 없고, #544/#545/#547의 미완료 gate와 stop condition을 명시했다.

## 최종 상태

`PLAN READY / IMPLEMENTATION NOT STARTED / #547 GATE REQUIRED / PADDLEOCR CONDITIONAL`

- 계획 문서와 stacked branch만 준비한다.
- #546 문서 PR merge 전에는 production API/module/dependency/model/container를 만들지
  않는다.
- #547이 fresh `ADOPT`/`DEFER`/`REJECT`를 결정하기 전까지 public API/provider/
  benchmark implementation PR을 생성하지 않는다. `ADOPT` 이후에만 Paddle HTTP adapter와
  BOM entry를 생성하고, `DEFER`/`REJECT`이면 명시적 Tesseract-only re-scope 또는 문서
  closeout으로 멈춘다.
- PR/hosted CI/merge/local sync/cleanup은 각 fresh gate와 별도 승인을 거친다.
