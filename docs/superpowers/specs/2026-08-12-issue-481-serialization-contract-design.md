# issue #481 공개 직렬화 계약 재설계

## 목표와 범위

`bluetape4k-images` privacy pipeline과 `bluetape4k-images-spring-boot` runtime 구현체가
실제로 복원 가능한 값만 직렬화 경계에 노출하도록 공개 계약을 정리한다. 현재
`PrivacyDerivativeFormat`은 `@Transient` writer를 non-null로 선언하고,
`PrivacyDerivativeResult`는 Scrimage `ImmutableImage`를, batch result는 `Path`와 임의의
`Throwable`을 보유한다. `LocalImageStorage`, `S3ImageStorage`,
`S3PreSignedUrlSigner`도 live filesystem/AWS collaborator를 가진 채 `Serializable`을
구현한다.

이번 변경은 다음 두 경계를 분리한다.

1. **runtime graph**: 이미지 객체, coroutine writer, filesystem path, AWS facade,
   transfer client, 예외 객체를 포함하며 직렬화하지 않는다.
2. **value/snapshot graph**: 정책, report, encoded payload, 제한된 failure 정보를
   포함하며 Jackson 3 JSON과 Java serialization으로 round-trip 할 수 있다.

unrelated moderation/metadata/upload DTO, 저장소 동작 자체, module topology와 release
manual 변경은 이번 이슈의 named scope 밖이다. 단, `CloudFrontUrlSigner`와 secret-bearing
`CdnProperties.CloudFront`는 Spring runtime의 false serialization graph이므로 이번
경계 정리에 포함한다. 새 Jackson 3 runtime dependency와 published
POM/BOM/runtimeClasspath/ABI evidence도 public snapshot codec을 위해 명시적으로
포함한다.

## 설계 선택

### 선택지 A — runtime과 snapshot DTO 분리 (채택)

runtime 타입에서 `java.io.Serializable`을 제거하고 다음 additive DTO/codec을 제공한다.

- `PrivacyDerivativeFormatId`: 현재 안정적으로 복원 가능한 `JPEG`, `PNG` 식별자.
- `PrivacyDerivativeOptionsSnapshot`: primitive policy, format id, redactions를
  보존한다. runtime의 `ThumbnailSize`, `ThumbnailCrop`, `SensitiveRegion`을 그대로
  노출하지 않고 concrete snapshot model로 평탄화한다. `toOptions()`는 built-in
  JPEG/PNG와 rectangle redaction만 복원하며 custom writer 또는 지원하지 않는 geometry는
  명시적으로 거부한다.
- `PrivacyDerivativePayload`: encoded bytes와 report만 보유한다. 입력 byte와 반환 byte는
  defensive copy를 사용한다.
- `PrivacyDerivativeBatchSnapshot`: source를 문자열로 보존하고 성공 payload 또는
  제한된 `PrivacyDerivativeFailureSnapshot(stage, code)` 중 하나만 보존한다. `Path`와
  `Throwable`은 포함하지 않는다.
- `PrivacyDerivativeJackson`: `bluetape4k-images`가 `api` 범위로 노출하는 Jackson 3.2.1
  기본 codec이다. `tools.jackson` 기반 mapper와 String/ByteArray encode/decode helper를
  제공한다. 외부 입력에는 고정 strict mapper를 사용하고, trusted 확장 경로에서만 caller의
  Jackson 3 `ObjectMapper`를 주입할 수 있으며 public API는 JSON 경계를 명시한다. Ktor의
  HTTP content negotiation에는 자동 등록하지 않고, Ktor/Spring/plain-JVM caller가 같은
  core codec을 명시적으로 호출한다.

기존 runtime 생성자와 `PrivacyDerivativeResult.image` 사용은 유지한다. 기존 Java
serialization을 이미 사용하던 caller는 snapshot으로 마이그레이션해야 하며, runtime
타입의 `Serializable` marker 제거는 의도적인 fail-fast 동작 변경이다.

### 선택지 B — `writeReplace/readResolve` token registry

기존 타입을 `Serializable`로 유지하고 built-in writer를 token으로 복원하는 방법이다.
custom writer, classloader 교체, process 재시작 시 registry 수명이 계약이 되므로 cross-process
복원이 불명확하다. transient null 문제를 완전히 제거하지 못해 채택하지 않는다.

### 선택지 C — kotlinx.serialization 전면 도입

명시적 schema를 얻을 수 있지만 현재 repository의 Ktor/benchmark용 kotlinx 사용과
public privacy DTO의 codec 요구를 한 dependency train으로 묶는다. Jackson 3의 Kotlin
module, 확장 가능한 module/mapper 주입, Spring 생태계와의 정합성을 우선해 채택하지 않는다.

