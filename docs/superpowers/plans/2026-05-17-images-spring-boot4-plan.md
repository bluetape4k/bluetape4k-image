# images-spring-boot4 구현 계획

- **문서 종류**: 내부 구현 계획 (한국어 작성, CLAUDE.md 문서 언어 정책)
- **작성일**: 2026-05-17
- **연결 스펙**: `docs/superpowers/specs/2026-05-17-images-spring-boot4-design.md`
- **대상 이슈**: #5 — `S3 / CDN / Spring Boot 자동 구성 통합`
- **대상 모듈(신규)**: `images-spring-boot4`
- **그룹/버전**: `io.github.bluetape4k.image` / `0.1.0-SNAPSHOT`
- **Kotlin / JVM / Spring Boot**: Kotlin 2.3 / Java 21 / Spring Boot 4.0.6

---

## 개요

본 계획은 스펙(§1–§10)을 기반으로, 의존 그래프에 맞게 작업을 정렬한 단계별 구현 계획이다.
모든 작업은 이전 단계(Phase)/이전 작업(Task)의 결과물에만 의존하며, 다음 원칙을 고정 불변으로 따른다.

### 공통 불변 규칙 (모든 Task에 적용)

- **Kotlin 스타일** (CLAUDE.md):
  - `!!` 금지. `?.`, `?:`, `requireNotNull`, bluetape4k `require*` 확장으로 대체.
  - `runCatching {}`는 suspend 함수 내부에서 사용 금지.
  - 모든 `suspend` catch 블록에서 `CancellationException`을 가장 먼저 rethrow.
  - `@Synchronized`/`synchronized {}` 사용 금지. 필요 시 `reentrantLock()`.
  - 모든 구현 클래스에 `companion object : KLogging()`.
  - 모든 `data class`/config 클래스에 `java.io.Serializable + serialVersionUID`.
- **검증** (CLAUDE.md):
  - 호출자 입력 검증은 `requireXxx()` (IllegalArgumentException).
  - 내부 invariant는 사용 금지(기존 코드 제외). 새 코드에서 `assertXxx()` 도입 금지.
  - 두 개 이상의 동일 타입 파라미터는 named data class로 묶기.
- **테스트** (CLAUDE.md):
  - JUnit 5 + MockK + `bluetape4k-assertions` + Testcontainers 싱글턴.
  - `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` 베이스 클래스에 적용.
  - 예외 단언은 `assertFailsWith<T> { }`; suspend 전용 패턴은 `coInvoking { } shouldThrow T::class`.
  - `runTest` 또는 bluetape4k `runSuspendIO` 사용.
- **IDE 사이클** (CLAUDE.md, Kotlin Editing Workflow):
  - 각 Task 종료 시 `ide_diagnostics` 확인 → import error/Deprecated 경고 해소.
  - 컴파일·테스트는 진단 클린 이후에 수행.

### 패키지 / 경로 규약

- 메인 패키지: `io.bluetape4k.images.spring`
- 모듈 디렉터리: `images-spring-boot4/`
- 소스: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/...`
- 테스트: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/...`
- AutoConfig imports 리소스 경로:
  `images-spring-boot4/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

---

## Phase 0 — 빌드 / 모듈 골격

### T0.1 — `libs.versions.toml` 버전·라이브러리·플러그인 추가
- **complexity**: low
- **files**: `gradle/libs.versions.toml`
- **description**: 스펙 §7.3에 정의된 모든 항목을 추가한다.
  - `[versions]`: `aws2 = "2.44.5"`, `spring-boot = "4.0.6"`, `bluetape4k-aws = "0.1.0-SNAPSHOT"` (없는 경우만 추가).
  - `[libraries]`: `aws2-bom`, `aws2-s3`, `aws2-cloudfront`, `bluetape4k-aws`, `bluetape4k-aws-spring-boot`,
    `bluetape4k-testcontainers`, `spring-boot-dependencies`, `spring-boot-autoconfigure`,
    `spring-boot-actuator`, `spring-boot-configuration-processor`, `spring-boot-starter-test`,
    `micrometer-core` (없는 경우 한정).
  - `[plugins]`: `kotlin-spring`, `spring-boot`.
- **acceptance**: 기존 `./gradlew help` 명령이 회귀 없이 통과한다.

### T0.2 — `settings.gradle.kts`에 신규 모듈 포함
- **complexity**: low
- **files**: `settings.gradle.kts`
- **description**: `include("images-spring-boot4")` (혹은 워크스페이스 표준 `includeModules("images-spring-boot4")`) 추가.
- **acceptance**: `./gradlew :images-spring-boot4:help` 실행 시 모듈을 인식한다(설사 빈 상태라도 모듈 인식).

### T0.3 — `images-spring-boot4/build.gradle.kts` 작성
- **complexity**: medium
- **files**: `images-spring-boot4/build.gradle.kts`
- **description**: 스펙 §7.1을 그대로 반영한다.
  - 플러그인: `kotlin("jvm")`, `kotlin("plugin.spring")`, `id("io.spring.dependency-management")`.
  - **`kotlin-noarg` 플러그인은 추가하지 않는다** (Boot 4 constructor binding은 noarg 없이 동작).
  - Java toolchain 21.
  - `dependencyManagement`: spring-boot-dependencies BOM → aws2 BOM → kotlin-bom (Kotlin 2.3.x 명시 임포트). 순서 중요(Risk 4).
  - 의존성:
    - `implementation(project(":images"))` — **api가 아님** (스펙 §7.1 변경점).
    - `compileOnly`: `spring-boot-autoconfigure`, `micrometer-core`, `spring-boot-actuator`,
      `bluetape4k-aws-spring-boot`, `bluetape4k-aws`, `aws2-s3`, `aws2-cloudfront`.
    - `annotationProcessor`: `spring-boot-configuration-processor`.
    - `testImplementation`: `spring-boot-starter-test`, `bluetape4k-aws-spring-boot`, `bluetape4k-aws`,
      `aws2-s3`, `aws2-cloudfront`, `bluetape4k-testcontainers`, `micrometer-core`, `spring-boot-actuator`.
- **acceptance**: `./gradlew :images-spring-boot4:tasks` 정상 출력.

### T0.4 — 테스트 리소스 파일 생성
- **complexity**: low
- **files**:
  - `images-spring-boot4/src/test/resources/junit-platform.properties`
  - `images-spring-boot4/src/test/resources/logback-test.xml`
- **description**: 기존 모듈(`images`)의 동일 파일을 참고하여 동일 설정을 복사한다(`@TestInstance` 기본 PER_CLASS 설정 포함).
- **acceptance**: `./gradlew :images-spring-boot4:compileTestKotlin -x test` 통과(빈 상태라도 OK).

---

## Phase 1 — 값 객체(Value Objects)

### T1.1 — `ImageObjectKey` 구현
- **complexity**: high
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/ImageObjectKey.kt`
- **description**: 스펙 §4.1.1 그대로 구현한다.
  - `data class ImageObjectKey private constructor(prefix: String, name: String) : java.io.Serializable`.
  - **검증은 반드시 `init` 블록에서 수행한다** (copy()도 primary constructor를 거치므로 보호됨, Codex P1-1).
  - `prefix.requireNotBlank("prefix")`, `name.requireNotBlank("name")`.
  - `require(!prefix.contains("..") && !name.contains("..")) { "prefix and name must not contain '..' segments" }`.
  - `private val VALID_SEGMENT = Regex("^[A-Za-z0-9._/-]+$")` companion에 보관, 두 입력 모두 매치 검증.
  - `val fullKey`: prefix가 `/`로 끝나지 않으면 `/` 부여하여 double-slash 방지.
  - companion: `private const val serialVersionUID: Long = 1L`, `fun of(prefix: String, name: String) = ImageObjectKey(prefix, name)`.
  - English KDoc: factory 동작과 validation 이유 명시. `## Behavior / Contract` 섹션 포함.
