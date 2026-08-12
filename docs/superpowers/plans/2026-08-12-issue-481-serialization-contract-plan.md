# #481 공개 직렬화 계약 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: `$executing-plans`를 사용해 이 계획을 단계별로 실행하고, 각 단계의 검증 증거를 남긴다.

## 목표

`bluetape4k-images`의 privacy runtime graph와 Jackson 3.2.1 snapshot graph를 분리하고,
기존 `Serializable` runtime 타입은 0.5.0에서 fail-fast migration 경계로 전환한다. 공개
codec public signature는 Jackson 타입을 노출하지 않고 최소 Jackson 3 좌표를
implementation으로만 사용하며, untrusted JSON은 고정 strict mapper와 할당 전 bounded
decode로만 읽는다.
`images-spring-boot`의 storage/CDN
runtime collaborator와 secret-bearing configuration은 직렬화 graph에서 제거하고 Spring
bean 재생성 경계를 검증한다.

## 확정 설계와 의존성

- `images/build.gradle.kts`에 중앙 immutable catalog의 Jackson 3.2.1 BOM과
  `implementation("tools.jackson.core:jackson-databind")`,
  `implementation("tools.jackson.module:jackson-module-kotlin")`를 추가한다. helper
  runtime graph인 `bluetape4k-jackson3`는 사용하지 않고 공개 API에도 추가하지 않으며 Jackson 2 alias나
  새 dependency version을 만들지 않는다. 변경 후 `apiElements`, `runtimeClasspath`,
  POM/GMM의 Jackson 좌표와 public `javap` surface를 이 PR에서 검증한다. 이전 stable artifact를
  사용한 old-compiled consumer 비교는 릴리즈 전 별도 호환성 증거 범위로 남긴다.
- Jackson 3 codec은 `images/src/main/kotlin/io/bluetape4k/images/privacy/` 아래에 둔다.
  Ktor content negotiation에는 자동 등록하지 않는다. Ktor/Spring/plain-JVM caller가
  `PrivacyDerivativeJackson`을 명시적으로 호출한다.
- 기본 mapper는 lazy singleton이며 Kotlin module, unknown/null strict features,
  disabled default typing, Jackson stream/read constraints를 고정한다.
- 외부 decode는 codec 내부의 고정 mapper만 사용한다. 원본 mapper 주입이나 caller identity
  전역 캐시는 제공하지 않으며, custom polymorphic class-name/default typing/base64 설정과
  임의 module/mixin/deserializer를 허용하지 않는다.
- snapshot은 runtime 모델을 직접 참조하지 않는 concrete wire DTO를 사용한다. 각
  `Serializable` concrete class에 실제 `serialVersionUID` JVM field를 두고 collection과
  byte array를 방어적으로 복사한다. batch는 payload/failure 정확히 하나 불변식을 생성자와
  Java deserialization 양쪽에서 검증한다.

## 구현 단계

### 1. 테스트 우선 계약 고정

먼저 `images/src/test/kotlin/io/bluetape4k/images/privacy/PrivacyDerivativeSerializationTest.kt`
를 추가한다.

- JPEG/PNG, FIT/SMART crop, rectangle redaction을 snapshot으로 만들고 Java
  `ObjectOutputStream`/`ObjectInputStream` round-trip을 검증한다.
- Jackson 3 String/UTF-8 byte round-trip, canonical property, `ByteArray`
  `contentEquals/contentHashCode`, constructor/getter defensive copy, nested collection
  mutation, concrete `serialVersionUID` reflection을 검증한다.
- `encodeTo(OutputStream)`와 bounded `decode(InputStream)`의 one-shot logical EOF,
  truncated final input, trailing document/garbage, partial write/`IOException`, stream
  close/flush ownership을 검증하고 String/ByteArray 편의 API가 이미 materialized된
  입력이라는 문서 계약을 고정한다.
- batch payload-only/failure-only 성공과 두 필드 모두 null/모두 non-null의 constructor 및
  malformed Java stream fail-fast를 검증한다.
- unknown field, null primitive, default typing payload, malformed JSON, oversized JSON,
  oversized base64, collection/depth limit, source/path/stack trace leakage를 검증한다.