## 공개 타입 계약

### Runtime-only 타입

다음 타입은 source constructor와 일반 in-process 호출을 유지하되 `Serializable`을
구현하지 않는다.

- `PrivacyDerivativeFormat`
- `PrivacyDerivativeOptions`
- `PrivacyDerivativeResult`
- `PrivacyDerivativeBatchResult`와 그 `Success`/`Failure`
- `LocalImageStorage`
- `S3ImageStorage`
- `S3PreSignedUrlSigner`
- `CloudFrontUrlSigner`
- `CdnProperties`와 `CdnProperties.CloudFront` (configuration snapshot은 별도 wire DTO로 만들지 않으며
  PEM/private-key material을 직렬화하지 않는다.)

`PrivacyDerivativeFormat`의 `writer`는 항상 생성 시 non-null이어야 하며 deserialization
후 null이 되는 경로를 더 이상 제공하지 않는다. custom writer는 runtime pipeline에서
계속 사용할 수 있지만 `PrivacyDerivativeOptionsSnapshot`으로 변환할 수 없다.

### Snapshot 타입

snapshot은 모두 명시적인 `serialVersionUID`를 가지며, Java serialization과 Jackson 3
JSON 모두에서 동일한 의미를 보존한다.

```kotlin
enum class PrivacyDerivativeFormatId { JPEG, PNG }

data class PrivacyThumbnailSizeSnapshot(
    val width: Int,
    val height: Int,
    val suffix: String,
) : Serializable

enum class PrivacyThumbnailCropId { FIT, SMART_SOBEL_ENERGY }

data class PrivacyRedactionSnapshot(
    val regionId: String?,
    val coordinateSpace: SensitiveCoordinateSpace,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val mode: PrivacyRedactionMode,
    val maskColorArgb: Int,
    val maskOpacity: Double,
) : Serializable

data class PrivacyDerivativeOptionsSnapshot(
    val stripMetadata: Boolean,
    val removeGps: Boolean,
    val normalizeOrientation: Boolean,
    val maxPixels: Long,
    val maxSide: Int?,
    val thumbnailSize: PrivacyThumbnailSizeSnapshot?,
    val thumbnailCrop: PrivacyThumbnailCropId,
    val outputFormat: PrivacyDerivativeFormatId,
    val redactions: List<PrivacyRedactionSnapshot>,
) : Serializable

class PrivacyDerivativePayload(
    encodedBytes: ByteArray,
    val report: PrivacyDerivativeReportSnapshot,
) : Serializable {
    private val storedBytes: ByteArray = encodedBytes.copyOf()
    val bytes: ByteArray get() = storedBytes.copyOf()
}

data class PrivacyDerivativeReportSnapshot(
    val sourceId: String?,
    val sourceDimensions: PrivacyImageDimensionsSnapshot,
    val outputDimensions: PrivacyImageDimensionsSnapshot,
    val strippedMetadataCategories: Set<PrivacyMetadataCategory>,
    val appliedActions: List<PrivacyDerivativeAction>,
    val redactions: List<PrivacyAppliedRedactionSnapshot>,
    val failures: List<PrivacyDerivativeFailureSnapshot>,
    val elapsedMillis: Long,
    val metadataVerification: PrivacyMetadataVerificationSnapshot,
) : Serializable

enum class PrivacyDerivativeFailureCode { VALIDATION, LOAD, TRANSFORM, WRITE, VERIFY, UNKNOWN }

data class PrivacyImageDimensionsSnapshot(val width: Int, val height: Int) : Serializable
data class PrivacyAppliedRedactionSnapshot(
    val regionId: String?,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) : Serializable
data class PrivacyMetadataVerificationSnapshot(
    val requested: Set<PrivacyMetadataCategory>,
    val sourcePresent: Set<PrivacyMetadataCategory>,
    val remaining: Set<PrivacyMetadataCategory>,
    val verified: Boolean,
) : Serializable

data class PrivacyDerivativeFailureSnapshot(
    val stage: PrivacyDerivativeFailureStage,
    val code: PrivacyDerivativeFailureCode,
) : Serializable

data class PrivacyDerivativeBatchSnapshot(
    val sourceId: String,
    val payload: PrivacyDerivativePayload?,
    val failure: PrivacyDerivativeFailureSnapshot?,
) : Serializable
```

