# images-spring-boot4 설계 스펙

- **문서 종류**: 내부 설계 스펙 (한국어 작성, CLAUDE.md 문서 언어 정책)
- **작성일**: 2026-05-17
- **대상 이슈**: #5 — `S3 / CDN / Spring Boot 자동 구성 통합`
- **대상 모듈(신규)**: `images-spring-boot4`
- **그룹/버전**: `io.github.bluetape4k.image` / `0.1.0-SNAPSHOT`
- **Kotlin / JVM / Spring Boot**: Kotlin 2.3 / Java 21 / Spring Boot 4.0.6

---

## 1. 배경 및 목표

### 1.1 Issue #5 요약

Issue #5 (`S3 / CDN / Spring Boot 자동 구성 통합`)는 `bluetape4k-image`가 라이브러리 레벨에서만
이미지 처리를 제공하고, 실제 서비스 환경에서 필요한 다음 세 요소가 빠져 있다는 문제 의식에서 출발한다.

1. 원본/파생 이미지를 영속화하기 위한 **객체 스토리지 통합**(S3 호환 스토리지 우선)
2. CloudFront 같은 **CDN 서명 URL 발급**
3. Spring Boot 환경에서 위 두 요소를 application property만으로 활성화할 수 있는 **자동 구성**

### 1.2 현재 문제 (반복 구현 비용)

- `bluetape4k-image`의 컨슈머 프로젝트들은 `ImmutableImage`를 그대로 다루는 데에는 무리가 없으나,
  업로드/다운로드 경로마다 `S3AsyncClient` boilerplate를 직접 작성하고 있다.
- CloudFront 서명 URL을 발급하는 코드는 `CloudFrontUtilities` + PEM 파싱 + 만료 시각 처리가 매번 비슷한 형태로
  복사·붙여넣기 되고 있다.
- Spring Boot 환경에서 `S3AutoConfiguration` (bluetape4k-aws-spring-boot)과 이미지 처리 코드를 잇는 어댑터
  계층이 표준화되어 있지 않다.

### 1.3 목표

- `images` 모듈은 **순수 이미지 처리 라이브러리**로 유지하고, **Spring Boot/S3/CDN 통합은 신규 모듈
  `images-spring-boot4`** 에서 책임진다.
- Spring Boot 4 자동 구성 패턴(여러 phase 클래스 + `AutoConfiguration.imports`)을 그대로 따른다.
- `bluetape4k-aws-spring-boot`의 `S3Operations`를 1차 백엔드로 사용한다. `S3Operations`가 존재하지 않는
  환경에서는 로컬 파일시스템 fallback (`LocalImageStorage`)으로 동작한다.
- CloudFront URL 서명은 PEM 키 1회 파싱 + `CloudFrontUtilities` 위임으로 단순화한다.
- 모든 외부 호출은 코루틴 기반 `suspend` API로 노출한다.

### 1.4 비목표

- 이미지 변환·필터·압축 자체는 `images` 모듈 책임이며 본 스펙의 대상이 아니다.
- AWS 외 CDN (Cloudflare, Fastly, Akamai)은 본 phase의 대상이 아니다. 추후 별도 phase로 확장 가능한
  형태(`CdnReadSigner` 인터페이스)로만 설계한다.
- Spring Boot 3 호환은 본 모듈의 목표가 아니다. (네이밍이 `images-spring-boot4`인 이유)

### 1.5 소비자 책임 (AuthZ 정책)

본 모듈은 인증(AuthN) 없이 `key`를 기반으로 저장·서명만 수행한다. **어떤 key에 어떤 호출자가 접근 가능한지의
인가(AuthZ) 검증은 호출자 책임이다.** `ImageStorage`/`CdnReadSigner`/`CdnWriteSigner` 호출 전에
Spring Security, 사내 정책 등으로 key 접근 권한을 검증해야 한다.

---

## 2. 설계 위험 요소

### 2.1 Risk 1 — `S3Operations`가 optional dependency라는 점

- **현상**: `bluetape4k-aws-spring-boot`의 `S3Operations`는 `compileOnly`로 선언된다. 컨슈머가
  해당 라이브러리를 추가하지 않으면 `S3Operations` 클래스 자체가 classpath에 존재하지 않는다.
- **영향**: `S3ImageStorage`/`S3PreSignedUrlSigner`를 그냥 `@Bean`으로 등록하면 자동 구성 시점에
  `NoClassDefFoundError`가 발생한다.
- **완화**:
  - `@ConditionalOnClass(name = ["io.bluetape4k.aws.spring.boot.S3Operations"])` string FQCN 사용.
  - `S3Operations`를 파라미터로 받는 `@Bean` 메서드는 반드시 nested `@Configuration` 안에만 배치
    (최상위 `@AutoConfiguration` 진입 클래스에 직접 `S3Operations` 타입 참조 금지).
  - S3 config가 조건 불충족 시 `LocalImageStorage`가 `@ConditionalOnMissingBean(ImageStorage)` 패턴으로
    항상 fallback된다. `backend=s3`를 설정했더라도 S3Operations가 없으면 Local로 자동 대체.

### 2.2 Risk 2 — CloudFront 개인키 보안

- **현상**: CloudFront URL 서명에는 PEM 형식의 RSA 개인키가 필요하다.
- **영향**:
  - YAML literal 방식은 git commit, 로그, `/actuator/configprops`, `/actuator/env`에 노출될 가능성이 높다.
  - `String`은 JVM 힙·intern pool에 잔류하므로 zero-out 불가.
- **완화**:
  - **권장**: `cdn.cloudfront.private-key-path` (파일 경로). 컨테이너 secret/HSM/KMS와 호환.
  - **허용하지만 경고**: `cdn.cloudfront.private-key-pem` (인라인 PEM). `WARN` 로그 + **Actuator 노출 차단**
    (`toString()` redact + `SanitizingFunction`/`SanitizableData` 등록).
  - 두 속성을 동시 지정하면 `IllegalStateException("Specify either private-key-path or private-key-pem, not both.")`으로 fail-fast.
  - 오류 메시지에서 PEM 값/파일 내용을 절대 echo하지 않는다. 경로/지문(SHA-256)만 표시.
  - `CloudFrontUrlSigner` 생성자에서 `private-key-path`로 읽은 PEM byte 배열은 `RSAPrivateKey` 파싱 후
    `Arrays.fill(bytes, 0)`로 zero-out. `private-key-pem` String 경로는 JVM 특성상 zero-out 불가이므로
    **인라인 PEM 사용 자체를 권장하지 않는 추가 사유**가 된다.
  - `CdnProperties.CloudFront.toString()`을 override하여 `privateKeyPem`을 `"[REDACTED]"`로 마스킹.