- **acceptance**: `ImageObjectKeyTest`(T6.1)에서 모든 케이스 통과.

### T1.2 — `ImageUploadResult` 구현
- **complexity**: low
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/ImageUploadResult.kt`
- **description**: 스펙 §4.1.2의 `data class ImageUploadResult(key, eTag, sizeBytes) : Serializable`. `serialVersionUID = 1L`.
  English KDoc.
- **acceptance**: T1.1 완료 후 컴파일 통과.

### T1.3 — `UploadOptions` 구현
- **complexity**: medium
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/UploadOptions.kt`
- **description**: 스펙 §4.1.4 구현.
  - `data class UploadOptions(contentType, cacheControl, contentDisposition, metadata) : Serializable`.
  - `init` 블록: `contentType.requireNotBlank("contentType")`, `require(contentType in ALLOWED_CONTENT_TYPES)`.
  - companion: `ALLOWED_CONTENT_TYPES`는 `image/jpeg, image/png, image/webp, image/gif, image/avif, image/heic`. SVG 제외(stored XSS).
  - `serialVersionUID = 1L`.
  - English KDoc, content-type 정책 명시.
- **acceptance**: `UploadOptionsTest`(T6.3)에서 allowlist 검증 통과.

### T1.4 — `ImageStorageException` sealed hierarchy 구현
- **complexity**: medium
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/ImageStorageException.kt`
- **description**: 스펙 §4.1.3 구현.
  - `sealed class ImageStorageException(message, cause) : RuntimeException`.
  - 하위 클래스 5종: `NotFoundException`, `AccessDeniedException`, `ConflictException`, `TransientException`, `ValidationException`.
  - 메시지에 `key.fullKey`만 노출 — PEM/서명 값 echo 금지.
  - companion `wrap(key: ImageObjectKey, e: Throwable): ImageStorageException`:
    - 이미 `ImageStorageException`이면 그대로 반환.
    - SdkException 분류는 T3.2 `S3ImageStorage` 구현 시 statusCode 매핑 로직과 함께 확장. 본 Task에서는 기본 분기와 `else → TransientException` 분기만 우선 구현.
  - English KDoc.
- **acceptance**: 컴파일 통과 + T1.1의 `fullKey`만 사용해 메시지를 생성하는지 grep 검증.

---

## Phase 2 — 인터페이스(Contracts)

### T2.1 — `CdnReadSigner` 인터페이스
- **complexity**: low
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/CdnReadSigner.kt`
- **description**: 스펙 §4.3.
  - `suspend fun signGet(key: ImageObjectKey, expiresIn: Duration): URI`.
  - KDoc: expiresIn 양수 + 구현체별 최대값 한도 명시(S3: 7일, CloudFront: 제한 없음).
  - `@throws IllegalArgumentException expiresIn ≤ 0`, `@throws ImageStorageException 서명 실패`.
- **acceptance**: T1.1 의존 컴파일 통과.

### T2.2 — `CdnWriteSigner` 인터페이스
- **complexity**: low
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/CdnWriteSigner.kt`
- **description**: 스펙 §4.3.
  - `suspend fun signPut(key: ImageObjectKey, expiresIn: Duration, options: UploadOptions): URI`.
  - KDoc 작성. `CdnReadSigner`와 **반드시 별도 인터페이스**로 분리 — `CloudFrontUrlSigner`가 read-only임을 컴파일 타임에 보장.
- **acceptance**: T1.1, T1.3 의존 컴파일 통과.

### T2.3 — `ImageStorage` 인터페이스
- **complexity**: medium
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/ImageStorage.kt`
- **description**: 스펙 §4.2.
  - 메서드 정의:
    - `suspend fun upload(key, bytes: ByteArray, options): ImageUploadResult`.
    - `suspend fun upload(key, source: Path, options): ImageUploadResult`.
    - `suspend fun download(key): ByteArray`.
    - `suspend fun download(key, destination: Path)`.
    - `suspend fun delete(key)` — idempotent.
    - `suspend fun exists(key): Boolean`.
    - `fun list(prefix: ImageObjectKey): Flow<ImageObjectKey>` — **타입을 `ImageObjectKey`로 받아 path traversal bypass 차단** (H-3).
  - 각 메서드에 English KDoc + `## Behavior / Contract` 섹션(원자성, 예외, 사이즈 한도 동작).
- **acceptance**: 컴파일 통과 + `list`가 `String`이 아닌 `ImageObjectKey`를 받는지 코드 리뷰.

---

## Phase 3 — Properties (Configuration)

