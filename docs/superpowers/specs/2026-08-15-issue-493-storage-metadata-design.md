# STORAGE-1: ImageStorage object metadata capability 설계

## 목적

`ImageStorage` 소비자가 object body를 내려받지 않고 size, ETag, content type,
last-modified를 조회할 수 있도록 선택적 capability를 추가한다. 기존
`ImageStorage` 구현체와 외부 test double의 source/ABI 호환성은 유지한다.

기준 구현 HEAD는 `f0e6f641e3f55a9acc92f1b6fc5127b8c023fa30`이며 대상 issue는
[#493](https://github.com/bluetape4k/bluetape4k-image/issues/493), epic은
[#507](https://github.com/bluetape4k/bluetape4k-image/issues/507)이다.

## 현재 근거와 경계

- `ImageStorage`는 upload/download/delete/exists/list만 선언한다.
- `ImageUploadResult`에는 upload 시점 metadata가 있지만 read path가 이를 재조회할
  수 없다.
- `LocalImageStorage`는 `BasicFileAttributes`를 descriptor-relative로 읽을 수 있다.
  Local ETag은 backend가 보장하지 않으므로 size 문자열을 ETag으로 추측하지 않는다.
- `S3Operations` 현재 공개 계약에는 `headObject`가 없고,
  `S3Resource.contentLength()`와 `lastModified()`는 각각 별도 HEAD를 실행한다.
  따라서 `bluetape4k-aws`에 provider-neutral `S3ObjectMetadata`와 suspend
  `S3Operations.headObject`를 먼저 추가한다. 정확한 signature는
  `suspend fun headObject(bucket: String, key: String): S3ObjectMetadata`이며,
  `S3ObjectMetadata`는 `io.bluetape4k.aws.spring.s3` package의
  `Serializable` data class(`sizeBytes: Long`, `etag: String?`,
  `contentType: String?`, `lastModified: Instant?`)이다. 새 interface method는
  source/ABI 호환을 위해 unsupported default를 제공하고, 기존 custom 구현체의
  default 경로는 metadata capability를 광고하지 않고 fail closed한다.
  `S3CoroutinesTemplate`만 단일 async `HeadObject` 응답을 반환하고,
  `MicrometerS3Operations`도 이 호출을 계측한다.
- image 모듈은 해당 upstream 최소 버전을 고정한 뒤 단일 HEAD snapshot을
  metadata와 download pre-check에 사용한다. AWS SDK 타입은 public image API로
  유출하지 않는다.
- `bluetape4k-dependencies` catalog의 `bluetape4k-aws-bom` 최소 버전과 upstream
  headObject PR SHA를 release-train metadata에 고정한다. `compileOnly`인 점을
  고려해 `ImagesStorageAutoConfiguration`은 S3 backend 생성 시
  `S3Operations.headObject` method 존재를 reflection으로 확인하고, 구 runtime이면
  `IllegalStateException`으로 startup fail closed한다. 구 버전으로 되돌릴 때는
  upstream AWS artifact → image artifact 순서로 rollback하며, image artifact만
  먼저 되돌리지 않는다.
- 현재 upstream 선행 PR은 [bluetape4k-aws #516](https://github.com/bluetape4k/bluetape4k-aws/pull/516),
  exact head는 `24c8039006220de654c732f722f3c7beb9b5b74f`이다. 이미지 worktree의
  targeted build는 이 commit을 `mavenLocal`에 `0.6.0-issue493-SNAPSHOT`으로
  publish한 임시 catalog를 사용했다. 안정 release version을 추측하지 않으며,
  image의 catalog source ref `45235aa22184b6a2280f530fb90c82a94e31c59d`와
  dependency catalog evidence ref `9db9c2c65d8d4663f2658b0f0cf1a15b43d02a15`를
  release train 증거로 보존한다.
- S3 byte/Path download는 HEAD size pre-check와 실제 download 뒤 byte-count 검사를
  모두 유지한다. 실제 byte 수가 HEAD snapshot과 다르면 limit 이내여도
  `ValidationException`으로 fail closed한다.
- #480의 upload atomicity/path streaming 재작성은 범위에서 제외한다.

## 제안 API

### `ImageObjectMetadata`

`io.bluetape4k.images.spring`에 provider-neutral immutable model을 추가한다.

```kotlin
data class ImageObjectMetadata(
    val key: ImageObjectKey,
    val sizeBytes: Long,
    val etag: String? = null,
    val contentType: String? = null,
    val lastModified: Instant? = null,
) : Serializable
```

- `sizeBytes`는 음수가 될 수 없다.
- `etag`는 opaque backend token이다. MD5, multipart ETag, content hash로 해석하지
  않는다.
- backend가 값을 제공하지 않으면 `etag`, `contentType`, `lastModified`는 `null`이다.
- data class에는 `serialVersionUID`를 둔다.

### `ImageObjectMetadataReader`

`io.bluetape4k.images.spring.storage`에 선택적 capability를 추가한다.

```kotlin
interface ImageObjectMetadataReader {
    suspend fun readMetadata(key: ImageObjectKey): ImageObjectMetadata
}
```

`ImageStorage`에는 새 abstract method를 추가하지 않는다. 소비자는
`storage as? ImageObjectMetadataReader`로 capability를 탐색하고, capability가
없으면 backend가 metadata read를 지원하지 않는 것으로 처리한다.

### Decorator 보존

Micrometer가 `ImageStorage`를 감쌀 때 capability를 잃지 않도록 두 가지 decorator를
둔다.

- capability가 없는 delegate: 기존 `MetricImageStorage`를 사용한다.
- `ImageObjectMetadataReader`를 구현한 delegate: BPP가
  `MetricImageStorageWithMetadata`를 선택하고 `readMetadata`를 delegate에
  전달한다.
- `MetricImageStorage`와 `MetricImageStorageWithMetadata` 모두 이미 metric wrapper인
  delegate를 다시 감싸지 않는다. capability 없는 custom storage에는 metadata
  interface를 광고하지 않는다.

이 선택은 Spring bean이 unsupported capability를 거짓으로 광고하지 않게 하며,
기존 `MetricImageStorage` 생성자와 `ImageStorage` 구현 source/ABI를 보존한다.
metadata read 자체는 현재 metric 이름을 추가하지 않고, 다음 train에서 관측성
요구가 확정될 때 별도 metric으로 다룬다.

## Backend 동작

### Local

`LocalImageStorage.readMetadata`는 `resolveKey`와 기존
`readObjectAttributes`를 사용해 body를 열지 않고 attributes를 읽는다.

- `sizeBytes = BasicFileAttributes.size()`
- `lastModified = BasicFileAttributes.lastModifiedTime().toInstant()`
- `etag = null`
- `contentType = null`

missing, permission, symbolic link, non-regular file, cancellation 매핑은 기존
storage exception 계약을 그대로 따른다.

### S3

S3는 upstream `operations.headObject(bucket, objectKey)` 한 번으로 snapshot을
받는다. 이 호출은 body stream을 열지 않고 AWS `HeadObject` 한 번을 실행한다.

- `sizeBytes = head.sizeBytes`
- `lastModified = head.lastModified`
- `etag = head.etag`
- `contentType = head.contentType`

예외는 기존 `toImageStorageException` 매핑으로 변환하고
`CancellationException`은 먼저 재전파한다. HEAD failure를 `listPage` fallback으로
숨기지 않는다.

byte-array `download`는 동일한 HEAD snapshot으로 pre-check한 뒤
`operations.downloadBytes`를 호출하고, 반환된 byte 수가 snapshot의
`sizeBytes`와 일치하는지와 configured limit을 다시 검증한다. Path download도
같은 크기 일치 검사를 수행한다. `S3Resource`는 body streaming adapter로만 남으며
metadata HEAD의 source가 아니다.

## 실패·경합 계약

| 상황 | 기대 결과 |
|---|---|
| key 없음 | `NotFoundException`, body read 0회 |
| HEAD 권한 거부 | `AccessDeniedException`, list/download fallback 0회 |
| HEAD 일시 오류 | `TransientException`, fail closed |
| caller cancellation | `CancellationException` 원형 재전파 |
| HEAD는 허용, download 결과가 limit 초과 | `ValidationException`, caller 결과/목적지 미노출 |
| HEAD 이후 object 교체 | snapshot size와 실제 byte count 불일치로 최종 방어 |
| Local parent/object symbolic link | 기존 validation/secure descriptor 계약 유지 |
| unsupported metadata | 해당 field만 `null`, 추측값 금지 |

예외 public message에는 backend path, bucket, credential, SDK raw message를 새로
노출하지 않는다. 기존 exception cause 보존 정책은 변경하지 않는다.

## 대안과 선택

| 대안 | 판단 | 이유 |
|---|---|---|
| `ImageStorage`에 abstract `metadata` 추가 | 거부 | 외부 구현체·decorator의 source/ABI break |
| `ImageStorage`에 default method 추가 | 거부 | capability 부재와 지원을 구분하기 어렵고 기존 계약을 넓힘 |
| 별도 optional `ImageObjectMetadataReader` | 선택 | provider-neutral, source-compatible, capability 탐색 가능 |
| 이미지 모듈이 AWS `S3Client`를 직접 호출 | 거부 | optional AWS 경계를 깨고 SDK 타입/credential 책임을 유출 |
| 현재 `S3Resource` HEAD adapter | 거부(legacy fallback) | 두 property 호출이 단일 snapshot을 보장하지 않으며, metadata/download correctness를 약화함 |
| `bluetape4k-aws` `headObject` 선행 PR | 선택 | 단일 HEAD 응답, ETag/content type, async/cancellation 경계를 upstream에 고정하므로 이번 train의 필수 선행 단계로 채택 |
| 이미지 모듈이 AWS `S3Client`를 직접 호출 | 거부 | optional AWS 경계를 깨고 SDK 타입/credential 책임을 유출 |

## 수용 기준

1. 기존 `ImageStorage`, `MetricImageStorage`, custom implementation의 기존 메서드와
   생성자 signature가 유지된다. upstream `S3Operations.headObject`는 unsupported
   default method로 추가해 기존 구현체가 source/ABI를 유지하되, 해당 구현체의
   metadata 호출은 성공한 것처럼 보이지 않고 fail closed한다.
2. Local/S3가 body를 읽지 않고 `ImageObjectMetadata`를 반환한다.
3. Local은 size/last-modified만 채우고 ETag/content type은 `null`이다.
4. `bluetape4k-aws` `headObject`가 단일 AWS HEAD 응답을 반환하고 Micrometer
   decorator가 이를 계측한다. S3는 그 snapshot을 사용하며 HEAD failure를
   fail-closed로 처리한다.
5. S3 byte/Path download는 HEAD pre-check → download → snapshot size 및 configured
   limit post-check 순서를 지킨다.
6. missing/access-denied/transient/cancellation/race/unsupported field를 targeted
   tests로 증명한다.
7. capability를 지원하는 delegate를 Micrometer로 감싸도 capability가 보존되고,
   지원하지 않는 custom delegate에는 capability가 광고되지 않으며 double wrap이
   일어나지 않는다.
8. 한국어 KDoc과 `ImageUploadResult` ETag 설명, `images-spring-boot/README.md`, `README.ko.md`에 사용법과 nullable
   field/ETag 의미를 반영한다.
9. `bluetape4k-dependencies` catalog 최소 버전·upstream PR SHA·구 runtime
   compatibility guard·AWS→image 배포/역 rollback 순서를 release checklist와
   테스트에 고정한다.

### 필수 테스트 시나리오

- fake `S3Operations.headObject`가 반환한 단일 snapshot에 대해
  `S3ImageStorage.readMetadata`가 `headObject`를 정확히 한 번 호출하고
  `downloadBytes`, `resource`, `listPage`를 호출하지 않는지 검증한다.
- byte download는 `headObject → downloadBytes` 순서를 기록한다. HEAD size와
  실제 byte count가 다른 경우 `ValidationException`을 던지고 반환 byte를
  caller에게 전달하지 않는다.
- Path download은 HEAD size와 stream count 불일치 시 기존 destination을
  유지하고 staged file을 삭제한다. 테스트 종료 후 destination parent에
  `.download` 잔여 파일이 없어야 한다.
- HEAD에서 `NotFoundException`, `AccessDeniedException`, `TransientException`,
  `CancellationException`을 각각 발생시키고 body/목적지 쓰기가 0회인지 검증한다.
- `MetricImageStorage` BPP는 metadata-capable delegate에만
  `MetricImageStorageWithMetadata`를 선택하고, 두 번 적용해도 wrapper가 하나인지
  검증한다. capability 없는 custom delegate는 `as? ImageObjectMetadataReader`
  결과가 `null`이어야 한다.
- AWS upstream test는 `S3CoroutinesTemplate.headObject`가 SDK
  `HeadObjectRequest` 한 번으로 ETag의 quote와 content type, `Instant` 정밀도를
  보존하는지, `MicrometerS3Operations`가 HEAD operation을 계측하는지 검증한다.
- public model test는 음수 size 거부, Java `javap` signature, nullable field,
  ETag 문자열의 quote 보존을 검증한다.

Local/S3 `readMetadata`는 각각 `withContext(Dispatchers.IO)` 경계를 갖고,
upstream `S3CoroutinesTemplate`은 `S3AsyncClient` await를 사용해 blocking HEAD를
호출하지 않는다. `CancellationException`은 image와 upstream 양쪽에서 래핑하지
않는다.

## DoD

- 설계·계획의 독립 6-lane review에서 P0/P1이 없다.
- `bluetape4k-aws` 선행 PR이 merged 또는 image CI가 참조할 수 있는 최소 snapshot으로
  고정되어 있다.
- 구 `bluetape4k-aws` runtime에서 `NoSuchMethodError` 대신 startup compatibility
  failure가 발생하고, rollback 절차가 문서에 남아 있다.
- `images-spring-boot` targeted test, compile, `detekt`, `git diff --check`가 통과한다.
- issue #493과 epic #507을 연결한 PR body가 한국어이며 마지막 `## DoD Status`를
  포함한다.
- CI exact-head 증거와 변경 파일/known gap을 merge-ready 보고서에 기록한다.

## SPW writer gate

- SPW-01: PASS — issue, epic, current source, upstream `S3Resource` 근거와 미지원 값을
  명시했다.
- SPW-02: PASS — API, 경계, failure mode, alternatives, acceptance, DoD를 포함했다.
- SPW-03: PASS — 한국어 기술 문체와 code token/URL 보존을 확인했다.
- SPW-04: PASS — `ImageStorage.kt`, Local/S3 구현, `S3Operations.kt`, `S3Resource.kt`,
  `S3CoroutinesTemplate.kt`, `MicrometerS3Operations.kt`, issue #493을 대조했다.
- SPW-05: PASS — Markdown read-back에서 heading/table/code fence와 scope drift가 없다.