각 `Serializable` snapshot 구현체는 `private companion object { @JvmField
private val serialVersionUID: Long = 1L }` 또는 동등한 JVM 필드를 실제 classfile에 두며,
중첩 `List`/`Set`은 생성 시 불변 복사본으로 정규화한다. `PrivacyDerivativePayload`는
입력 `ByteArray`를 생성 시 복사하고 getter에서도 새 배열을 반환한다. batch snapshot은
생성자와 Java deserialization(`readObject`/serialization proxy 중 하나) 양쪽에서
payload와 failure 중 정확히 하나인지 검증한다. JSON에는 sealed subtype discriminator나
arbitrary class name을 쓰지 않고 위의 고정 enum/구체 DTO만 사용한다.

`PrivacyDerivativeBatchSnapshot`은 payload와 failure가 정확히 하나만 존재하도록
생성 시 검증한다. `PrivacyDerivativeResult.toPayload()`와
`PrivacyDerivativeBatchResult.toSnapshot()`을 통해 변환하며, failure message는
원본 exception graph나 stack trace를 노출하지 않는 고정된 stage/code 조합이다. report의
source와 batch sourceId는 absolute path 대신 caller가 제공한 opaque id 또는 안전한 basename만
허용하며, 길이와 redaction/metadata collection 크기도 제한한다. `maxPixels`, `maxSide`,
thumbnail width/height에도 고정된 최대값을 적용해 `Long.MAX_VALUE`와 `Int.MAX_VALUE`가
decode guard를 우회하지 못하게 한다. snapshot 변환은 rectangle redaction만
지원하고, `SensitiveRegionGeometry.Polygon` 같은 지원하지 않는 geometry를 조용히 버리지
않고 명시적으로 거부한다.

`PrivacyImageDimensionsSnapshot`, `PrivacyAppliedRedactionSnapshot`,
`PrivacyMetadataVerificationSnapshot`도 wire 전용 concrete DTO로 정의하며 runtime
`typealias`/`AppliedPrivacyRedaction`/검증 객체를 snapshot public API에 노출하지 않는다.
각 wire DTO의 JSON property와 `schemaVersion`은 고정하고, `PrivacyDerivativePayload`는
`ByteArray.contentEquals/contentHashCode` 기반 value equality/hash를 제공한다.

### Jackson 3 기본 codec

Jackson 3의 package/group 변경(`tools.jackson`, `tools.jackson.core:jackson-databind`)
과 Kotlin module 등록을 따른다. 저장소의 immutable external catalog가 제공하는
Jackson 3.2.1 BOM을 사용해 다른 bluetape4k 모듈과 dependency train을 맞춘다. 외부
reference:

- https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.1
- https://github.com/FasterXML/jackson-module-kotlin
- https://github.com/FasterXML/jackson-databind

`images/build.gradle.kts`는 `api(bt4k.bluetape4k.jackson3)`로 codec의
`tools.jackson.databind.ObjectMapper` 공개 타입과 Jackson 3.2.1 runtime 좌표를 함께
노출한다. 별도 Jackson 2 alias나 `kotlinx.serialization` dependency는 codec 경계에
추가하지 않는다. Ktor 모듈은 기존 HTTP serializer를 유지하되 core codec을 직접 호출할
때만 이 전이 의존성을 사용한다. published POM/Gradle Module Metadata와 plain-JVM,
Ktor, Spring Boot consumer smoke가 이 `api` 선택을 고정한다.

`PrivacyDerivativeJackson`은 기본 strict mapper를 lazy singleton으로 한 번 구성하고,
typed reader/writer를 재사용한다. 외부 입력 decode에는 이 고정 mapper만 사용한다. caller가
mapper를 주입하는 확장 경로는 `trustedMapper(...)`로 명시하고, mapper를 mutate하지 않는
strict copy/reader를 만들어 `FAIL_ON_UNKNOWN_PROPERTIES`와
`FAIL_ON_NULL_FOR_PRIMITIVES`를 강제하며 default typing을 비활성화한다. 이 경로는
trusted module code와 trusted payload에만 허용하고, arbitrary mapper로 untrusted JSON을
읽는 public 기본 경로는 제공하지 않는다. payload byte는 JSON base64 표준 표현을
사용하며 raw `ImmutableImage`, `Path`, `Throwable`, writer/client, PEM/private-key
material은 JSON에 나타나지 않는다. 입력 JSON/encoded byte/collection 크기에는 명시적인
bounded limit을 적용하고 초과 시 decode 전에 실패한다.

Jackson 3.2.1에서 caller mapper는 `mapper.rebuild().enable(...).disable(...).build()`로
복제한다. 원본 mapper의 설정이나 module 목록은 mutate하지 않으며, rebuild 결과에는
codec가 허용한 Kotlin module과 고정 subtype만 사용한다. arbitrary default typing,
polymorphic class-name resolution, custom base64 variant는 trusted codec 경계에서도
허용하지 않는다.