- `CancellationException`은 codec failure mapping을 우회하고 그대로 재전파한다.
- 고정 codec 인스턴스를 barrier로 동시에 16개 worker가 100회씩 공유하는 테스트를 추가한다.
  worker 수·반복·timeout·결과 equality를 고정하고, per-call mapper rebuild와 전역 caller
  mapper cache가 없음을 계측한다.

실행 기준:

```bash
./gradlew :bluetape4k-images:test --tests '*PrivacyDerivativeSerializationTest'
```

### 2. Snapshot/wire DTO와 runtime 변환

`PrivacyDerivativePipeline.kt`를 작은 타입 단위로 정리한다.

- `PrivacyDerivativeFormat`, `PrivacyDerivativeOptions`, `PrivacyDerivativeResult`,
  `PrivacyDerivativeBatchResult`의 `Serializable` marker와 stale serialVersionUID를
  제거하되 runtime constructor와 `image`/`bytes` access는 유지한다.
- `PrivacyDerivativeFormatId`, `PrivacyThumbnailSizeSnapshot`,
  `PrivacyThumbnailCropId`, `PrivacyRedactionSnapshot`,
  `PrivacyImageDimensionsSnapshot`, `PrivacyAppliedRedactionSnapshot`,
  `PrivacyMetadataVerificationSnapshot`, `PrivacyDerivativeReportSnapshot`,
  `PrivacyDerivativeFailureSnapshot`, `PrivacyDerivativePayload`,
  `PrivacyDerivativeBatchSnapshot`을 추가한다.
- snapshot DTO는 runtime `typealias`, `ImmutableImage`, `Path`, `Throwable`, writer,
  client, PEM/private-key를 보유하지 않는다. JSON property와 schema version을 고정한다.
- `SensitiveCoordinateSpace`, `PrivacyRedactionMode`, `PrivacyMetadataCategory`,
  `PrivacyDerivativeAction`, `PrivacyDerivativeFailureStage`를 public wire DTO에서 직접
  참조하지 않고 `PrivacyWire*Id` 전용 enum으로 매핑한다. enum 이름·추가 값·unknown 값의
  JSON compatibility를 golden fixture로 고정하고 새 enum 값은 decoder가 명시적으로
  `INVALID_VALUE`로 거부한다.
- `PrivacyDerivativeOptions.toSnapshot()`/`PrivacyDerivativeOptionsSnapshot.toOptions()`와
  `PrivacyDerivativeResult.toPayload()`/`PrivacyDerivativeBatchResult.toSnapshot()`을
  제공한다. built-in JPEG/PNG, FIT/SMART crop, rectangle만 복원하고 custom writer와
  unsupported geometry는 안정된 validation exception으로 거부한다.
- `PrivacyDerivativePayload`는 bytes를 생성·getter 모두 복사하고 content-based
  equality/hash를 구현한다. 모든 List/Set은 Java와 Kotlin caller 모두에서 mutation이
  불가능한 `List.copyOf`/`Set.copyOf` 수준의 canonical copy로 정규화하고, JSON 배열 순서를
  deterministic하게 고정한다.
- 모든 snapshot concrete class에 serialization proxy 또는 `readObject` 재검증을 적용해
  생성자 불변식·NaN/음수 차원·collection/array 상한·sourceId 규칙을 Java deserialization
  경계에서도 유지한다. `ObjectInputFilter` allow-list/depth/reference/array 제한을 함께
  제공하며 untrusted Java bytes는 공개 API로 읽지 않는다. 구형 runtime graph stream은
  `NotSerializableException`으로 폐기한다.
- `toPayload(sourceId: String?)`/`toSnapshot(sourceId: String)`은 runtime `Path`를 복사하지
  않고 opaque sourceId만 받는다. absolute path, `/`·`\\`, control character, 빈 값, 4 KiB
  초과를 양쪽 변환 경계에서 거부한다.

### 3. Jackson 3.2.1 codec와 bounded decode

`PrivacyDerivativeJackson.kt`, `PrivacyDerivativeJsonLimits.kt`,
`PrivacyDerivativeCodecException.kt`를 추가한다.

