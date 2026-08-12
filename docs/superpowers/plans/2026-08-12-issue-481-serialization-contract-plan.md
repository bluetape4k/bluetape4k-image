# #481 공개 직렬화 계약 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: `$executing-plans`를 사용해 이 계획을 단계별로 실행하고, 각 단계의 검증 증거를 남긴다.

## 목표

`bluetape4k-images`의 privacy runtime graph와 Jackson 3.2.1 snapshot graph를 분리하고,
기존 `Serializable` runtime 타입은 0.5.0에서 fail-fast migration 경계로 전환한다. 공개
codec은 `tools.jackson.databind.ObjectMapper`를 `api`로 노출하며, untrusted JSON은 고정
strict mapper와 할당 전 bounded decode로만 읽는다. `images-spring-boot`의 storage/CDN
runtime collaborator와 secret-bearing configuration은 직렬화 graph에서 제거하고 Spring
bean 재생성 경계를 검증한다.

## 확정 설계와 의존성

- `images/build.gradle.kts`에 `api(bt4k.bluetape4k.jackson3)`를 추가한다. 중앙 immutable
  catalog의 Jackson 3.2.1 dependency train을 사용하고 Jackson 2 alias나 새 dependency
  version을 만들지 않는다.
- Jackson 3 codec은 `images/src/main/kotlin/io/bluetape4k/images/privacy/` 아래에 둔다.
  Ktor content negotiation에는 자동 등록하지 않는다. Ktor/Spring/plain-JVM caller가
  `PrivacyDerivativeJackson`을 명시적으로 호출한다.
- 기본 mapper는 lazy singleton이며 Kotlin module, unknown/null strict features,
  disabled default typing, Jackson stream/read constraints를 고정한다.
- trusted mapper는 `mapper.rebuild().enable(...).disable(...).build()`로 복제한다. 원본
  mapper를 변경하지 않고 custom polymorphic class-name/default typing/base64 설정을
  허용하지 않는다.
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
- batch payload-only/failure-only 성공과 두 필드 모두 null/모두 non-null의 constructor 및
  malformed Java stream fail-fast를 검증한다.
- unknown field, null primitive, default typing payload, malformed JSON, oversized JSON,
  oversized base64, collection/depth limit, source/path/stack trace leakage를 검증한다.
- `CancellationException`은 codec failure mapping을 우회하고 그대로 재전파한다.

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
- `PrivacyDerivativeOptions.toSnapshot()`/`PrivacyDerivativeOptionsSnapshot.toOptions()`와
  `PrivacyDerivativeResult.toPayload()`/`PrivacyDerivativeBatchResult.toSnapshot()`을
  제공한다. built-in JPEG/PNG, FIT/SMART crop, rectangle만 복원하고 custom writer와
  unsupported geometry는 안정된 validation exception으로 거부한다.
- `PrivacyDerivativePayload`는 bytes를 생성·getter 모두 복사하고 content-based
  equality/hash를 구현한다. 모든 List/Set은 `toList()`/`toSet()` 또는 동등한 immutable
  canonical copy로 정규화한다.
- batch invariant는 `readObject` 또는 serialization proxy로 재검증하고, Java stream의
  구형 runtime graph는 `NotSerializableException`으로 폐기한다.

### 3. Jackson 3.2.1 codec와 bounded decode

`PrivacyDerivativeJackson.kt`, `PrivacyDerivativeJsonLimits.kt`,
`PrivacyDerivativeCodecException.kt`를 추가한다.

- fixed mapper를 한 번만 만들고 typed reader/writer를 재사용한다.
- `encode(payload)`, `encodeBytes(payload)`, `decode(json, limits)`,
  `decodeBytes(bytes, limits)`, `trustedMapper(mapper)`의 public KDoc과 안정된 reason
  code를 제공한다.
- `InputStream` bounded wrapper, `StreamReadConstraints`, token preflight/custom
  deserializer로 JSON document length, string/array/collection count, nesting depth,
  base64 decoded byte length를 materialization 전에 제한한다.
- `PrivacyDerivativeJsonLimits` 기본값은 snapshot에서 확정한 64 MiB decoded payload,
  96 MiB UTF-8 JSON, redaction 1,024, report action/failure 각 256, metadata 256,
  source/code 4 KiB, depth 32, maxPixels 100,000,000, maxSide 65,536,
  thumbnail dimension 16,384이다. caller는 더 작은 limit만 사용할 수 있다.