`PrivacyDerivativeJsonLimits`는 raw JSON 최대 크기, decoded payload 최대 크기, redaction
최대 개수, report action/failure/metadata entry 수, source/code/field 문자열 길이, nesting
depth, maxPixels/maxSide/thumbnail dimension을 함께 정의한다. 기본값은 64 MiB decoded
payload, 96 MiB UTF-8 JSON, 1,024개 redaction, report action/failure 각 256개,
metadata entry 256개, 4 KiB source/code, depth 32이며 `maxPixels` 100,000,000,
`maxSide` 65,536, thumbnail width/height 16,384를 기본 상한으로 둔다. caller가 더 작은
한도를 지정할 수 있다. `toOptions()`는 이 상한을 초과하는 snapshot을 decode 전에
거부한다. String API는 편의 계층으로 유지하고, large
payload에는 UTF-8 `ByteArray` API를 우선 사용한다. Java serialization은 trusted-only
compatibility test 경계이며 untrusted bytes를 읽는 public API는 제공하지 않는다.

decode는 `InputStream` bounded wrapper와 Jackson stream/read constraints를 함께 사용해
JSON 문서·문자열·배열·nesting 상한을 token/materialization 전에 검사한다. base64 payload는
custom deserializer가 decoded length를 누적 검사하고 상한을 넘는 즉시 중단한다. 단순히
`toOptions()` 이후에 검사하는 구현은 완료로 인정하지 않는다.

기본 codec의 decode는 `PrivacyDerivativeJackson.decode(json|bytes, limits)`만 허용하며,
JSON 문서 길이와 base64 decoded byte 길이를 allocation 전에 검사한다. oversized,
malformed JSON, unknown field, null primitive는 동일한 공개 `PrivacyDerivativeCodecException`
계열의 안정된 `reason` 코드로 매핑하고 내부 Jackson 경로·클래스명·원본 path·stack trace는
호출자 메시지에 포함하지 않는다. `CancellationException`은 이 매핑을 거치지 않고 그대로
재전파한다. `trustedMapper(mapper)`는 trusted module 경계에서만 사용할 수 있고, 해당
mapper를 복제한 strict reader/writer만 사용한다.

## 데이터 흐름

```text
ImmutableImage + PrivacyDerivativeOptions
        │ (runtime transform/encode/verify)
        ▼
PrivacyDerivativeResult (runtime-only)
        │ toPayload()
        ▼
PrivacyDerivativePayload (bytes + report)
        │ PrivacyDerivativeJackson.encode/decode
        ▼
JSON snapshot boundary
```

batch flow는 기존 cancellation, dispatcher, skip-failure semantics를 유지한다. snapshot
변환은 flow의 처리와 분리하여 IO/transform 경계를 변경하지 않는다.

## 호환성과 마이그레이션

- 기존 runtime constructor signature와 `PrivacyDerivativeResult.image`/`bytes` 접근은
  유지한다.
- `Serializable` marker에 의존한 Java serialization은 더 이상 runtime 타입의 지원
  계약이 아니다. 0.5.0에서 기존 runtime stream은 지원하지 않으며 현재 객체를
  `ObjectOutputStream`에 쓰면 `NotSerializableException`으로 즉시 실패한다. caller는
  `toPayload()`, `toSnapshot()`, `PrivacyDerivativeJackson.encode/decode`로 마이그레이션한다.
  module README에는 plain JVM, Ktor route, Spring bean 세 가지 최소 예제를 제공하고,
  Ktor의 기존 kotlinx JSON 응답은 snapshot을 DTO로 변환한 뒤 기존 응답 serializer를
  사용할 수 있음을 명시한다. 이 breaking behavior와 rollback 절차는 0.5.0 CHANGELOG와
  EN/KO README에 함께 기록한다.
- built-in JPEG/PNG 옵션과 rectangle redaction은 snapshot 왕복 후 동일 policy로
  복원된다. custom writer 또는 unsupported geometry는 `IllegalArgumentException` 계열의
  명시적 오류로 snapshot 변환을 거부한다.
- `LocalImageStorage`, `S3ImageStorage`, `S3PreSignedUrlSigner`, `CloudFrontUrlSigner`,
  `CdnProperties`는 재생성 가능한
  configuration/bean wiring 경계이며 객체 직렬화 대상이 아니다. Spring auto-configuration
  및 metrics wrapper의 runtime 동작은 바꾸지 않는다. 기존 configuration serialization
  caller는 환경 설정 재바인딩/bean 재생성으로 마이그레이션한다.