### T3.1 — `ImageProcessingProperties`
- **complexity**: low
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImageProcessingProperties.kt`
- **description**: 스펙 §5.1.1.
  - `@ConfigurationProperties("bluetape4k.images.processing")`.
  - 필드: `enabled: Boolean = true`, `defaultQuality: Int = 85`.
  - `init { require(defaultQuality in 1..100) }`.
  - Serializable + serialVersionUID.
- **acceptance**: 컴파일 통과.

### T3.2 — `ImageStorageProperties`
- **complexity**: medium
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImageStorageProperties.kt`
- **description**: 스펙 §5.1.2 그대로.
  - `@ConfigurationProperties("bluetape4k.images.storage")`.
  - 필드: `enabled`, `backend: Backend = LOCAL`, `bucket: String? = null`, `keyPrefix = ""`, `maxSizeBytes = 50 * 1024 * 1024L`.
  - nested `Local(rootDir = tmp dir + "/bluetape4k-images")`, `S3(callTimeout, attemptTimeout, maxRetries, maxInFlight)`.
  - enum `Backend { LOCAL, S3 }`.
  - **모든 nested data class에 Serializable + serialVersionUID**.
  - 스펙 §6.1에 따라 `bucket` 검증은 `@PostConstruct`에서 수행(여기서는 미수행).
  - `healthProbeKey: String = ".health-probe"` 필드도 함께 추가 (Phase 4 health probe용, 스펙 §6.1 Phase 4).
- **acceptance**: 컴파일 통과 + 모든 nested data class에 `serialVersionUID` 존재.

### T3.3 — `CdnProperties` (REDACTED toString 포함)
- **complexity**: high
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/CdnProperties.kt`
- **description**: 스펙 §5.1.3 + §9.1.
  - `@ConfigurationProperties("bluetape4k.images.cdn")`.
  - `provider: Provider = S3_PRESIGN`, `enabled = false`.
  - nested `CloudFront(distributionDomain, keyPairId, privateKeyPath, privateKeyPem, defaultExpiry = PT10M, maxExpiry = PT1H)`.
  - **`CloudFront.toString()`을 override하여 `privateKeyPem = [REDACTED]`로 마스킹** (보안 핵심).
  - 모든 data class Serializable + serialVersionUID.
- **acceptance**: `CdnProperties.CloudFront(privateKeyPem = "secret").toString()` 결과에 `[REDACTED]`만 포함되고 `"secret"` 미포함(T6.5 테스트).

---

## Phase 4 — 구현체 (Storage)

### T4.1 — `LocalImageStorage` 구현
- **complexity**: high
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/storage/LocalImageStorage.kt`
- **description**: 스펙 §4.2 구현체.
  - `class LocalImageStorage(private val rootDir: Path) : ImageStorage`.
  - `companion object : KLogging()`.
  - 생성자 init 블록에서 `Files.createDirectories(rootDir)`.
  - 모든 suspend 메서드 본문은 `withContext(Dispatchers.IO)` 안.
  - **경로 검증**: `val resolved = rootDir.resolve(key.fullKey).normalize();
    require(resolved.startsWith(rootDir.normalize())) { ... }` — 실패 시 `ImageStorageException.ValidationException`.
  - `IOException` → `ImageStorageException.TransientException`로 래핑. `NoSuchFileException` → `NotFoundException`.
  - **`CancellationException`을 가장 먼저 rethrow** (broad catch 이전).
  - upload 실패 시 partial file best-effort delete(`Files.deleteIfExists`).
  - `download(key, destination)`은 `Files.newInputStream/newOutputStream` + `use { copyTo }`.
  - `delete`는 missing key에 대해 idempotent — 예외 없음.
  - `list(prefix)`는 `Files.walk` + `Flow` 변환; 디렉터리 stream은 `use {}` 보장.
  - `maxSizeBytes` 체크: 본 클래스는 properties 의존이 없으므로 별도 `maxSizeBytes` 파라미터를 생성자 주입으로 받는다(LocalStorageConfiguration에서 전달).
  - **`runCatching` 사용 금지** — 모든 catch는 try/catch.
  - **`!!` 사용 금지**.
  - English KDoc.
- **acceptance**: 모든 메서드가 `Dispatchers.IO`로 호핑; path traversal 시도가 `ValidationException`; T6.6의 모든 테스트 케이스 통과.

### T4.2 — `S3ImageStorage` 구현
- **complexity**: high
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/storage/S3ImageStorage.kt`
- **description**: 스펙 §4.2 구현체.
  - `class S3ImageStorage(private val operations: S3Operations, private val properties: ImageStorageProperties) : ImageStorage`.
  - `companion object : KLogging()`.
  - init 블록: `properties.bucket.requireNotBlank("bucket")`, `require(properties.maxSizeBytes > 0)`.
  - `S3Operations`는 compileOnly이므로 본 파일에서 직접 import OK (이 파일 자체가 nested config에서만 인스턴스화됨, classpath 보장).
  - 모든 SdkException → `ImageStorageException.wrap(key, e)` (T1.4의 wrap을 확장: S3Exception statusCode 분류).
    - 404 → `NotFoundException`
    - 403 → `AccessDeniedException`
    - 409 → `ConflictException`
    - 그 외 → `TransientException`.
  - **`CancellationException` 가장 먼저 rethrow**.
  - `upload` 실패 시 best-effort multipart abort (operations API 활용).
  - `download` 시 `properties.maxSizeBytes` 초과 → `ValidationException`. HEAD object size로 사전 검증, 또는 download 중 누적 byte counter.
  - `upload` 시 `bytes.size > maxSizeBytes` 또는 `Files.size(source) > maxSizeBytes` → `ValidationException`.
  - `list(prefix: ImageObjectKey)`는 S3 paginated list와 `Flow` 변환.
  - `keyPrefix` 처리: `properties.keyPrefix`와 `key.fullKey`를 결합 — single-slash 정규화.
  - English KDoc + `## Behavior / Contract`.
- **acceptance**: T6.7 `S3ImageStorageTest` 모든 케이스 통과. T1.4 `wrap()`을 statusCode 기반 분류로 확장(같은 Task 내).