- fixed mapper를 한 번만 만들고 typed reader/writer를 재사용한다.
- `encodeOptions/decodeOptions`, `encodeReport/decodeReport`,
  `encodePayload/decodePayload`, `encodeBatch/decodeBatch`와 각 String/UTF-8 byte,
  `encodeTo(..., OutputStream)`/`decode(..., InputStream)` overload의 반환형·KDoc을
  고정한다. 모든 문서는 `schemaVersion=1`, `kind`, `value`의 typed envelope를 사용하며
  unknown version, kind/decoder mismatch, missing envelope를 조기 거부한다. String/ByteArray는
  편의 API이고 large payload는 streaming API를 사용한다.
- `InputStream` bounded wrapper와 `StreamReadConstraints`로 JSON document length, nesting
  depth, token/string 상한을 읽는 동안 제한하고, typed snapshot을 caller에게 반환하기 전에
  caller limit을 적용한다. caller limit은 기본 hard cap보다 클 수 없다. InputStream 경로는 전체 JSON을
  먼저 String/ByteArray로 복제하지 않는다. one-shot stream API는 caller stream을 close하지
  않고 encode는 implicit flush를 하지 않으며, decode는 한 문서 뒤 trailing non-whitespace를
  `TRAILING_DATA`로 거부한다. truncated input, partial write/`IOException`, limit 초과와
  stream ownership을 명시 테스트한다.
- `PrivacyDerivativeJsonLimits` 기본값은 snapshot에서 확정한 64 MiB decoded payload,
  96 MiB UTF-8 JSON, redaction 1,024, report action/failure 각 256, metadata 256,
  source/code 4 KiB, depth 32, maxPixels 100,000,000, maxSide 65,536,
  thumbnail dimension 16,384이다. caller는 더 작은 limit만 사용할 수 있다.
- 외부 decode는 codec 내부의 fixed mapper만 사용한다. per-request rebuild와 caller mapper
  identity 전역 캐시는 금지하며 default typing, arbitrary subtype, 임의 module/mixin/custom
  deserializer, custom base64 variant를 끈다. malicious `@JsonTypeInfo`와 unknown-field/null
  primitive negative test를 포함한다.
- malformed/oversized/unknown/null input은 내부 class/path/stack trace를 노출하지 않는
  `PrivacyDerivativeCodecException` reason으로 매핑하고 `CancellationException`은
  재전파한다. reason enum은 `MALFORMED_JSON`, `UNSUPPORTED_SCHEMA_VERSION`,
  `TYPE_MISMATCH`, `UNKNOWN_FIELD`, `NULL_VALUE`, `LIMIT_EXCEEDED`, `INVALID_VALUE`,
  `TRAILING_DATA`, `IO_FAILURE`로 고정하고 입력별 매핑표와 golden JSON을 테스트한다.

### 4. Spring runtime graph 정리와 재생성 계약

`images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/`과 해당 테스트를 갱신한다.

- `images-spring-boot/build.gradle.kts`에는 중앙 version catalog로 고정한 Jackson
  annotation 좌표 `compileOnly("com.fasterxml.jackson.core:jackson-annotations:${bt4k.versions.jackson.annotations}")`만 추가해 annotation class를
  제공하고, databind runtime을 Spring 모듈의 public/runtime dependency로 만들지 않는다.

- `LocalImageStorage`, `S3ImageStorage`, `S3PreSignedUrlSigner`, `CloudFrontUrlSigner`,
  `CdnProperties`/`CloudFront`의 `Serializable` marker와 runtime UID를 제거한다.
- `ImagesStorageAutoConfiguration`/`ImagesCdnAutoConfiguration`의 existing constructor,
  root provisioning, `S3Operations`, optional transfer collaborator, signer bucket/
  keyPrefix wiring은 유지한다.
- `ApplicationContextRunner`로 Local 정상 생성, required root 누락 startup failure,
  S3 required operations 누락 startup failure, optional transfer absent 기본 경로,
  signer 재생성, CloudFront private key 비직렬화, metrics wrapper 경계를 검증한다.
- provider matrix를 Local disabled/enabled, S3 disabled/enabled with/without required
  `S3Operations`, optional transfer present/absent, CloudFront classpath present/absent,
  user-provided signer로 고정한다. enabled provider의 required collaborator/class 누락은
  startup failure이고, disabled provider와 사용자 signer는 no-op/override 경로다.