### 2.3 Risk 3 — Floci 테스트 컨테이너의 안정성

- **현상**: `bluetape4k-testcontainers`의 `FlociServer`는 초기 단계로 `@Deprecated(WARNING)` 상태.
- **완화** (사용자 확정):
  - `FlociServer.Launcher.floci` 싱글턴 패턴 사용, 모든 사용처에서 `@Suppress("DEPRECATION")` 명시.
  - `AbstractS3StorageTest` 베이스 클래스에서 1회만 노출.
  - `LocalImageStorageTest`는 외부 컨테이너 없이 독립적으로 동작하여 Floci 불안정성과 무관하게 회귀 보호.

### 2.4 Risk 4 — Spring Boot BOM vs Kotlin BOM 충돌

- **현상**: Spring Boot 4.0.6 BOM은 Kotlin 2.2.x를 고정한다.
- **완화**: `dependencyManagement`에서 spring-boot-dependencies BOM **이후** kotlin-bom 2.3.x를 명시 임포트.

---

## 3. 설계 접근법 비교

### 3.1 Approach A — `ImageStorage`/`CdnReadSigner` 인터페이스를 `images` 모듈에 배치

- **거절 이유**: `images` 모듈의 책임 경계(scrimage 기반 변환·필터·인코딩)에 객체 스토리지 시맨틱을 끌어들이면
  단일 책임 원칙이 약화된다. `images`는 `bluetape4k-aws-spring-boot` 의존성 없는 상태로 유지해야 한다.

### 3.2 Approach B — `ImageStorage`/`CdnReadSigner` 인터페이스를 `images-spring-boot4`에 배치 (**선택**)

- 추상 + 구현(`LocalImageStorage`, `S3ImageStorage`, `S3PreSignedUrlSigner`, `CloudFrontUrlSigner`)이 동일 모듈.
- **선택 이유**: `images` 모듈 책임 경계 보호, 객체 스토리지/CDN 시맨틱 고응집, 자동 구성 phase 클래스와
  인터페이스/구현이 한 모듈로 단순화.

### 3.3 Approach C — `S3Operations` 대신 `S3AsyncClient`를 직접 사용

- **거절 이유**: `S3Operations`가 이미 코루틴 친화적 wrapper, presign helper, 예외 변환, 메트릭 hook을 제공.
  우회 시 중복 구현 → DRY 위반.

**최종 선택**: Approach B + `S3Operations` 위임.

---

## 4. API 설계 (확정)

> 모든 public 타입은 **English KDoc**을 작성한다 (CLAUDE.md). 본 스펙에서는 한국어 설명만 동행한다.
>
> **구현 불변 규칙**: 모든 `ImageStorage`/`CdnReadSigner`/`CdnWriteSigner` 구현체는 다음을 준수한다.
> - 모든 구현 클래스에 `companion object : KLogging()` 선언.
> - `suspend` 함수에서 넓은 예외 catch 전에 `CancellationException`을 먼저 rethrow.
> - `suspend` 함수 내부에서 `runCatching {}` 사용 금지 (CLAUDE.md).
> - `@Synchronized`/`synchronized {}` 사용 금지; 필요시 `reentrantLock()`.

### 4.1 값 객체

#### 4.1.1 `ImageObjectKey`

```kotlin
data class ImageObjectKey private constructor(
    val prefix: String,
    val name: String,
) : java.io.Serializable {

    val fullKey: String
        get() {
            val p = if (prefix.endsWith("/")) prefix else "$prefix/"
            return "$p$name"
        }

    companion object {
        private const val serialVersionUID: Long = 1L

        private val VALID_SEGMENT = Regex("^[A-Za-z0-9._/-]+$")

        /**
         * Creates a validated [ImageObjectKey].
         *
         * @throws IllegalArgumentException if prefix or name is blank, contains `..`, or
         *   contains characters outside `[A-Za-z0-9._/-]`.
         */
        fun of(prefix: String, name: String): ImageObjectKey {
            prefix.requireNotBlank("prefix")
            name.requireNotBlank("name")
            require(!prefix.contains("..") && !name.contains("..")) {
                "prefix and name must not contain '..' segments"
            }
            require(VALID_SEGMENT.matches(prefix) && VALID_SEGMENT.matches(name)) {
                "prefix and name must match [A-Za-z0-9._/-]+"
            }
            return ImageObjectKey(prefix, name)
        }
    }
}
```

- 생성자를 `private`으로 선언하여 `of()` factory를 통해서만 생성 가능하게 한다.
- Path traversal 방지: `..` 포함 및 allowlist 외 문자 거부.
- `fullKey`에서 double-slash가 생기지 않도록 prefix 정규화.
- `Serializable` + `serialVersionUID` 명시 (CLAUDE.md 데이터 클래스 규칙).
- `keyPrefix` (저장소 전역 prefix) + `ImageObjectKey` 조합은 `ImagesStorageAutoConfiguration`이 담당;
  `ImageObjectKey` 자체는 prefix 조합 책임이 없다.

#### 4.1.2 `ImageUploadResult`