### T4.3 — `T1.4 ImageStorageException.wrap()` 확장 (T4.2 후속)
- **complexity**: medium
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/ImageStorageException.kt`
- **description**: T4.2와 동시 작업이 가능하나, 의존성상 T4.2가 완료 후 또는 함께 PR로 묶는다.
  - S3 SDK `S3Exception.statusCode()` → `NotFoundException`/`AccessDeniedException`/`ConflictException`/`TransientException` 매핑 로직 추가.
  - `S3Exception` 클래스는 `compileOnly` 의존성이므로 `Class.forName(...)` 또는 별도 어댑터로 isolation 가능. 다만 `S3ImageStorage` 클래스 자체가 nested config에서만 인스턴스화되므로, `S3Exception`을 직접 import해도 호출 시점에만 해석된다. 본 Task에서는 직접 import 채택.
  - **SDK 오류 메시지에 키 값이 포함될 수 있으므로 wrap 시 메시지 sanitize** — `key.fullKey`만 사용, SDK 원문은 `cause`로만 전달.
- **acceptance**: T4.2의 catch가 본 함수만 호출하도록 통합.

---

## Phase 5 — 구현체 (CDN Signers)

### T5.1 — `CloudFrontUrlSigner` 구현
- **complexity**: high
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/cdn/CloudFrontUrlSigner.kt`
- **description**: 스펙 §4.3 + §9.1.
  - `class CloudFrontUrlSigner(properties: CdnProperties.CloudFront) : CdnReadSigner` — **`CdnWriteSigner`를 구현하지 않는다**.
  - `companion object : KLogging()`.
  - init 블록:
    - `properties.distributionDomain.requireNotBlank("distributionDomain")`.
    - `val domain = properties.distributionDomain ?: error("distributionDomain required")` — **`!!` 금지** (H-2). local val로 추출.
    - `require(!domain.startsWith("http") && !domain.contains("/")) { "distributionDomain must be a bare hostname (e.g., d123.cloudfront.net), not a full URL" }` — 술어 정방향(H-1).
    - `properties.keyPairId.requireNotBlank("keyPairId")`.
    - `require(properties.maxExpiry.isPositive()) { "maxExpiry must be positive" }`.
    - **두 키 소스 동시 지정 → `IllegalStateException("Specify either private-key-path or private-key-pem, not both.")`**.
    - 키 소스 선택:
      - `privateKeyPath` 우선: `Files.readAllBytes(path)` → `RSAPrivateKey` 파싱 → `Arrays.fill(bytes, 0)` zero-out.
      - `privateKeyPem`만 있으면 사용 + `WARN` 로그(인라인 PEM 비권장 사유 명시). zero-out 불가.
      - 둘 다 null이면 `IllegalStateException`.
    - 파싱 실패 시 PEM 본문 echo 금지 — 경로/SHA-256 지문만 메시지에 표시.
  - `signGet(key, expiresIn)`:
    - `require(expiresIn.isPositive())`, `require(expiresIn <= properties.maxExpiry)`.
    - `CloudFrontUtilities.getSignedUrlWithCannedPolicy(...)` 호출. CPU 작업이므로 Dispatchers hop **불필요**.
    - **`CancellationException` 가장 먼저 rethrow** (suspend 함수).
    - 반환 `URI`.
  - English KDoc.
- **acceptance**: T6.4 `CloudFrontUrlSignerTest` 모든 케이스 통과. 특히
  `assertFalse { signer is CdnWriteSigner }` 타입 검증 통과.

### T5.2 — `S3PreSignedUrlSigner` 구현
- **complexity**: medium
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/cdn/S3PreSignedUrlSigner.kt`
- **description**: 스펙 §4.3.
  - `class S3PreSignedUrlSigner(private val operations: S3Operations, private val properties: ImageStorageProperties) : CdnReadSigner, CdnWriteSigner`.
  - `companion object : KLogging()`.
  - init: `properties.bucket.requireNotBlank("bucket")`.
  - `signGet`: `require(expiresIn.isPositive())`, `require(expiresIn <= Duration.ofDays(7))` (S3 SigV4 max).
    `operations.presignGet(...)` 위임.
  - `signPut`: 동일한 expiry 검증 + `operations.presignPut(...)` 위임 + `options`의 `contentType` 등을 메타데이터로 전달.
  - **`CancellationException` 가장 먼저 rethrow**.
  - English KDoc.
- **acceptance**: T6.8 `S3PreSignedUrlSignerTest` 통과 + `s3PreSignedUrlSigner is CdnReadSigner && s3PreSignedUrlSigner is CdnWriteSigner` 타입 검증.

---

## Phase 6 — 단위 테스트 (Value Objects / Implementations)

> Phase 6 테스트는 해당 구현 Task에 자연스럽게 묶여도 좋다. 의존성을 분명히 하기 위해 별도 Task로 나열한다.

### T6.1 — `ImageObjectKeyTest`
- **complexity**: medium
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/ImageObjectKeyTest.kt`
- **description**: 스펙 §8.11.
  - `of("foo", "bar.jpg") → fullKey == "foo/bar.jpg"`.
  - `of("foo/", "bar.jpg") → fullKey == "foo/bar.jpg"` (double-slash 방지).
  - `of("foo", "../etc/passwd") → IllegalArgumentException`.
  - `of("foo", "bar/../secret") → IllegalArgumentException`.
  - `of("", "bar.jpg")` / `of("foo", "")` → `IllegalArgumentException`.
  - `of("foo", "bar baz") → IllegalArgumentException` (공백).
  - **`copy()`로 `..` 주입 → `IllegalArgumentException`** (init 블록 동작 보호 검증, P1-1).
- **acceptance**: 모든 케이스 통과.

### T6.2 — `ImageUploadResultTest` (간단 직렬화/equals)
- **complexity**: low
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/ImageUploadResultTest.kt`
- **description**: data class equals/hashCode/copy + Java serialization round-trip(serialVersionUID 검증).
- **acceptance**: 통과.

### T6.3 — `UploadOptionsTest`
- **complexity**: low
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/UploadOptionsTest.kt`
- **description**: 스펙 §8.10.
  - allowlist 내 contentType 정상 생성.
  - allowlist 외 contentType → `IllegalArgumentException`.
  - blank contentType → `IllegalArgumentException`.
- **acceptance**: 모든 케이스 통과.