- `CdnProperties.CloudFront.privateKeyPem`/`privateKeyPath`는 generic Jackson wire에서
  Jackson 3 호환 `@get:JsonIgnore`와 compileOnly `com.fasterxml.jackson.core:jackson-annotations`
  좌표로 제외하고 `toString()`, startup failure, signer exception, Actuator/metrics 진단에서 값·경로·
  원본 cause가 사라지는지 검증한다. 기존 Local serialization success test는
  `NotSerializableException` migration test로 변경하고, missing collaborator의 “bean 생략”과
  “startup failure” 상태를 구현 계약과 정확히 맞춘다.

### 5. 문서·공개 API 증거

- `images/README.md`, `images/README.ko.md`, `images-spring-boot/README.md`,
  `images-spring-boot/README.ko.md`, 해당 `CHANGELOG.md`에 0.5.0 migration을 추가한다.
  plain JVM/Ktor route/Spring bean 세 가지 호출 예제, `NotSerializableException` 전환,
  rollback 경로, Ktor 기존 kotlinx serializer 유지와 explicit codec 호출을 설명한다.
- 기존 README의 runtime bytes 예제는 snapshot codec 예제와 locale parity를 갖춘다.
- generated POM/GMM의 Jackson 3 좌표가 runtime variant에만 나타나고, public
  `PrivacyDerivativeJackson` signature에 Jackson mapper 타입이 없음을 `javap`와 dependency
  report로 확인한다. README/CHANGELOG에는 runtime graph 제거, snapshot migration,
  stream ownership과 Jackson 3 explicit call 경계를 기록한다.
- plain-JVM/Ktor/Spring consumer fixture, 0.4.x old-compiled ABI/serialized-stream 비교,
  1/4/16 concurrency benchmark는 이 코드 변경의 수용 기준이 아니라 0.5.0 릴리즈 전
  호환성·성능 증거로 별도 추적한다. 해당 증거 없이는 성능/ABI 보장을 주장하지 않는다.

## 검증 순서

각 단계 후 변경 worktree에서 다음을 순차 실행한다.

```bash
./gradlew :bluetape4k-images:test --tests '*PrivacyDerivativeSerializationTest'
./gradlew :bluetape4k-images-spring-boot:test --tests '*Serialization*' --tests '*AutoConfiguration*'
./gradlew :bluetape4k-images:compileKotlin :bluetape4k-images-spring-boot:compileKotlin
./gradlew :bluetape4k-images:test :bluetape4k-images-spring-boot:test --no-daemon
./gradlew detekt
./gradlew :bluetape4k-images:generatePomFileForMavenJavaPublication :bluetape4k-images:generateMetadataFileForMavenJavaPublication
./gradlew :bluetape4k-images:dependencies --configuration apiElements
./gradlew :bluetape4k-images:dependencies --configuration runtimeClasspath
git diff --check
```

`apiElements`/`runtimeClasspath` 및 생성된 POM/GMM의 Jackson 좌표가 계획한 implementation
범위와 다르거나 public API에 Jackson 타입이 나타나면 dependency scope를 임의로 바꾸지
않고 계획을 재개정한다. benchmark/old-compiled fixture가 없는 상태에서는 해당 보장을
완료했다고 보고하지 않는다.

그 후 6관점 코드 리뷰, lesson 문서/commit, PR 생성, PR 후 리뷰, exact-head job 확인을
순서대로 수행한다. CI가 green이어도 merge는 별도 게이트로 유지하며, 사용자의 fresh
merge approval 전에는 merge하지 않는다.

## 위험과 중단 조건

- Jackson 3.2.1 실제 API가 계획과 다르면 먼저 `javap`/minimal compile fixture로 계약을
  수정하고 구현을 중단한다.
- `implementation` Jackson 좌표로 public POM/GMM이 예상보다 크게 변하거나 Jackson
  타입이 public ABI에 나타나면 dependency scope를 임의로 바꾸지 말고 consumer smoke와
  함께 계획을 갱신한다.
- Java deserialization invariant를 입증하지 못하면 batch snapshot을 공개하지 않는다.
- bounded decode가 materialization 이후에만 동작하면 security gate를 실패로 기록하고
  구현을 완료 처리하지 않는다.
- existing Spring bean behavior가 “conditional omission”인지 “fail-fast”인지 코드와
  테스트가 일치하지 않으면 해당 계약을 먼저 확정한다.