- Spring auto-configuration은 Local root/prefix provisioning, S3 `S3Operations` 및
  optional transfer collaborator, signer bucket/keyPrefix를 configuration source에서
  재생성한다. `ApplicationContextRunner`로 Local/S3/signer 정상 재기동, missing
  collaborator의 startup fail-fast, metrics wrapper의 비직렬화 경계를 검증한다. Local
  storage는 required root 설정이 없으면 startup failure, S3 signer는 required
  `S3Operations`가 없으면 startup failure, 선택적 transfer collaborator가 없을 때는
  기본 S3 경로만 제공하는 상태를 고정한다. `@ConditionalOnBean`으로 조용히 bean을
  생략하는 현재 동작은 이 경계의 성공 조건으로 인정하지 않는다.
- release-facing manual은 0.4.0 stable tag에 고정되어 있으므로 이번 0.5.0 개발 변경의
  직접 편집 대상에서 제외하고, module README와 KDoc에 migration을 반영한다.

## 검증 계획과 완료 기준

1. snapshot option/report/payload/batch의 Java serialization round-trip 및 Jackson 3.2.1
   JSON round-trip을 검증한다. 기본·Smart crop·rectangle redaction을 모두 포함한다.
2. payload byte와 collection/nested DTO mutation이 원본/round-trip 객체의 관찰 가능한
   값과 hash를 깨뜨리지 않는지 검증한다. constructor와 getter 모두 defensive copy를
   수행하며 모든 snapshot class에 실제 `serialVersionUID` field를 둔다. source-level
   reflection test로 UID와 nested collection copy를 확인한다.
3. snapshot으로 복원한 built-in options로 privacy pipeline을 재실행하고 output
   dimensions/metadata verification을 비교한다.
4. runtime result/storage/signer/CloudFront signer를 Java serialization하려는 시도가
   명시적으로 실패하고, `ImmutableImage`, `Path`, `Throwable`, AWS/Scrimage collaborator,
   PEM/private-key material이 snapshot JSON/Java graph에 포함되지 않는지 검증한다.
5. runtime marker 제거와 sealed batch subtype의 source/binary API matrix를 기록하고
   `japicmp` 또는 동등한 classfile diff를 실행한다. 기존 `ObjectOutputStream` 호출은
   명시적 fail-fast migration 계약으로 검증한다.
6. `CancellationException` 재전파와 기존 privacy/storage targeted tests를 유지한다.
7. Jackson mapper 주입 경로, unknown field 거부, arbitrary polymorphic type 미활성화,
   JSON/byte/collection bounded limit을 검증한다.
8. default/caller mapper의 concurrent reuse와 per-call mapper construction 부재를
   검증한다. caller mapper는 원본 설정이 변하지 않아야 한다.
9. `images`와 `images-spring-boot` targeted Gradle tests, detekt/compile, full relevant
   module build를 실행하고 published POM/BOM/runtimeClasspath/ABI에서 Jackson 3.2.1
   dependency가 의도한 범위로만 노출되는지 확인한다. public codec이
   `tools.jackson.databind.ObjectMapper`를 받으므로 `images`의 `api` 노출과
   `jackson-module-kotlin` runtime 좌표를 exact POM/Gradle Module Metadata, plain-JVM
   consumer compile/runtime smoke, Spring Boot consumer classpath로 확인한다.
10. 기존 benchmark 모듈에 payload JSON String/bytes, cold/warm mapper, concurrent
   encode/decode baseline을 추가한다. 이번 PR에서 baseline을 실행할 수 없을 때는
   해당 benchmark fixture와 별도 후속 issue 번호를 함께 기록해야 하며, 미결 양자택일
   상태로 완료 처리하지 않는다.
11. failure stage/code, sourceId, unknown-field/null/missing-bean, malformed JSON의
   stable diagnostic과 redaction/path/secret 비노출을 로그·메트릭 correlation fixture로
   검증한다. `CHANGELOG.md`, EN/KO module README migration 및 rollback note를 함께
   갱신한다.
12. plain JVM consumer compile/runtime smoke, Ktor route response conversion, Spring
   `ApplicationContextRunner` bean reconstruction/missing-collaborator fail-fast를 각
   dependency scope와 함께 검증한다. 기존 kotlinx JSON caller가 Jackson 3 codec을
   선택적으로 도입하는 migration path와 기본 Jackson 3 path 모두 문서·테스트에 남긴다.

완료 조건은 P0/P1 finding 0, runtime graph의 false `Serializable` marker 0, snapshot
round-trip green, public API/KDoc/README locale parity, exact-head CI green이다.