### T6.4 — `CloudFrontUrlSignerTest`
- **complexity**: high
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/cdn/CloudFrontUrlSignerTest.kt`
- **description**: 스펙 §8.4.
  - 에페머럴 RSA 키페어 생성(KeyPairGenerator) → PEM 직렬화 → `CdnProperties.CloudFront` 주입.
  - `signGet`: scheme=`https`, host==`distributionDomain`, query에 `Key-Pair-Id`, `Expires`, `Signature` 포함.
  - 만료 시각: `Instant.now() + expiresIn` ±5초.
  - `expiresIn <= 0` → `IllegalArgumentException`.
  - `expiresIn > maxExpiry` → `IllegalArgumentException`.
  - **타입 체크**: `assertFalse { signer is CdnWriteSigner }`.
  - 잘못된 PEM → 생성자에서 `ImageStorageException.ValidationException` (또는 `IllegalArgumentException`, 스펙에서는 ValidationException 명시).
  - `private-key-path` + `private-key-pem` 동시 지정 → `IllegalStateException`.
  - **Actuator 마스킹**: `CdnProperties.CloudFront(privateKeyPem = "secret").toString()`에 `secret` 미포함, `[REDACTED]` 포함.
- **acceptance**: 모든 케이스 통과.

### T6.5 — `CdnPropertiesRedactionTest`
- **complexity**: low
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/autoconfigure/CdnPropertiesRedactionTest.kt`
- **description**: §9.1 Actuator 마스킹 단독 단위 테스트.
  - `CdnProperties.CloudFront(privateKeyPem = "TOP_SECRET").toString()` →
    `assertTrue { it.contains("[REDACTED]") && !it.contains("TOP_SECRET") }`.
- **acceptance**: 통과.

### T6.6 — `LocalImageStorageTest`
- **complexity**: high
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/storage/LocalImageStorageTest.kt`
- **description**: 스펙 §8.2.
  - `@TempDir`로 `rootDir` 주입.
  - Happy: `upload → exists → download → delete → exists(false)`.
  - `list(prefix)` 결과 검증.
  - 존재하지 않는 key `download` → `NotFoundException`.
  - `delete` 없는 key → idempotent.
  - Path traversal: `ImageObjectKey.of("foo", "../etc/passwd")` → `IllegalArgumentException`.
  - `rootDir` 바깥 직접 resolve → `ValidationException`.
  - **취소**: `withTimeout(1.milliseconds) { largeUpload() }` → `TimeoutCancellationException` propagation.
  - **동시성**: 동일 key N 코루틴 동시 upload → last-write-wins 동작 검증.
  - 파일 스트리밍: `maxSizeBytes` 초과 → `ValidationException`; 정상 Path round-trip.
- **acceptance**: 모든 케이스 통과.

### T6.7 — `S3ImageStorageTest` (Floci)
- **complexity**: high
- **files**:
  - `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/storage/AbstractS3StorageTest.kt`
  - `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/storage/S3ImageStorageTest.kt`
- **description**: 스펙 §8.3.
  - `AbstractS3StorageTest`: `@Suppress("DEPRECATION")` + `companion object : KLogging() { val floci = FlociServer.Launcher.floci }` 싱글턴.
  - bucket 생성 후 `runSuspendIO`로 happy path: `upload/download/delete/exists/list`.
  - 존재하지 않는 bucket → `ImageStorageException` (Transient/AccessDenied 둘 중 하나).
  - 존재하지 않는 key `download` → `NotFoundException`.
  - `delete` 없는 key → idempotent.
  - `maxSizeBytes` 초과 upload → `ValidationException`.
  - 취소: `withTimeout(1.milliseconds) { largeUpload() }` → `TimeoutCancellationException` propagation.
- **acceptance**: Floci 컨테이너 가용 환경에서 통과.

### T6.8 — `S3PreSignedUrlSignerTest` (MockK)
- **complexity**: medium
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/cdn/S3PreSignedUrlSignerTest.kt`
- **description**: 스펙 §8.5.
  - MockK로 `S3Operations.presignGet/presignPut` mocking.
  - 위임 파라미터(key, expires) 전달 정확성 검증.
  - `expiresIn <= 0` → `IllegalArgumentException`.
  - `expiresIn > 7days` → `IllegalArgumentException`.
  - **타입 체크**: `assertTrue { signer is CdnReadSigner && signer is CdnWriteSigner }`.
  - `CancellationException` rethrow 확인(`coInvoking { ... } shouldThrow CancellationException::class`).
- **acceptance**: 모든 케이스 통과.

---

## Phase 7 — AutoConfiguration (Phase Classes)

### T7.1 — `ImagesProcessingAutoConfiguration`
- **complexity**: medium
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesProcessingAutoConfiguration.kt`
- **description**: 스펙 §6.1 Phase 1.
  - `@AutoConfiguration`, `@ConditionalOnProperty(prefix="bluetape4k.images.processing", name=["enabled"], havingValue="true", matchIfMissing=true)`.
  - `@EnableConfigurationProperties(ImageProcessingProperties::class)`.
  - 본 phase는 placeholder — 추후 `ImageProcessor` 빈 자리.
- **acceptance**: 컴파일 통과.

### T7.2 — `ImagesStorageAutoConfiguration` + nested S3 / Local
- **complexity**: high
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesStorageAutoConfiguration.kt`
- **description**: 스펙 §6.1 Phase 2 + P0-2/P0-3 + Round 2 C-1.
  - `@AutoConfiguration(afterName = ["io.bluetape4k.aws.spring.boot.autoconfigure.S3AutoConfiguration", "io.bluetape4k.images.spring.autoconfigure.ImagesProcessingAutoConfiguration"])` — **`afterName` (String[]) 사용**. `after` (KClass[]) 금지.
  - `@ConditionalOnProperty(prefix="bluetape4k.images.storage", name=["enabled"], havingValue="true", matchIfMissing=true)`.
  - `@EnableConfigurationProperties(ImageStorageProperties::class)`.
  - **Nested `S3StorageConfiguration`**:
    - `@Configuration(proxyBeanMethods = false)`.
    - `@ConditionalOnClass(name = ["io.bluetape4k.aws.spring.boot.S3Operations"])`.
    - `@ConditionalOnProperty(prefix="bluetape4k.images.storage", name=["backend"], havingValue="s3")`.
    - **생성자 주입**: `class S3StorageConfiguration(private val properties: ImageStorageProperties)`.
    - **`@PostConstruct fun validateBucket()`는 no-arg** (JSR-250 강제, C-1).
      - 본문: `properties.bucket.requireNotBlank("bluetape4k.images.storage.bucket (required when backend=s3)")`.
    - `@Bean @ConditionalOnMissingBean(ImageStorage::class) fun s3ImageStorage(operations: S3Operations): ImageStorage = S3ImageStorage(operations, properties)`.
  - **Nested `LocalStorageConfiguration`**:
    - `@Configuration(proxyBeanMethods = false)`.
    - **조건은 `@ConditionalOnMissingBean(ImageStorage::class)` 만** — `backend=local` 조건 **제거**(P0-2).
    - `@Bean @ConditionalOnMissingBean(ImageStorage::class) fun localImageStorage(properties: ImageStorageProperties): ImageStorage = LocalImageStorage(Path.of(properties.local.rootDir), properties.maxSizeBytes)`.
  - **`@ConditionalOnProperty`는 각 phase 클래스에 적용** (CLAUDE.md Spring Boot Auto-Configuration 룰).
