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

`CloudFrontUrlSigner`, unrelated moderation/metadata/upload DTO, 저장소 동작 자체,
module/BOM/CI topology 변경은 이번 이슈의 named scope 밖이다. 단, 새 Jackson 3
runtime dependency는 public snapshot codec을 제공하기 위해 명시적으로 추가한다.

## 설계 선택

### 선택지 A — runtime과 snapshot DTO 분리 (채택)

runtime 타입에서 `java.io.Serializable`을 제거하고 다음 additive DTO/codec을 제공한다.

- `PrivacyDerivativeFormatId`: 현재 안정적으로 복원 가능한 `JPEG`, `PNG` 식별자.
- `PrivacyDerivativeOptionsSnapshot`: primitive policy, format id, redactions를
  보존한다. `toOptions()`는 built-in JPEG/PNG만 복원하며 custom writer는 명시적으로
  거부한다.
- `PrivacyDerivativePayload`: encoded bytes와 report만 보유한다. 입력 byte와 반환 byte는
  defensive copy를 사용한다.
- `PrivacyDerivativeBatchSnapshot`: source를 문자열로 보존하고 성공 payload 또는
  `PrivacyDerivativeFailure(stage, message)` 중 하나만 보존한다. `Path`와
  `Throwable`은 포함하지 않는다.
- `PrivacyDerivativeJackson`: `tools.jackson` 기반 기본 mapper와 String encode/decode
  helper를 제공한다. caller는 필요할 때 자신의 Jackson 3 `ObjectMapper`를 주입할 수
  있으며 public API는 JSON 경계를 명시한다.

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

`PrivacyDerivativeFormat`의 `writer`는 항상 생성 시 non-null이어야 하며 deserialization
후 null이 되는 경로를 더 이상 제공하지 않는다. custom writer는 runtime pipeline에서
계속 사용할 수 있지만 `PrivacyDerivativeOptionsSnapshot`으로 변환할 수 없다.

### Snapshot 타입

snapshot은 모두 명시적인 `serialVersionUID`를 가지며, Java serialization과 Jackson 3
JSON 모두에서 동일한 의미를 보존한다.

```kotlin
enum class PrivacyDerivativeFormatId { JPEG, PNG }

data class PrivacyDerivativeOptionsSnapshot(
    val stripMetadata: Boolean,
    val removeGps: Boolean,
    val normalizeOrientation: Boolean,
    val maxPixels: Long,
    val maxSide: Int?,
    val thumbnailSize: ThumbnailSize?,
    val thumbnailCrop: ThumbnailCrop,
    val outputFormat: PrivacyDerivativeFormatId,
    val redactions: List<PrivacyRedaction>,
) : Serializable

class PrivacyDerivativePayload(
    encodedBytes: ByteArray,
    val report: PrivacyDerivativeReport,
) : Serializable {
    val bytes: ByteArray get() = encodedBytes.copyOf()
}

data class PrivacyDerivativeBatchSnapshot(
    val source: String,
    val payload: PrivacyDerivativePayload?,
    val failure: PrivacyDerivativeFailure?,
) : Serializable
```

`PrivacyDerivativeBatchSnapshot`은 payload와 failure가 정확히 하나만 존재하도록
생성 시 검증한다. `PrivacyDerivativeResult.toPayload()`와
`PrivacyDerivativeBatchResult.toSnapshot()`을 통해 변환하며, failure message는
원본 exception graph나 stack trace를 노출하지 않는 제한된 진단 문자열이다.

### Jackson 3 기본 codec

Jackson 3의 package/group 변경(`tools.jackson`, `tools.jackson.core:jackson-databind`)
과 Kotlin module 등록을 따른다. 3.1 LTS line의 고정 버전을 사용하고 3.2 non-LTS line은
이번 변경의 dependency target으로 사용하지 않는다. 외부 reference:

- https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.1
- https://github.com/FasterXML/jackson-module-kotlin
- https://github.com/FasterXML/jackson-databind

`PrivacyDerivativeJackson`은 기본 mapper를 한 번 구성하고, encode/decode 시 caller가
제공한 mapper를 우선한다. unknown property, polymorphic default typing, arbitrary class
instantiation은 켜지 않는다. payload byte는 JSON base64 표준 표현을 사용하며 raw
`ImmutableImage`, `Path`, `Throwable`, writer/client는 JSON에 나타나지 않는다.

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
  계약이 아니다. caller는 `toPayload()`, `toSnapshot()`, `PrivacyDerivativeJackson`을
  사용해야 한다.
- built-in JPEG/PNG 옵션은 snapshot 왕복 후 동일 policy로 복원된다. custom writer는
  `IllegalArgumentException` 또는 `NotSerializableException` 계열의 명시적 오류로
  snapshot 변환을 거부한다.
- `LocalImageStorage`, `S3ImageStorage`, `S3PreSignedUrlSigner`는 재생성 가능한
  configuration/bean wiring 경계이며 객체 직렬화 대상이 아니다. Spring auto-configuration
  및 metrics wrapper의 runtime 동작은 바꾸지 않는다.
- release-facing manual은 0.4.0 stable tag에 고정되어 있으므로 이번 0.5.0 개발 변경의
  직접 편집 대상에서 제외하고, module README와 KDoc에 migration을 반영한다.

## 검증 계획과 완료 기준

1. snapshot option/report/payload/batch의 Java serialization round-trip 및 Jackson 3
   JSON round-trip을 검증한다.
2. payload byte와 collection mutation이 원본/round-trip 객체의 관찰 가능한 값과 hash를
   깨뜨리지 않는지 검증한다.
3. snapshot으로 복원한 built-in options로 privacy pipeline을 재실행하고 output
   dimensions/metadata verification을 비교한다.
4. runtime result/storage/signer를 Java serialization하려는 시도가 명시적으로 실패하고,
   `ImmutableImage`, `Path`, `Throwable`, AWS/Scrimage collaborator가 snapshot JSON/Java
   graph에 포함되지 않는지 검증한다.
5. `CancellationException` 재전파와 기존 privacy/storage targeted tests를 유지한다.
6. Jackson mapper 주입 경로, unknown field 거부, arbitrary polymorphic type 미활성화를
   검증한다.
7. `images`와 `images-spring-boot` targeted Gradle tests, detekt/compile, full relevant
   module build를 실행한다.

완료 조건은 P0/P1 finding 0, runtime graph의 false `Serializable` marker 0, snapshot
round-trip green, public API/KDoc/README locale parity, exact-head CI green이다.