- 외부 decode는 fixed mapper만 사용한다. trusted mapper는 strict rebuild copy만 사용하고
  default typing, arbitrary subtype, custom base64 variant를 끈다.
- malformed/oversized/unknown/null input은 내부 class/path/stack trace를 노출하지 않는
  `PrivacyDerivativeCodecException` reason으로 매핑하고 `CancellationException`은
  재전파한다.

### 4. Spring runtime graph 정리와 재생성 계약

`images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/`과 해당 테스트를 갱신한다.

- `LocalImageStorage`, `S3ImageStorage`, `S3PreSignedUrlSigner`, `CloudFrontUrlSigner`,
  `CdnProperties`/`CloudFront`의 `Serializable` marker와 runtime UID를 제거한다.
- `ImagesStorageAutoConfiguration`/`ImagesCdnAutoConfiguration`의 existing constructor,
  root provisioning, `S3Operations`, optional transfer collaborator, signer bucket/
  keyPrefix wiring은 유지한다.
- `ApplicationContextRunner`로 Local 정상 생성, required root 누락 startup failure,
  S3 required operations 누락 startup failure, optional transfer absent 기본 경로,
  signer 재생성, CloudFront private key 비직렬화, metrics wrapper 경계를 검증한다.
- 기존 Local serialization success test는 `NotSerializableException` migration test로
  변경하고, missing collaborator의 “bean 생략”과 “startup failure” 상태를 구현 계약과
  정확히 맞춘다.

### 5. 문서·ABI·benchmark 증거

- `images/README.md`, `images/README.ko.md`, `images-spring-boot/README.md`,
  `images-spring-boot/README.ko.md`, 해당 `CHANGELOG.md`에 0.5.0 migration을 추가한다.
  plain JVM/Ktor route/Spring bean 세 가지 호출 예제, `NotSerializableException` 전환,
  rollback 경로, Ktor 기존 kotlinx serializer 유지와 explicit codec 호출을 설명한다.
- 기존 README의 runtime bytes 예제는 snapshot codec 예제와 locale parity를 갖춘다.
- `japicmp` 또는 classfile 동등 diff로 pre-change artifact와 source/binary ABI matrix를
  기록한다. Jackson 3.2.1 dependency가 published POM/GMM/BOM/runtimeClasspath에
  의도한 `api` 범위로만 나타나는지 확인한다.
- benchmark 모듈에 JSON String/bytes, cold/warm mapper, concurrent encode/decode
  baseline을 추가한다. 실행이 환경상 불가능하면 fixture와 별도 후속 issue 번호를
  산출물에 남긴다.

## 검증 순서

각 단계 후 변경 worktree에서 다음을 순차 실행한다.

```bash
./gradlew :bluetape4k-images:test --tests '*PrivacyDerivativeSerializationTest'
./gradlew :bluetape4k-images-spring-boot:test --tests '*Serialization*' --tests '*AutoConfiguration*'
./gradlew :bluetape4k-images:compileKotlin :bluetape4k-images-spring-boot:compileKotlin
./gradlew :bluetape4k-images:test :bluetape4k-images-spring-boot:test --no-daemon
./gradlew detekt
./gradlew :bluetape4k-images:generatePomFileForMavenJavaPublication :bluetape4k-images:generateMetadataFileForMavenJavaPublication
git diff --check
```

그 후 6관점 코드 리뷰, lesson 문서/commit, PR 생성, PR 후 리뷰, exact-head job 확인을
순서대로 수행한다. CI가 green이어도 merge는 별도 게이트로 유지하며, 사용자의 fresh
merge approval 전에는 merge하지 않는다.

## 위험과 중단 조건

- Jackson 3.2.1 실제 API가 계획과 다르면 먼저 `javap`/minimal compile fixture로 계약을
  수정하고 구현을 중단한다.
- `api` 의존성으로 public POM/GMM이 예상보다 크게 변하면 dependency scope를 임의로
  바꾸지 말고 consumer smoke와 함께 계획을 갱신한다.
- Java deserialization invariant를 입증하지 못하면 batch snapshot을 공개하지 않는다.
- bounded decode가 materialization 이후에만 동작하면 security gate를 실패로 기록하고
  구현을 완료 처리하지 않는다.
- existing Spring bean behavior가 “conditional omission”인지 “fail-fast”인지 코드와
  테스트가 일치하지 않으면 해당 계약을 먼저 확정한다.