- **acceptance**: 컴파일 통과 + T8.2 시나리오 6종 통과.

### T7.3 — `ImagesCdnAutoConfiguration` + nested S3Presign / CloudFront
- **complexity**: high
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesCdnAutoConfiguration.kt`
- **description**: 스펙 §6.1 Phase 3 + P0-3 + P1-2.
  - `@AutoConfiguration(afterName = ["io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration"])`.
  - `@ConditionalOnProperty(prefix="bluetape4k.images.cdn", name=["enabled"], havingValue="true")` — `matchIfMissing` 없음(기본 disabled).
  - `@EnableConfigurationProperties(CdnProperties::class)`.
  - **Nested `S3PresignCdnConfiguration`** (P0-3):
    - `@Configuration(proxyBeanMethods = false)`.
    - `@ConditionalOnClass(name = ["io.bluetape4k.aws.spring.boot.S3Operations"])`.
    - `@ConditionalOnProperty(prefix="bluetape4k.images.cdn", name=["provider"], havingValue="s3_presign", matchIfMissing=true)`.
    - **`@Bean @ConditionalOnMissingBean(S3PreSignedUrlSigner::class) fun s3PreSignedUrlSigner(...): S3PreSignedUrlSigner = ...`** — **반환 타입은 구체 타입 `S3PreSignedUrlSigner`** (P1-2). 인터페이스 타입(`CdnReadSigner`/`CdnWriteSigner`) 둘 다 자동 만족.
  - **Nested `CloudFrontCdnConfiguration`**:
    - `@Configuration(proxyBeanMethods = false)`.
    - `@ConditionalOnClass(name = ["software.amazon.awssdk.services.cloudfront.CloudFrontUtilities"])`.
    - `@ConditionalOnProperty(prefix="bluetape4k.images.cdn", name=["provider"], havingValue="cloudfront")`.
    - `@Bean @ConditionalOnMissingBean(CdnReadSigner::class) fun cloudFrontUrlSigner(properties: CdnProperties): CloudFrontUrlSigner = CloudFrontUrlSigner(properties.cloudfront)`.
  - maintainer note 주석: `afterName=[String]` 변경 금지 사유 명시(NoClassDefFoundError).
- **acceptance**: T8.3 시나리오 5종 통과.

### T7.4 — `ImagesHealthAutoConfiguration` + `ImageStorageHealthIndicator`
- **complexity**: medium
- **files**:
  - `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesHealthAutoConfiguration.kt`
  - `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/health/ImageStorageHealthIndicator.kt`
- **description**: 스펙 §6.1 Phase 4.
  - `@AutoConfiguration(afterName=["...ImagesStorageAutoConfiguration"])`.
  - `@ConditionalOnClass(name=["org.springframework.boot.actuate.health.HealthIndicator"])`.
  - `@ConditionalOnProperty(prefix="bluetape4k.images.health", name=["enabled"], havingValue="true", matchIfMissing=true)`.
  - `@Bean @ConditionalOnMissingBean(name=["imageStorageHealthIndicator"]) fun imageStorageHealthIndicator(storage: ImageStorage, properties: ImageStorageProperties): HealthIndicator = ImageStorageHealthIndicator(storage, properties.healthProbeKey)`.
  - `ImageStorageHealthIndicator`: probe key (기본 `.health-probe`)에 대해 `exists` suspend 호출(coroutine bridge `runBlocking`/`kotlinx-coroutines-reactor`). `Health.up()`/`down()`.
    - **`runBlocking`은 health probe 한정으로 허용** (CLAUDE.md "tightly controlled lazy initialization" 예외).
  - English KDoc.
- **acceptance**: 컴파일 통과 + T6.9 `ImageStorageHealthIndicatorTest` 통과.

### T7.5 — `ImagesMetricsAutoConfiguration` (Micrometer optional decorator)
- **complexity**: medium
- **files**: `images-spring-boot4/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesMetricsAutoConfiguration.kt`
- **description**: 스펙 §6.1 Phase 5.
  - `@AutoConfiguration(afterName=["...ImagesStorageAutoConfiguration"])`.
  - `@ConditionalOnClass(name=["io.micrometer.core.instrument.MeterRegistry"])`.
  - `ImageStorage` 빈을 Timer/Counter 데코레이터로 래핑(BeanPostProcessor 또는 `@Bean ImageStorage` 재정의 + `@ConditionalOnBean(MeterRegistry::class)`).
  - 측정 메트릭: `images.storage.upload.duration`, `images.storage.upload.errors`, `images.storage.download.duration`, `images.cdn.sign.duration`.
  - English KDoc.
- **acceptance**: 컴파일 통과 + 메트릭 wrapper 단위 테스트 통과(간단 happy path).

### T7.6 — `AutoConfiguration.imports` 등록
- **complexity**: low
- **files**: `images-spring-boot4/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- **description**: 5개 FQCN 한 줄씩.
  ```
  io.bluetape4k.images.spring.autoconfigure.ImagesProcessingAutoConfiguration
  io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration
  io.bluetape4k.images.spring.autoconfigure.ImagesCdnAutoConfiguration
  io.bluetape4k.images.spring.autoconfigure.ImagesHealthAutoConfiguration
  io.bluetape4k.images.spring.autoconfigure.ImagesMetricsAutoConfiguration
  ```
- **acceptance**: 5개 phase 모두 등록. `./gradlew :images-spring-boot4:compileJava` 통과.

---

## Phase 8 — AutoConfiguration 테스트 (ApplicationContextRunner)