```kotlin
data class ImageUploadResult(
    val key: ImageObjectKey,
    val eTag: String?,
    val sizeBytes: Long,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

#### 4.1.3 `ImageStorageException` (sealed hierarchy)

```kotlin
sealed class ImageStorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class NotFoundException(key: ImageObjectKey, cause: Throwable? = null)
        : ImageStorageException("Object not found: ${key.fullKey}", cause)

    class AccessDeniedException(key: ImageObjectKey, cause: Throwable? = null)
        : ImageStorageException("Access denied: ${key.fullKey}", cause)

    class ConflictException(key: ImageObjectKey, cause: Throwable? = null)
        : ImageStorageException("Conflict on key: ${key.fullKey}", cause)

    class TransientException(message: String, cause: Throwable? = null)
        : ImageStorageException(message, cause)

    class ValidationException(message: String, cause: Throwable? = null)
        : ImageStorageException(message, cause)

    companion object {
        fun wrap(key: ImageObjectKey, e: Throwable): ImageStorageException = when (e) {
            is ImageStorageException -> e
            // SdkException 분류 로직 (구현 시 S3Exception.statusCode() 참고)
            else -> TransientException("Storage operation failed for ${key.fullKey}", e)
        }
    }
}
```

- sealed class로 복구 가능 여부를 컴파일 타임에 구분.
- 메시지에 PEM/서명 값이 포함되지 않도록 `fullKey`만 사용.
- `LocalImageStorage`의 `IOException`도 이 계층으로 래핑한다.

#### 4.1.4 `UploadOptions`

```kotlin
data class UploadOptions(
    val contentType: String,
    val cacheControl: String? = null,
    val contentDisposition: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) : java.io.Serializable {
    init {
        contentType.requireNotBlank("contentType")
        require(contentType in ALLOWED_CONTENT_TYPES) {
            "contentType '$contentType' is not in the allowed list: $ALLOWED_CONTENT_TYPES"
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L

        val ALLOWED_CONTENT_TYPES: Set<String> = setOf(
            "image/jpeg", "image/png", "image/webp",
            "image/gif", "image/avif", "image/heic",
        )
    }
}
```

- content-type allowlist를 `UploadOptions` 생성 시 검증하여 stored XSS 방지.
- SVG는 CDN 통해 서빙 시 XSS 위험이 있으므로 기본 allowlist에서 제외. 필요 시 문서화 후 확장.

### 4.2 `ImageStorage` 인터페이스

```kotlin
interface ImageStorage {
    /** 바이트 배열 업로드. 대용량 이미지는 [upload(key, source, options)] 오버로드를 사용한다. */
    suspend fun upload(
        key: ImageObjectKey,
        bytes: ByteArray,
        options: UploadOptions,
    ): ImageUploadResult

    /** 파일 경로에서 스트리밍 업로드. Dispatcher.IO 내에서 멀티파트/청크 처리. */
    suspend fun upload(
        key: ImageObjectKey,
        source: Path,
        options: UploadOptions,
    ): ImageUploadResult

    /** 바이트 배열 다운로드. [maxDownloadBytes] 초과 시 [ImageStorageException.ValidationException]. */
    suspend fun download(key: ImageObjectKey): ByteArray

    /** 파일 경로로 스트리밍 다운로드. 대용량 이미지에 적합. */
    suspend fun download(key: ImageObjectKey, destination: Path)

    /** 삭제. 키가 없어도 idempotent (예외 없음). */
    suspend fun delete(key: ImageObjectKey)

    /** 존재 여부. 인증 오류는 [ImageStorageException.AccessDeniedException] throw. */
    suspend fun exists(key: ImageObjectKey): Boolean

    /** 주어진 prefix로 시작하는 키 목록. 페이지네이션 불필요 시 `Flow` 소비 후 `toList()`. */
    fun list(prefix: String): Flow<ImageObjectKey>
}
```

#### 구현체

**`LocalImageStorage(rootDir: Path)`**

```kotlin
class LocalImageStorage(private val rootDir: Path) : ImageStorage {
    companion object : KLogging()

    init {
        Files.createDirectories(rootDir)
    }
    // 모든 suspend 함수: withContext(Dispatchers.IO) 사용
    // key.fullKey 해석 시: rootDir.resolve(key.fullKey).normalize() 후
    //   normalize().startsWith(rootDir.normalize()) 검증 → 실패 시 ValidationException
    // IOException은 ImageStorageException.TransientException으로 래핑
    // CancellationException은 모든 catch 전에 먼저 rethrow
    // upload 실패 시 partial file best-effort delete
}
```

**`S3ImageStorage(operations: S3Operations, properties: ImageStorageProperties)`**

```kotlin
class S3ImageStorage(
    private val operations: S3Operations,
    private val properties: ImageStorageProperties,
) : ImageStorage {
    companion object : KLogging()

    init {
        properties.bucket.requireNotBlank("bucket")
        require(properties.maxSizeBytes > 0) { "maxSizeBytes must be positive" }
    }
    // SdkException → ImageStorageException.wrap(key, e)
    // CancellationException 먼저 rethrow
    // upload 실패 시 S3 multipart abort best-effort
    // 다운로드 시 properties.maxSizeBytes 초과 → ValidationException
}
```

#### 업로드 원자성 계약

- `upload()` 호출자는 성공 시 `ImageUploadResult`를, 실패 시 `ImageStorageException`을 받는다.
- 실패 시 구현체는 best-effort 정리(부분 파일 삭제, multipart abort)를 시도하지만 보장하지는 않는다.
- 완전한 원자성이 필요한 경우 호출자가 멱등 업로드 패턴을 사용한다.

### 4.3 CDN URL 서명 인터페이스 (분리)

#### `CdnReadSigner`

```kotlin
interface CdnReadSigner {
    /**
     * 서명된 GET URL을 반환한다.
     *
     * @param expiresIn 양수여야 하며 구현체별 최대값(S3: 7일, CloudFront: 제한 없음) 이내.
     * @throws IllegalArgumentException expiresIn ≤ 0
     * @throws ImageStorageException 서명 실패
     */
    suspend fun signGet(key: ImageObjectKey, expiresIn: Duration): URI
}
```

#### `CdnWriteSigner`

```kotlin
interface CdnWriteSigner {
    /**
     * 서명된 PUT URL을 반환한다.
     *
     * @throws ImageStorageException 서명 실패
     */
    suspend fun signPut(key: ImageObjectKey, expiresIn: Duration, options: UploadOptions): URI
}
```

> `CdnReadSigner`와 `CdnWriteSigner`를 분리하여 `CloudFrontUrlSigner`가 `CdnReadSigner`만 구현하도록 한다.
> 호출자는 컴파일 타임에 PUT 지원 여부를 인지할 수 있다.

#### 구현체

**`CloudFrontUrlSigner(properties: CdnProperties.CloudFront)` — `CdnReadSigner` 구현**

```kotlin
class CloudFrontUrlSigner(properties: CdnProperties.CloudFront) : CdnReadSigner {
    companion object : KLogging()

    init {
        properties.distributionDomain.requireNotBlank("distributionDomain")
        require(properties.distributionDomain!!.startsWith("https://") ||
                !properties.distributionDomain.contains("/")) {
            "distributionDomain must be a bare hostname (e.g., d123.cloudfront.net), not a URL path"
        }
        properties.keyPairId.requireNotBlank("keyPairId")
        require(properties.maxExpiry.isPositive()) { "maxExpiry must be positive" }
    }

    // signGet: expiresIn > 0 검증, expiresIn ≤ properties.maxExpiry 검증
    // CloudFrontUtilities.getSignedUrlWithCannedPolicy 사용
    // 이 메서드는 순수 CPU 작업이므로 Dispatchers.IO hop 없음
    // CancellationException 먼저 rethrow
}
```

**`S3PreSignedUrlSigner(operations: S3Operations, properties: ImageStorageProperties)` — `CdnReadSigner` + `CdnWriteSigner` 구현**

```kotlin
class S3PreSignedUrlSigner(
    private val operations: S3Operations,
    private val properties: ImageStorageProperties,
) : CdnReadSigner, CdnWriteSigner {
    companion object : KLogging()

    init {
        properties.bucket.requireNotBlank("bucket")
    }
    // signGet/signPut: expiresIn > 0 검증, S3 SigV4 max 7일 검증
    // operations.presignGet/presignPut 위임
    // CancellationException 먼저 rethrow
}
```

### 4.4 패키지 구조

```
io.bluetape4k.images.spring
├── ImageObjectKey           (of() factory, path traversal validation)
├── ImageUploadResult
├── UploadOptions            (content-type allowlist)
├── ImageStorageException    (sealed: NotFoundException, AccessDeniedException,
│                              ConflictException, TransientException, ValidationException)
├── ImageStorage             (upload×2, download×2, delete, exists, list)
├── CdnReadSigner            (signGet)
├── CdnWriteSigner           (signPut)
├── storage
│   ├── LocalImageStorage    (CdnReadSigner 미구현)
│   └── S3ImageStorage
├── cdn
│   ├── CloudFrontUrlSigner  (CdnReadSigner만)
│   └── S3PreSignedUrlSigner (CdnReadSigner + CdnWriteSigner)
└── autoconfigure
    ├── ImagesProcessingAutoConfiguration
    ├── ImagesStorageAutoConfiguration
    ├── ImagesCdnAutoConfiguration
    ├── ImagesHealthAutoConfiguration
    ├── ImageProcessingProperties
    ├── ImageStorageProperties
    └── CdnProperties
```

---

## 5. 설정 속성 스키마

### 5.1 ConfigurationProperties 클래스

#### 5.1.1 `ImageProcessingProperties`

```kotlin
@ConfigurationProperties(prefix = "bluetape4k.images.processing")
data class ImageProcessingProperties(
    val enabled: Boolean = true,
    val defaultQuality: Int = 85,
) : java.io.Serializable {
    init {
        require(defaultQuality in 1..100) { "defaultQuality must be in 1..100" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

#### 5.1.2 `ImageStorageProperties`

```kotlin
@ConfigurationProperties(prefix = "bluetape4k.images.storage")
data class ImageStorageProperties(
    val enabled: Boolean = true,
    val backend: Backend = Backend.LOCAL,
    val bucket: String? = null,
    val keyPrefix: String = "",
    val maxSizeBytes: Long = 50 * 1024 * 1024L,  // 50 MB default
    val local: Local = Local(),
    val s3: S3 = S3(),
) : java.io.Serializable {

    enum class Backend { LOCAL, S3 }

    data class Local(
        /** 기본값은 JVM 재시작 시 사라질 수 있다. 영구 저장이 필요하면 명시적으로 지정한다. */
        val rootDir: String = System.getProperty("java.io.tmpdir") + "/bluetape4k-images",
    ) : java.io.Serializable {
        companion object { private const val serialVersionUID: Long = 1L }
    }

    data class S3(
        val callTimeout: Duration = Duration.ofSeconds(30),
        val attemptTimeout: Duration = Duration.ofSeconds(10),
        val maxRetries: Int = 3,
        val maxInFlight: Int = 64,
    ) : java.io.Serializable {
        companion object { private const val serialVersionUID: Long = 1L }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

- `backend = S3`이면 `bucket`은 필수 — `ImagesStorageAutoConfiguration` 시작 시 `bucket.requireNotBlank()`로 fail-fast.
- `maxSizeBytes` 초과 시 업로드/다운로드에서 `ValidationException`.
- `s3.callTimeout`/`attemptTimeout`은 `S3Operations` 요청 오버라이드 또는 SDK `overrideConfiguration`으로 전달.

#### 5.1.3 `CdnProperties`

```kotlin
@ConfigurationProperties(prefix = "bluetape4k.images.cdn")
data class CdnProperties(
    val enabled: Boolean = false,
    val provider: Provider = Provider.S3_PRESIGN,
    val cloudfront: CloudFront = CloudFront(),
) : java.io.Serializable {

    enum class Provider { S3_PRESIGN, CLOUDFRONT }

    data class CloudFront(
        val distributionDomain: String? = null,
        val keyPairId: String? = null,
        val privateKeyPath: String? = null,
        /** 권장하지 않음. Actuator 노출 차단 및 JVM 힙 잔류로 zero-out 불가. */
        val privateKeyPem: String? = null,
        val defaultExpiry: Duration = Duration.ofMinutes(10),
        /** signGet expiresIn 상한. 이를 초과하면 IllegalArgumentException. */
        val maxExpiry: Duration = Duration.ofHours(1),
    ) : java.io.Serializable {

        /** Actuator/toString 노출 방지: privateKeyPem을 REDACTED 처리. */
        override fun toString(): String =
            "CloudFront(domain=$distributionDomain, keyPairId=$keyPairId, " +
            "privateKeyPath=$privateKeyPath, privateKeyPem=[REDACTED], " +
            "defaultExpiry=$defaultExpiry, maxExpiry=$maxExpiry)"

        companion object { private const val serialVersionUID: Long = 1L }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

> **Actuator 보안**: `CdnProperties.CloudFront`를 `SanitizingFunction`에 등록하거나
> `@SensitiveEndpoint` 설정을 통해 `/actuator/configprops`에서 `privateKeyPem` 값이 노출되지 않도록 한다.

### 5.2 YAML 예시

**권장 설정** (`private-key-path` 사용):

```yaml
bluetape4k:
  images:
    processing:
      enabled: true
      default-quality: 85
    storage:
      enabled: true
      backend: s3
      bucket: my-image-bucket
      key-prefix: prod/images
      max-size-bytes: 52428800  # 50 MB
      s3:
        call-timeout: PT30S
        attempt-timeout: PT10S
        max-retries: 3
      local:
        root-dir: /var/data/images
    cdn:
      enabled: true
      provider: cloudfront
      cloudfront:
        distribution-domain: d123.cloudfront.net
        key-pair-id: K2EXAMPLEKEYPAIR
        private-key-path: /run/secrets/cloudfront-pk.pem
        default-expiry: PT10M
        max-expiry: PT1H
```

**비권장 (개발 환경 한정)**: `private-key-pem` 인라인 설정

> ⚠️ **SECURITY WARNING**: 인라인 PEM은 git commit, Actuator endpoint, 로그에 노출될 위험이 있다.
> 개발 환경에서만 사용하고 프로덕션에서는 `private-key-path`를 사용한다.

```yaml
# 개발 환경 전용 — 프로덕션 사용 금지
bluetape4k:
  images:
    cdn:
      cloudfront:
        private-key-pem: |
          -----BEGIN RSA PRIVATE KEY-----
          ...
```

### 5.3 비활성화 경로

```yaml
# 전체 비활성화
bluetape4k.images.storage.enabled: false
bluetape4k.images.cdn.enabled: false   # cdn은 기본값이 false이므로 명시 불필요
bluetape4k.images.processing.enabled: false
```

---

## 6. AutoConfig 설계

### 6.1 Phase 클래스

> **중요 (P0-1)**: Spring Boot 4의 `@AutoConfiguration` 어노테이션에서 String FQCN 정렬에는
> `after` 파라미터가 아닌 **`afterName`** 파라미터를 사용한다.
> (`after`는 `KClass[]`, `afterName`은 `String[]`)
>
> Codex가 `javap`로 확인한 Boot 4.0.3 소스:
> ```
> public abstract java.lang.String[] afterName();
> ```

#### Phase 1 — `ImagesProcessingAutoConfiguration`

```kotlin
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "bluetape4k.images.processing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(ImageProcessingProperties::class)
class ImagesProcessingAutoConfiguration
// placeholder — 추후 ImageProcessor/ImagePipeline 빈 등록
```

#### Phase 2 — `ImagesStorageAutoConfiguration`

```kotlin
@AutoConfiguration(
    afterName = [
        "io.bluetape4k.aws.spring.boot.autoconfigure.S3AutoConfiguration",
        "io.bluetape4k.images.spring.autoconfigure.ImagesProcessingAutoConfiguration",
    ],
)
@ConditionalOnProperty(
    prefix = "bluetape4k.images.storage",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(ImageStorageProperties::class)
class ImagesStorageAutoConfiguration {

    /**
     * S3 backend. S3Operations와 backend=s3가 모두 있을 때만 활성화.
     * S3Operations 타입이 compileOnly이므로 반드시 nested @Configuration으로 격리.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["io.bluetape4k.aws.spring.boot.S3Operations"])
    @ConditionalOnProperty(
        prefix = "bluetape4k.images.storage",
        name = ["backend"],
        havingValue = "s3",
    )
    class S3StorageConfiguration {

        @PostConstruct  // 또는 InitializingBean
        fun validateBucket(properties: ImageStorageProperties) {
            properties.bucket.requireNotBlank("bluetape4k.images.storage.bucket (required when backend=s3)")
        }

        @Bean
        @ConditionalOnMissingBean(ImageStorage::class)
        fun s3ImageStorage(
            operations: S3Operations,
            properties: ImageStorageProperties,
        ): ImageStorage = S3ImageStorage(operations, properties)
    }

    /**
     * Local fallback. backend=s3이더라도 S3Operations가 없거나 ImageStorage 빈이 없으면 자동 등록.
     * @ConditionalOnMissingBean(ImageStorage) 덕분에 S3가 있으면 적용되지 않는다.
     */
    @Configuration(proxyBeanMethods = false)
    class LocalStorageConfiguration {

        @Bean
        @ConditionalOnMissingBean(ImageStorage::class)
        fun localImageStorage(properties: ImageStorageProperties): ImageStorage =
            LocalImageStorage(Path.of(properties.local.rootDir))
    }
}
```

> **P0-2 해결**: `LocalStorageConfiguration`에서 `@ConditionalOnProperty(backend=local)` 조건을 제거하고
> `@ConditionalOnMissingBean(ImageStorage)` 만으로 fallback을 보장한다.
> 이로써 `backend=s3`이지만 S3Operations가 없는 환경에서도 LocalImageStorage가 등록된다.

#### Phase 3 — `ImagesCdnAutoConfiguration`

```kotlin
@AutoConfiguration(
    afterName = ["io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration"],
)
@ConditionalOnProperty(
    prefix = "bluetape4k.images.cdn",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(CdnProperties::class)
class ImagesCdnAutoConfiguration {

    /**
     * S3PreSignedUrlSigner: S3Operations가 compileOnly이므로 nested @Configuration으로 격리.
     * (P0-3: 최상위 @AutoConfiguration 클래스에 S3Operations 타입 직접 노출 금지)
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["io.bluetape4k.aws.spring.boot.S3Operations"])
    @ConditionalOnProperty(
        prefix = "bluetape4k.images.cdn",
        name = ["provider"],
        havingValue = "s3_presign",
        matchIfMissing = true,
    )
    class S3PresignCdnConfiguration {

        @Bean
        @ConditionalOnMissingBean(CdnReadSigner::class)
        fun s3PreSignedUrlSigner(
            operations: S3Operations,
            storageProperties: ImageStorageProperties,
        ): S3PreSignedUrlSigner = S3PreSignedUrlSigner(operations, storageProperties)
        // S3PreSignedUrlSigner는 CdnReadSigner + CdnWriteSigner 모두 구현
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["software.amazon.awssdk.services.cloudfront.CloudFrontUtilities"])
    @ConditionalOnProperty(
        prefix = "bluetape4k.images.cdn",
        name = ["provider"],
        havingValue = "cloudfront",
    )
    class CloudFrontCdnConfiguration {

        @Bean
        @ConditionalOnMissingBean(CdnReadSigner::class)
        fun cloudFrontUrlSigner(properties: CdnProperties): CloudFrontUrlSigner =
            CloudFrontUrlSigner(properties.cloudfront)
        // CloudFrontUrlSigner는 CdnReadSigner만 구현
    }
}
```

> **maintainer note**: `@AutoConfiguration(afterName=[String])` 은 compileOnly 의존성의 FQCN 문자열을 사용한다.
> `after=[KClass]`로 바꾸면 classpath에 없을 때 `NoClassDefFoundError`. 절대 변경 금지.

#### Phase 4 — `ImagesHealthAutoConfiguration`

```kotlin
@AutoConfiguration(
    afterName = ["io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration"],
)
@ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
@ConditionalOnProperty(
    prefix = "bluetape4k.images.health",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class ImagesHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = ["imageStorageHealthIndicator"])
    fun imageStorageHealthIndicator(storage: ImageStorage): HealthIndicator =
        ImageStorageHealthIndicator(storage)
}
```

- `ImageStorageHealthIndicator`: `exists(sentinelKey)`를 통한 probe 또는 S3 headBucket.
- probe key는 `ImageStorageProperties.healthProbeKey` 속성으로 커스터마이즈 가능 (기본: `.health-probe`).

#### Phase 5 — `ImagesMetricsAutoConfiguration`

```kotlin
@AutoConfiguration(
    afterName = ["io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration"],
)
@ConditionalOnClass(name = ["io.micrometer.core.instrument.MeterRegistry"])
class ImagesMetricsAutoConfiguration {
    // MeterRegistry 있으면 ImageStorage를 Timer/Counter 데코레이터로 래핑
    // images.storage.upload.duration, images.storage.upload.errors,
    // images.storage.download.duration, images.cdn.sign.duration 등
}
```

### 6.2 `AutoConfiguration.imports`

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
io.bluetape4k.images.spring.autoconfigure.ImagesProcessingAutoConfiguration
io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration
io.bluetape4k.images.spring.autoconfigure.ImagesCdnAutoConfiguration
io.bluetape4k.images.spring.autoconfigure.ImagesHealthAutoConfiguration
io.bluetape4k.images.spring.autoconfigure.ImagesMetricsAutoConfiguration
```

### 6.3 `@ConditionalOnClass` 전략

- 모든 외부 타입(`S3Operations`, `S3AsyncClient`, `CloudFrontUtilities`, `HealthIndicator`, `MeterRegistry`)은
  **string FQCN**으로 참조.
- `compileOnly` 타입이 파라미터·반환값으로 등장하는 `@Bean` 메서드는 반드시 **nested `@Configuration`** 안에서만
  선언한다. 최상위 `@AutoConfiguration` 클래스에 직접 `compileOnly` 타입 참조 금지.

### 6.4 AutoConfig 우선순위 다이어그램

```
ImagesProcessingAutoConfiguration
          ↓ (afterName)
ImagesStorageAutoConfiguration
          ↓ (afterName)
    ┌─────────────────┐
    │ ImagesCdnAuto   │
    │ ImagesHealthAuto│
    │ ImagesMetrics   │
    └─────────────────┘
```

- 사용자가 `@Bean ImageStorage`를 직접 정의하면 `@ConditionalOnMissingBean`으로 자동 등록이 건너뛰어진다.

---

## 7. 빌드 설계

### 7.1 `images-spring-boot4/build.gradle.kts` (요약)

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    // kotlin-noarg는 Spring Boot 4 constructor binding이 noarg 없이 동작하므로 제거
    alias(libs.plugins.spring.boot) apply false
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.boot.dependencies.get().toString())
        mavenBom(libs.aws2.bom.get().toString())
        mavenBom("org.jetbrains.kotlin:kotlin-bom:${libs.versions.kotlin.get()}")
    }
}

dependencies {
    implementation(project(":images"))  // api → implementation: ImageStorage는 ImmutableImage 미노출

    // Spring Boot
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.micrometer.core)   // optional metrics
    compileOnly(libs.spring.boot.actuator)  // optional health
    annotationProcessor(libs.spring.boot.configuration.processor)

    // AWS / bluetape4k-aws (optional)
    compileOnly(libs.bluetape4k.aws.spring.boot)
    compileOnly(libs.bluetape4k.aws)
    compileOnly(libs.aws2.s3)
    compileOnly(libs.aws2.cloudfront)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.bluetape4k.aws.spring.boot)
    testImplementation(libs.bluetape4k.aws)
    testImplementation(libs.aws2.s3)
    testImplementation(libs.aws2.cloudfront)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.spring.boot.actuator)
}
```

> **변경점**: `api(project(":images"))` → `implementation`. `ImageStorage` 공개 API가 `ImmutableImage`를
> 파라미터·반환값으로 노출하지 않으므로 전이 의존성이 불필요하다.
> `kotlin-noarg` 제거: Spring Boot 4 `@ConfigurationProperties` data class는 constructor binding으로 동작.

### 7.2 `settings.gradle.kts`

```kotlin
includeModules("images-spring-boot4")
```

### 7.3 `gradle/libs.versions.toml` 추가 목록

#### `[versions]` 추가

```toml
aws2 = "2.44.5"
spring-boot = "4.0.6"
bluetape4k-aws = "0.1.0-SNAPSHOT"
```

#### `[libraries]` 추가

```toml
aws2-bom = { module = "software.amazon.awssdk:bom", version.ref = "aws2" }
aws2-s3 = { module = "software.amazon.awssdk:s3" }
aws2-cloudfront = { module = "software.amazon.awssdk:cloudfront" }

bluetape4k-aws = { module = "io.github.bluetape4k.aws:bluetape4k-aws", version.ref = "bluetape4k-aws" }
bluetape4k-aws-spring-boot = { module = "io.github.bluetape4k.aws:bluetape4k-aws-spring-boot", version.ref = "bluetape4k-aws" }
bluetape4k-testcontainers = { module = "io.github.bluetape4k:bluetape4k-testcontainers", version.ref = "bluetape4k" }

spring-boot-dependencies = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }
spring-boot-autoconfigure = { module = "org.springframework.boot:spring-boot-autoconfigure", version.ref = "spring-boot" }
spring-boot-actuator = { module = "org.springframework.boot:spring-boot-actuator", version.ref = "spring-boot" }
spring-boot-configuration-processor = { module = "org.springframework.boot:spring-boot-configuration-processor", version.ref = "spring-boot" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test", version.ref = "spring-boot" }
```

#### `[plugins]` 추가

```toml
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
```

---

## 8. 테스트 전략

### 8.1 공통 규칙 (CLAUDE.md)

- JUnit 5 + MockK + `bluetape4k-assertions` + Testcontainers 싱글턴.
- 모든 테스트 베이스 클래스에 `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`.
- suspend 테스트는 `runTest` 또는 bluetape4k의 `runSuspendIO` 사용.
- 예외 단언은 `assertFailsWith<T> { }`; suspend 전용 패턴은 `coInvoking { ... } shouldThrow T::class`.
- `@Testcontainers` 어노테이션은 일반적으로 불필요 (싱글턴 Launcher 사용).
- `src/test/resources/junit-platform.properties`, `logback-test.xml` 포함.

### 8.2 `LocalImageStorageTest`

Happy path:
- `@TempDir`으로 `rootDir` 주입.
- `runTest` 안에서 `upload → exists → download → delete → exists(false)` 검증.
- `list(prefix)` 결과 검증.

실패 경로:
- 존재하지 않는 key `download` → `ImageStorageException.NotFoundException`.
- `delete` 없는 key → 예외 없이 idempotent.
- Path traversal 시도 (`ImageObjectKey.of("foo", "../etc/passwd")`) → `IllegalArgumentException` in `of()`.
- `rootDir` 바깥 경로 직접 resolve 시도 → `ValidationException`.

취소 테스트:
- `Job.cancel()` 후 `upload` join → `CancellationException` propagation 확인.

동시성 테스트:
- 동일 key에 N개 코루틴이 동시 `upload` → 하나만 최종 저장되거나 all succeed (last-write-wins 문서화).

파일 스트리밍:
- 큰 파일(`maxSizeBytes` 초과) `Path` upload → `ValidationException`.
- 정상 크기 `Path` upload → `download(destination: Path)` 검증.

### 8.3 `S3ImageStorageTest`

```kotlin
@file:Suppress("DEPRECATION")
abstract class AbstractS3StorageTest {
    companion object : KLogging() {
        val floci = FlociServer.Launcher.floci
    }
}
```

Happy path:
- bucket 생성 (`floci.s3.createBucket(...)`) → `runSuspendIO`로 `upload/download/delete/exists/list` 검증.

실패 경로:
- 존재하지 않는 bucket → `ImageStorageException` (TransientException or AccessDeniedException).
- 존재하지 않는 key `download` → `NotFoundException`.
- `delete` 없는 key → idempotent.
- `maxSizeBytes` 초과 upload → `ValidationException`.

취소 테스트:
- `withTimeout(1.milliseconds) { largeUpload() }` → `TimeoutCancellationException` propagation.

### 8.4 `CloudFrontUrlSignerTest`

- 에페머럴 RSA 키페어: `KeyPairGenerator.getInstance("RSA").generateKeyPair()`.
- PEM 직렬화 → `CdnProperties.CloudFront` 주입.
- `signGet`: URI scheme=`https`, host==`distributionDomain`, query에 `Key-Pair-Id`, `Expires`, `Signature` 포함.
- 만료 시각: `Instant.now() + expiresIn` ±5초 범위.
- `expiresIn ≤ 0` → `IllegalArgumentException`.
- `expiresIn > maxExpiry` → `IllegalArgumentException`.
- `CloudFrontUrlSigner`가 `CdnWriteSigner`를 구현하지 않는지 타입 체크
  (`assertFalse { cloudFrontUrlSigner is CdnWriteSigner }`).

PEM 실패:
- 잘못된 PEM → 생성자에서 `ImageStorageException.ValidationException`.
- `private-key-path` + `private-key-pem` 동시 지정 → `IllegalStateException`.

보안:
- `CdnProperties.CloudFront.toString()` → `privateKeyPem`이 `[REDACTED]`로 마스킹.

### 8.5 `S3PreSignedUrlSignerTest`

- MockK로 `S3Operations.presignGet/presignPut` mocking.
- 위임 파라미터(key, expires) 전달 정확성 검증.
- `expiresIn ≤ 0` → `IllegalArgumentException`.
- `expiresIn > 7days` (S3 SigV4 max) → `IllegalArgumentException`.
- `S3PreSignedUrlSigner`가 `CdnReadSigner`와 `CdnWriteSigner` 모두 구현하는지 타입 체크.
- `CancellationException` rethrow 확인.

### 8.6 `ImagesStorageAutoConfigurationTest`

`ApplicationContextRunner` (Boot 4) 시나리오:

1. `backend=local` → `LocalImageStorage` 빈 등록, `S3ImageStorage` 없음.
2. `backend=s3` + `S3Operations` 빈 제공 → `S3ImageStorage` 빈 등록.
3. `backend=s3` + `S3Operations` classpath 없음 (`FilteredClassLoader`) → `LocalImageStorage` fallback.
4. `enabled=false` → `ImageStorage` 빈 없음.
5. 사용자 직접 `@Bean ImageStorage` → 자동 구성 bean 없음 (`@ConditionalOnMissingBean` 동작).
6. `backend=s3` + `bucket` 미설정 → context 로딩 실패 (`IllegalArgumentException` / `BeanCreationException`).

> `FilteredClassLoader`: Spring Boot 4에서 `org.springframework.boot.test.context.FilteredClassLoader` 경로 유지 확인.

### 8.7 `ImagesCdnAutoConfigurationTest`

1. `enabled=false` → CDN 빈 없음 (default).
2. `provider=cloudfront` + 키 경로 미설정 → context fail.
3. `provider=cloudfront` + 키 경로 설정 → `CloudFrontUrlSigner` 빈 등록 (`CdnReadSigner` 타입).
4. `provider=s3_presign` + `S3Operations` 제공 → `S3PreSignedUrlSigner` 빈 등록 (`CdnReadSigner` + `CdnWriteSigner`).
5. `provider=s3_presign` + `S3Operations` 없음 → CDN 빈 없음.

### 8.8 `ImagesProcessingAutoConfigurationTest`

- `enabled=true` → `ImageProcessingProperties` 빈 바인딩.
- `enabled=false` → 빈 없음.
- `defaultQuality` 범위 0 / 101 → `ValidationException`.

### 8.9 `ImageStorageHealthIndicatorTest`

- MockK `ImageStorage` + `exists()` 성공 → `Health.up()`.
- `exists()` `ImageStorageException` → `Health.down()`.

### 8.10 `UploadOptionsTest`

- allowlist 내 contentType → 정상 생성.
- allowlist 외 contentType → `IllegalArgumentException`.
- `contentType` blank → `IllegalArgumentException`.

### 8.11 `ImageObjectKeyTest`

- `of("foo", "bar.jpg")` → `fullKey == "foo/bar.jpg"`.
- `of("foo/", "bar.jpg")` → `fullKey == "foo/bar.jpg"` (double-slash 방지).
- `of("foo", "../etc/passwd")` → `IllegalArgumentException`.
- `of("foo", "bar/../secret")` → `IllegalArgumentException`.
- `of("", "bar.jpg")` → `IllegalArgumentException` (blank prefix).
- `of("foo", "")` → `IllegalArgumentException` (blank name).
- `of("foo", "bar baz")` (공백 포함) → `IllegalArgumentException`.

---

## 9. 보안 고려사항

### 9.1 CloudFront PEM 키 관리

- **권장**: `bluetape4k.images.cdn.cloudfront.private-key-path`. 컨테이너 secret/HSM 마운트와 호환.
- **허용하지만 비권장**: `private-key-pem` 인라인. 사용 시 `WARN` 로그.
- 두 값 동시 지정 → `IllegalStateException`.
- PEM 검증 실패 시 경로/지문(SHA-256)만 표시, PEM 값 echo 금지.
- `private-key-path`로 읽은 byte 배열: `Arrays.fill(bytes, 0)` zero-out (최선).
- `private-key-pem` String: JVM heap 잔류, zero-out 불가 → 이것이 인라인 PEM을 권장하지 않는 이유.
- `CdnProperties.CloudFront.toString()` override로 Actuator 마스킹.
- `SanitizingFunction` 등록으로 `/actuator/configprops`, `/actuator/env` 에서 `privateKeyPem` 노출 차단.

### 9.2 오류 메시지 위생

- 모든 구현체에서:
  - bucket/key.fullKey → 로그에 OK.
  - PEM bytes, Authorization header, signed URL 전문 → 로그 출력 금지.
  - `ImageStorageException.wrap()` 팩토리에서 SdkException 메시지 sanitize (AWS SDK 오류 메시지에 키 값 포함 가능).

### 9.3 자원 누수 방지

- `LocalImageStorage` NIO 호출은 `withContext(Dispatchers.IO)` 안, stream은 `use {}`.
- `CloudFrontUrlSigner` 생성 후 `private-key-path`로 읽은 byte 배열 즉시 `Arrays.fill(..., 0)`.

### 9.4 자격증명

- 본 모듈은 자격증명을 직접 다루지 않는다. AWS SDK의 `DefaultCredentialsProvider` 체인을 그대로 따른다.
- IRSA/IMDSv2 컨테이너 환경에서 별도 설정 없이 동작.

### 9.5 DoS 방지

- `ImageStorageProperties.maxSizeBytes` (기본 50 MB)으로 업로드/다운로드 크기 제한.
- `CdnProperties.CloudFront.maxExpiry`로 서명 URL 유효기간 상한 제한.
- S3 동시 요청 수 `s3.maxInFlight` (기본 64)으로 제한.

---

## 10. Definition of Done (DoD)

### 구현

- [ ] `ImageObjectKey` (private constructor + `of()` factory, path traversal 검증), `ImageUploadResult`,
      `UploadOptions` (content-type allowlist), `ImageStorageException` (sealed hierarchy) 구현.
- [ ] `ImageStorage` (upload×2, download×2, delete, exists, list), `CdnReadSigner`, `CdnWriteSigner` 인터페이스 구현.
- [ ] `LocalImageStorage`, `S3ImageStorage`, `CloudFrontUrlSigner` (CdnReadSigner만),
      `S3PreSignedUrlSigner` (CdnReadSigner + CdnWriteSigner) 구현.
- [ ] 5개 AutoConfig phase 클래스 (`ImagesProcessingAutoConfiguration`, `ImagesStorageAutoConfiguration`,
      `ImagesCdnAutoConfiguration`, `ImagesHealthAutoConfiguration`, `ImagesMetricsAutoConfiguration`)
      + `AutoConfiguration.imports` 등록.
- [ ] `afterName = [...]` (string FQCN) 사용 확인 (P0-1).
- [ ] `LocalStorageConfiguration`은 `@ConditionalOnMissingBean(ImageStorage)` 만으로 fallback 보장 (P0-2).
- [ ] CDN Phase의 S3 빈은 nested `@Configuration` 안에 격리 (P0-3).
- [ ] 모든 구현 클래스에 `companion object : KLogging()`.
- [ ] 모든 config data class에 `Serializable + serialVersionUID`.

### 테스트

- [ ] `LocalImageStorageTest` — happy path + 실패 경로 + path traversal + cancellation + concurrency.
- [ ] `S3ImageStorageTest` — Floci + happy path + 실패 경로 + cancellation.
- [ ] `CloudFrontUrlSignerTest` — ephemeral RSA 키 + PEM 실패 + expiry 검증 + Actuator 마스킹 + 타입 체크.
- [ ] `S3PreSignedUrlSignerTest` — MockK + expiry 검증 + 타입 체크.
- [ ] `ImagesStorageAutoConfigurationTest` — 6개 시나리오.
- [ ] `ImagesCdnAutoConfigurationTest` — 5개 시나리오.
- [ ] `ImagesProcessingAutoConfigurationTest`, `ImageStorageHealthIndicatorTest`,
      `UploadOptionsTest`, `ImageObjectKeyTest` 통과.
- [ ] `./gradlew :images-spring-boot4:test` 전체 통과.

### 문서·빌드

- [ ] `README.md` + `README.ko.md` — 아키텍처(Mermaid), Quickstart, properties 레퍼런스, 보안 주의사항.
- [ ] 모든 public API에 영어 KDoc (요약 + `## Behavior / Contract`).
- [ ] `gradle/libs.versions.toml` 버전·라이브러리·플러그인 추가.
- [ ] `settings.gradle.kts`에 `includeModules("images-spring-boot4")` 추가.
- [ ] 워크스페이스 `CLAUDE.md` 및 `bluetape4k-image/CLAUDE.md` 모듈 표에 `images-spring-boot4` 행 추가.
- [ ] `README.md` + `README.ko.md` 모듈 표에도 동일하게 추가.
- [ ] `./gradlew :images-spring-boot4:detekt` 통과.

### CI/리뷰

- [ ] `oh-my-claudecode:code-reviewer` — HIGH/CRITICAL 0건.
- [ ] `.github/workflows/ci.yml` 신규 모듈 포함 여부 확인; 누락 시 보정 PR.
- [ ] PR title/body 영어, test 결과·근거·검증 명령 포함, squash-merge.

---

## 부록 A. 시퀀스 다이어그램

업로드 흐름:

```
Service ──upload(key, bytes, options)──▶ S3ImageStorage
                                             │ validate(maxSizeBytes, allowedContentType)
                                             ▼
                                          S3Operations.putObject (suspend)
                                             │
                                             ▼
                                          ImageUploadResult(key, eTag, sizeBytes)
```

서명 URL 발급 (CloudFront):

```
Controller ──signGet(key, 10m)──▶ CloudFrontUrlSigner
                                       │ validate(expiresIn > 0, ≤ maxExpiry)
                                       ▼
                                    CloudFrontUtilities.getSignedUrlWithCannedPolicy
                                       │
                                       ▼
                                    URI("https://d123.cloudfront.net/...?Key-Pair-Id=...&Signature=...")
```

AutoConfig 초기화 순서:

```
@AutoConfiguration ──▶ ImagesProcessingAutoConfiguration
                              ↓ afterName
                        ImagesStorageAutoConfiguration
                          ├── S3StorageConfiguration (@ConditionalOnClass S3Operations)
                          │     └── S3ImageStorage (@ConditionalOnMissingBean)
                          └── LocalStorageConfiguration
                                └── LocalImageStorage (@ConditionalOnMissingBean) ← always fallback
                              ↓ afterName
                   ┌──────────────────────────────────────┐
                   │ ImagesCdnAutoConfiguration            │
                   │ ImagesHealthAutoConfiguration         │
                   │ ImagesMetricsAutoConfiguration        │
                   └──────────────────────────────────────┘
```

---

## 부록 B. Review 수렴 이력

### Round 1 (2026-05-17)

| Reviewer | P0/CRITICAL | P1/HIGH | P2/MEDIUM | P3/LOW |
|----------|------------|---------|-----------|--------|
| Phase 1 - Developer | 0 | 3 | 3 | 2 |
| Phase 1 - Security | 0 | 3 | 3 | 1 |
| Phase 1 - Ops/SRE | 0 | 3 | 4 | 2 |
| Phase 1 - User/Caller | 0 | 2 | 4 | 2 |
| Phase 2 Critic | 0 | 12 (dedup) | 13 | 8 |
| 6-tier Advisor | 0 | 8 | 19 | 13 |
| Phase 3 Codex | 3 | 5 | — | — |

Round 1 P0: 3건 (Codex 발견) — 모두 §6.1에 반영 (afterName, LocalStorage fallback, CDN nested class)
Round 1 P1: 14 unique → 모두 스펙 반영 (§4.1–§9)
적용 commit: (다음 커밋에 기록)