### T8.1 — `ImagesProcessingAutoConfigurationTest`
- **complexity**: medium
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesProcessingAutoConfigurationTest.kt`
- **description**: 스펙 §8.8.
  - `enabled=true` → `ImageProcessingProperties` 빈 바인딩.
  - `enabled=false` → 빈 없음.
  - `default-quality=0` 또는 `101` → context 실패.
- **acceptance**: 통과.

### T8.2 — `ImagesStorageAutoConfigurationTest`
- **complexity**: high
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesStorageAutoConfigurationTest.kt`
- **description**: 스펙 §8.6 — `ApplicationContextRunner` 6 시나리오.
  1. `backend=local` → `LocalImageStorage` 빈, `S3ImageStorage` 없음.
  2. `backend=s3` + 사용자 제공 `S3Operations` 빈 → `S3ImageStorage`.
  3. `backend=s3` + `FilteredClassLoader(S3Operations 차단)` → `LocalImageStorage` fallback (P0-2 검증).
  4. `enabled=false` → 어떤 ImageStorage도 없음.
  5. 사용자 직접 `@Bean ImageStorage` 등록 → 자동 구성 빈 미등록(`@ConditionalOnMissingBean`).
  6. `backend=s3` + `bucket` 미설정 → context fail (`BeanCreationException` 또는 `IllegalArgumentException`). **`@PostConstruct` no-arg 형식 검증의 핵심**.
- **acceptance**: 6 시나리오 모두 통과.

### T8.3 — `ImagesCdnAutoConfigurationTest`
- **complexity**: high
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesCdnAutoConfigurationTest.kt`
- **description**: 스펙 §8.7 — 5 시나리오.
  1. `enabled=false` (default) → CDN 빈 없음.
  2. `provider=cloudfront` + 키 경로 미설정 → context fail.
  3. `provider=cloudfront` + private-key-path 설정 → `CloudFrontUrlSigner` 빈(`CdnReadSigner`).
  4. `provider=s3_presign` + `S3Operations` 빈 제공 → `S3PreSignedUrlSigner` 빈(`CdnReadSigner` + `CdnWriteSigner`).
  5. `provider=s3_presign` + `FilteredClassLoader(S3Operations 차단)` → CDN 빈 없음.
  - **타입 검증**: `context.getBean<CloudFrontUrlSigner>()` not `is CdnWriteSigner`,
    `context.getBean<S3PreSignedUrlSigner>() is CdnReadSigner && is CdnWriteSigner`.
- **acceptance**: 5 시나리오 모두 통과.

### T8.4 — `ImageStorageHealthIndicatorTest`
- **complexity**: medium
- **files**: `images-spring-boot4/src/test/kotlin/io/bluetape4k/images/spring/health/ImageStorageHealthIndicatorTest.kt`
- **description**: 스펙 §8.9.
  - MockK `ImageStorage.exists()` true → `Health.up()`.
  - MockK `ImageStorage.exists()` throws `ImageStorageException` → `Health.down()`.
- **acceptance**: 통과.

---

## Phase 9 — 문서 / 빌드 마무리

### T9.1 — `README.md` 작성
- **complexity**: medium
- **files**: `images-spring-boot4/README.md`
- **description**: 영어 작성. 스펙 §1, §4, §5, §6 요약.
  - 1) Architecture (Mermaid 다이어그램).
  - 2) Core features.
  - 3) Quickstart (Gradle 의존성, YAML 예시).
  - 4) Properties reference (`bluetape4k.images.processing/storage/cdn`).
  - 5) Security notes (CloudFront PEM 운영 권고, Actuator 마스킹).
- **acceptance**: 영문 README 존재; Mermaid 정상 렌더.

### T9.2 — `README.ko.md` 작성
- **complexity**: medium
- **files**: `images-spring-boot4/README.ko.md`
- **description**: 한국어 작성. `README.md`와 구조 정렬.
- **acceptance**: 한국어 README 존재; `README.md`와 동일 섹션 구조.

### T9.3 — 워크스페이스/모듈 `CLAUDE.md` 모듈 표 업데이트
- **complexity**: low
- **files**:
  - `/Users/debop/work/bluetape4k/CLAUDE.md` (워크스페이스)
  - `bluetape4k-image/CLAUDE.md` (모듈 표)
- **description**: 두 CLAUDE.md 모두 모듈 표에 `images-spring-boot4` 행 추가:
  - 설명: "Spring Boot 4 auto-config for image storage (S3/local) and CDN signing (CloudFront/S3 presign)."
- **acceptance**: 두 파일에 해당 행 존재.

### T9.4 — 루트 `README.md` / `README.ko.md` 모듈 표 업데이트
- **complexity**: low
- **files**: `README.md`, `README.ko.md`
- **description**: 동일하게 `images-spring-boot4` 행 추가. 두 파일 구조 정렬 유지.
- **acceptance**: 두 README에 해당 행 존재.

### T9.5 — KDoc 영문화 검수
- **complexity**: low
- **files**: `images-spring-boot4/src/main/kotlin/**/*.kt`
- **description**: 모든 public API에 영어 KDoc + `## Behavior / Contract`(필요 시) 완비 점검.
  CLAUDE.md "Document Language Policy" 준수.
- **acceptance**: 모든 public class/interface/object/extension function에 영어 KDoc.

### T9.6 — `detekt` 통과 / 컴파일 / 단위 테스트 / Floci 테스트
- **complexity**: medium
- **files**: 신규 모듈 전체
- **description**:
  - `./gradlew :images-spring-boot4:detekt` 통과.
  - `./gradlew :images-spring-boot4:test` 통과.
  - Floci 가용 환경에서 `S3ImageStorageTest` 통과 보고.
  - 보고에는 pass count + 경과 시간 포함.
- **acceptance**: 모든 명령이 0-exit.

---

## Phase 10 — CI / 리뷰 / PR

### T10.1 — `.github/workflows/ci.yml` 확인 및 보정 PR (필요 시)
- **complexity**: low
- **files**: `.github/workflows/ci.yml`, `.github/workflows/nightly-tests.yml` (영향 시)
- **description**: CI workflow가 `images-spring-boot4` 모듈을 빌드/테스트하는지 확인. 누락 시 보정.
- **acceptance**: CI에서 신규 모듈 빌드/테스트 단계 존재.

### T10.2 — `oh-my-claudecode:code-reviewer` 실행
- **complexity**: medium
- **files**: 신규 모듈 전체
- **description**: code-reviewer 스킬로 코드 리뷰. **HIGH/CRITICAL 0건**까지 반복 수정.
- **acceptance**: 리뷰 보고서에 HIGH/CRITICAL 없음.

### T10.3 — PR 생성
- **complexity**: low
- **files**: GitHub PR
- **description**: 영문 title/body. test 결과 + 검증 명령(`./gradlew :images-spring-boot4:test`,
  `./gradlew :images-spring-boot4:detekt`) + rationale 포함. squash-merge 전제.
- **acceptance**: PR 등록.

---

## 의존성 그래프 (요약)

```
Phase 0 (T0.1 → T0.2 → T0.3 → T0.4)
   ↓
Phase 1 (T1.1 → T1.2, T1.3, T1.4)
   ↓
Phase 2 (T2.1, T2.2, T2.3)         [T2.3은 T1.1, T1.3에 의존]
   ↓
Phase 3 (T3.1, T3.2, T3.3)
   ↓
Phase 4 (T4.1, T4.2 → T4.3)         [T4.1/T4.2는 T2.3, T3.2 의존]
   ↓
Phase 5 (T5.1, T5.2)                [T5.1은 T3.3 의존; T5.2는 T3.2 의존]
   ↓
Phase 6 (T6.1–T6.8)                  [구현체와 함께 묶어도 OK]
   ↓
Phase 7 (T7.1 → T7.2 → T7.3, T7.4, T7.5 → T7.6)
   ↓
Phase 8 (T8.1, T8.2, T8.3, T8.4)
   ↓
Phase 9 (T9.1–T9.6)
   ↓
Phase 10 (T10.1 → T10.2 → T10.3)
```

---

## DoD 매핑 (스펙 §10)

| DoD 항목 | 매핑 Task |
|---|---|
| `ImageObjectKey` (private ctor + `of()`, path traversal) | T1.1 |
| `ImageUploadResult` | T1.2 |
| `UploadOptions` (content-type allowlist) | T1.3 |
| `ImageStorageException` (sealed hierarchy) | T1.4, T4.3 |
| `ImageStorage` (upload×2, download×2, delete, exists, list) | T2.3 |
| `CdnReadSigner`, `CdnWriteSigner` (분리) | T2.1, T2.2 |
| `LocalImageStorage` | T4.1 |
| `S3ImageStorage` | T4.2 |
| `CloudFrontUrlSigner` (CdnReadSigner만) | T5.1 |
| `S3PreSignedUrlSigner` (Read + Write) | T5.2 |
| 5개 AutoConfig phase 클래스 + `AutoConfiguration.imports` | T7.1–T7.6 |
| `afterName = [String]` 사용(P0-1) | T7.2, T7.3, T7.4, T7.5 |
| `LocalStorageConfiguration` 조건 `@ConditionalOnMissingBean`만(P0-2) | T7.2 |
| CDN Phase의 S3 빈 nested config 격리(P0-3) | T7.3 |
| 모든 구현 클래스 `companion object : KLogging()` | T4.1, T4.2, T5.1, T5.2 |
| 모든 config data class `Serializable + serialVersionUID` | T3.1, T3.2, T3.3 |
| `@PostConstruct` no-arg + 생성자 주입(C-1) | T7.2 |
| `LocalImageStorageTest` (happy/실패/path traversal/cancel/concurrency) | T6.6 |
| `S3ImageStorageTest` (Floci) | T6.7 |
| `CloudFrontUrlSignerTest` (RSA/PEM 실패/expiry/마스킹/타입체크) | T6.4, T6.5 |
| `S3PreSignedUrlSignerTest` (MockK/expiry/타입체크) | T6.8 |
| `ImagesStorageAutoConfigurationTest` (6 시나리오) | T8.2 |
| `ImagesCdnAutoConfigurationTest` (5 시나리오) | T8.3 |
| `ImagesProcessingAutoConfigurationTest`, `ImageStorageHealthIndicatorTest`, `UploadOptionsTest`, `ImageObjectKeyTest` | T8.1, T8.4, T6.3, T6.1 |
| `./gradlew :images-spring-boot4:test` 통과 | T9.6 |
| `README.md` + `README.ko.md` | T9.1, T9.2 |
| 모든 public API 영어 KDoc | T9.5 |
| `libs.versions.toml` 업데이트 | T0.1 |
| `settings.gradle.kts` 모듈 포함 | T0.2 |
| 워크스페이스/모듈 CLAUDE.md 모듈 표 | T9.3 |
| 루트 README 모듈 표 | T9.4 |
| `./gradlew :images-spring-boot4:detekt` 통과 | T9.6 |
| `code-reviewer` HIGH/CRITICAL 0 | T10.2 |
| CI workflow 포함 | T10.1 |
| PR 영문 + test 결과 + 검증 명령 + squash-merge | T10.3 |

---

## 위험 완화 체크리스트 (스펙 §2)

| Risk | 완화 방안이 적용된 Task |
|---|---|
| R1 `S3Operations` optional dep | T7.2 (nested config + `@ConditionalOnClass(name=)`), T7.3 (nested S3Presign), T7.2 (Local fallback unconditional missing-bean) |
| R2 CloudFront PEM 보안 | T3.3 (REDACTED toString), T5.1 (path 우선, zero-out, 인라인 PEM WARN, 동시 지정 IllegalStateException), T6.4/T6.5 (Actuator 마스킹 테스트) |
| R3 Floci 안정성 | T6.7 (`@Suppress("DEPRECATION")`, AbstractS3StorageTest 싱글턴), T6.6 (Floci 무관 LocalImageStorage 회귀 보호) |
| R4 Boot BOM ↔ Kotlin BOM 충돌 | T0.3 (`dependencyManagement` 임포트 순서 명시: spring-boot-dependencies → aws2-bom → kotlin-bom 2.3.x) |

---

## 비고

- 본 계획은 한국어로 작성되었다(CLAUDE.md, internal artifact 정책).
- 본 계획 적용 후 새로 작성되는 KDoc / PR / commit / GitHub issue 등은 영어로 작성한다.
- 본 계획에 등장하는 모든 코드 스니펫은 스펙 §4–§9를 그대로 인용한 것이며, 구현 시 동일 정의를 따른다.
- 의존성 관계상 구현 Task(Phase 4–5)와 단위 테스트 Task(Phase 6)는 페어로 동시에 진행해도 좋다. 단, AutoConfiguration 테스트(Phase 8)는 Phase 7 완료 후 진행한다.
