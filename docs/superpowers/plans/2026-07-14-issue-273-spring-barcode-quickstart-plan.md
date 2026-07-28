# Issue #273 Spring Boot Barcode Quickstart 구현 계획

> **Agentic worker 필수 지침:** 이 계획은 task 단위로 구현한다. 구현 표면은 superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans를 사용한다. 진행 추적에는 checkbox(`- [ ]`) 문법을 사용한다.

**목표:** Bounded PNG/JPEG/WebP multipart upload에서 barcode를 추출하고 ZXing 기반 deterministic success, no-result, malformed-input endpoint를 제공하는 runnable, non-published Spring Boot 4 quickstart를 추가한다.

**아키텍처:** 전용 `examples/spring-boot-barcode-api` module이 provider-neutral `BarcodeReader` bean을 `ZxingBarcodeReader`에 연결한다. 단일 coroutine-aware extraction service가 upload validation, dimension probing, image decoding, provider call, bounded DTO mapping을 소유하며, multipart POST route와 module-owned fixture GET route 세 개가 모두 이 service를 호출한다. Controller advice는 stable, sanitized HTTP error mapping을 소유한다.

**Tech Stack:** Kotlin 2.4, Java 21, Spring Boot 4 WebMVC, Kotlin coroutines, Scrimage `ImmutableImage`, bluetape4k barcode API, ZXing provider, JUnit 5, MockMvc, bluetape4k assertions, Gradle 9.x.

---

## 승인된 계약

- Issue: [#273](https://github.com/bluetape4k/bluetape4k-image/issues/273)
- Spec: `docs/superpowers/specs/2026-07-14-issue-273-spring-barcode-quickstart-design.md`
- Repository: `bluetape4k/bluetape4k-image`
- Base: `origin/develop`
- Head: `feat/issue-273-barcode-quickstart`
- Endpoints:
  - `POST /api/barcodes/extract`
  - `GET /api/barcodes/sample`
  - `GET /api/barcodes/no-result`
  - `GET /api/barcodes/malformed`
- Upload allowlist: `image/png`, `image/jpeg`, `image/webp`
- Default guard: 5 MiB encoded byte, 16,777,216 decoded pixel, 8,192 maximum side
- Success와 no-result는 모두 `200 OK`를 반환한다. Malformed input은 reason `MALFORMED_INPUT`을 가진 sanitized `400 Bad Request`를 반환한다.
- Quickstart는 upload를 저장하지 않으며 production artifact, provider, dependency version, BOM entry, benchmark result, public library API를 추가하지 않는다.
- PR 생성은 implementation, verification, review, lesson, workflow gate가 모두 통과한 뒤에만 허용된다. Merge에는 이후 fresh merge-ready approval이 필요하다.
- Implementation은 plan approval 이후 이 session에서 inline으로 진행하며, 사용자가 이전에 선택한 execution mode와 맞춘다.

## 파일 Responsibility Map

| Path | 책임 |
|---|---|
| `settings.gradle.kts` | `:spring-boot-barcode-api` 등록과 example directory mapping |
| `AGENTS.md` | Module table과 targeted command list에 example 추가 |
| `.github/workflows/Examples.yml` | PR/push/daily matrix에 example test 추가 |
| `examples/spring-boot-barcode-api/build.gradle.kts` | Non-published Spring/ZXing example dependency와 main class 선언 |
| `examples/spring-boot-barcode-api/src/main/kotlin/io/bluetape4k/images/examples/spring/barcode/SpringBootBarcodeApiApplication.kt` | Application entrypoint만 담당 |
| `.../BarcodeApiConfiguration.kt` | Immutable property, `BarcodeReader`, fixture, service bean |
| `.../BarcodeApiModels.kt` | Bounded success/error DTO와 example-local request exception |
| `.../BarcodeExampleFixtures.kt` | Fixed enum-owned classpath resource, startup validation, copy-on-read |
| `.../BarcodeExtractionService.kt` | Upload validation, byte I/O, dimension guard, decode, extraction, DTO mapping |
| `.../BarcodeApiController.kt` | Multipart POST 하나와 fixture GET route 세 개 |
| `.../BarcodeApiExceptionHandler.kt` | Payload 또는 stack disclosure 없는 stable status/error/reason mapping |
| `examples/spring-boot-barcode-api/src/main/resources/application.yml` | Spring multipart request/file limit와 example default |
| `examples/spring-boot-barcode-api/src/main/resources/barcodes/*` | Module-owned QR, blank, malformed fixture |
| `examples/spring-boot-barcode-api/src/test/kotlin/.../BarcodeExampleFixturesTest.kt` | Fixture hash, dimension, payload, empty result, byte isolation |
| `.../BarcodeApiConfigurationTest.kt` | Property default/validation과 provider-neutral bean wiring |
| `.../BarcodeExtractionServiceTest.kt` | Service success, format, guard, malformed normalization, cancellation |
| `.../SpringBootBarcodeApiApplicationTest.kt` | MockMvc POST/GET/status/JSON integration contract |
| `.../BarcodeApiExceptionHandlerTest.kt` | MockMvc가 container limit를 강제한다고 가정하지 않는 resolver-level multipart/missing-part error mapping |
| `examples/spring-boot-barcode-api/src/test/resources/*` | Required JUnit parallelism과 test logging configuration |
| `examples/spring-boot-barcode-api/README.md` / `README.ko.md` | 동등한 runnable English/Korean guide와 production warning |
| `examples/spring-boot-barcode-api/docs/images/readme-diagrams/*` | Scenario, architecture, sequence SVG/PNG pair |
| `README.md` / `README.ko.md` | Root barcode와 Examples link |
| `images-barcode-zxing/README.md` / `README.ko.md` | Provider-to-quickstart link |
| `docs/review/2026-07-14-issue-273-spring-barcode-quickstart-*.md` | Plan/code review convergence evidence |
| `docs/lessons/2026-07-14-issue-273-spring-barcode-quickstart.md` | Required Type A lesson |

Example 아래 모든 Kotlin file은 package `io.bluetape4k.images.examples.spring.barcode`를 사용한다.

## Acceptance Traceability

| Requirement | Tasks | Proof |
|---|---|---|
| Runnable dedicated Spring Boot module | 1, 3 | `projects`, application context test, `bootRun` smoke |
| ZXing 기반 provider-neutral Spring bean | 3 | Bean type test; ZXing import는 configuration에만 한정 |
| Multipart PNG/JPEG/WebP upload | 4, 5 | Service format test와 MockMvc multipart test |
| Encoded byte, decoded pixel, side, content-type guard | 3-5 | Property validation, focused service test, HTTP status/code test |
| Deterministic success/no-result/malformed scenario | 2, 4, 6 | Pinned fixture test와 GET integration test 세 개 |
| Shared extraction service 하나 | 4-6 | Controller constructor/source review와 MockK/MockMvc behavior |
| Bounded response DTO와 sanitized error | 4, 5 | Exact JSON assertion과 forbidden-field assertion |
| 올바른 coroutine dispatch와 cancellation | 4 | Injected dispatcher test와 변경되지 않은 `CancellationException` |
| Bilingual docs와 rendered diagram 세 개 | 7 | Locale parity review와 SVG/PNG render validation |
| Complete non-published module registration | 1, 8 | settings/AGENTS/Examples/root/provider link, publication/Kover N/A evidence |
| Merge-ready workflow evidence | 8 | Clean verification, P0/P1=0 review, lesson, exact-head PR check |

## Risk Prediction

| Risk | Signal | Prevention/test | Rollback point |
|---|---|---|---|
| WebP가 media allowlist를 통과하지만 ImageIO가 probe하지 못함 | Valid WebP에 대해 `probeImageDimensions`가 `null` 반환 | Bounded `readImageMetadataReport` fallback 사용, real WebP 실행 | Task 4 RED/GREEN으로 복귀 |
| Compressed image bomb이 full decode까지 도달 | Pixel/side check가 `immutableImageOf` 이후 발생 | Decode 전에 dimension을 해석하고 두 limit 모두 강제 | Service commit revert |
| Multipart limit와 application limit 불일치 | Stable JSON handler 전에 container가 거부하거나 service가 excess byte를 읽음 | File limit는 5 MiB로 유지, request-envelope overhead 허용, handler 직접 test와 real oversized request smoke test | Task 3, 5, 8로 복귀 |
| Error response가 provider 또는 input detail 누출 | Handler가 exception message/cause/filename 반환 | Fixed message와 bounded DTO 사용, filename/bytes/stack/metadata 부재 assert | Task 5로 복귀 |
| Mutable fixture byte가 request 사이에서 누출 | Cached `ByteArray`를 직접 반환 | Load와 read마다 clone, mutation-isolation test | Task 2로 복귀 |
| Cancellation이 decode failure로 normalize됨 | Broad `catch (Exception)`이 cancellation보다 앞섬 | `CancellationException`을 명시적으로 rethrow, focused test | Task 4로 복귀 |
| Provider implementation이 HTTP/API code로 누출 | Configuration/test generation 밖에 `com.google.zxing` 또는 `ZxingBarcodeReader` 등장 | Dependency/source scan과 bounded response DTO | Offending task revert |
| 새 module은 local green이지만 CI에 없음 | Examples matrix 또는 settings mapping 누락 | Registration task 우선, final matrix와 `projects` check | Task 1을 한 단위로 revert |
| Documentation이 route/status contract와 drift | curl path/status가 MockMvc test와 다름 | Exact tested example을 두 locale에 복사, parity review | Task 7로 복귀 |

### Task 1: Non-published Spring Example Skeleton 등록

- **Complexity:** Medium
- **Depends on:** approved spec과 clean baseline
- **Pattern skills:** `bluetape-kotlin-patterns`, `references/module-setup.md`
- **Files:** settings, AGENTS, Examples workflow, new Gradle build, required test resource
**Expected DoD:** Gradle이 module을 발견하고, example에 Maven publication surface가 없으며, CI가 PR/push/daily event에서 test를 schedule한다.

- [ ] **Step 1: Module build와 registration chain 추가**

`examples/spring-boot-barcode-api/build.gradle.kts`를 생성한다:

```kotlin
plugins {
    application
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":bluetape4k-images-barcode-zxing"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass.set("io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationKt")
}

springBoot {
    mainClass.set("io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationKt")
}
```

Exact settings mapping을 추가한다:

```kotlin
include("spring-boot-barcode-api")
project(":spring-boot-barcode-api").projectDir = file("examples/spring-boot-barcode-api")
```

`AGENTS.md`에 module table/command를 추가하고 `.github/workflows/Examples.yml`에는 다음 matrix entry를 추가한다:

```yaml
- example: spring-boot-barcode-api
  gradle_tasks: :spring-boot-barcode-api:test
```

기존 `examples/**` path filter가 이미 새 directory를 포함하므로 중복 추가하지 않는다. `examples/spring-boot-image-api`의 repository-standard content를 복사해 `junit-platform.properties`와 `logback-test.xml`을 추가한다.

- [ ] **Step 2: Discovery와 non-publication 검증**

다음을 실행한다:

```bash
./gradlew projects --console=plain
./gradlew :spring-boot-barcode-api:tasks --all --console=plain
actionlint .github/workflows/Examples.yml
```

예상 결과:

- `:spring-boot-barcode-api`가 `examples/spring-boot-barcode-api`에 mapping된다.
- application, `bootRun`, test task가 존재한다.
- Example에 Maven Central publication task가 도입되지 않는다.
- `Examples.yml`이 유효하며 module matrix row를 정확히 하나만 포함한다.

- [ ] **Step 3: Registration unit commit**

```bash
git add settings.gradle.kts AGENTS.md .github/workflows/Examples.yml \
  examples/spring-boot-barcode-api/build.gradle.kts \
  examples/spring-boot-barcode-api/src/test/resources
git commit -m "build: register Spring barcode example"
```

### Task 2: Module-owned Fixture Contract 고정

- **Complexity:** High
- **Depends on:** Task 1
- **Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, `references/testing.md`
- **Files:** fixture resource, loader, fixture test
**Expected DoD:** Module이 hash, dimension, payload/no-result semantic, startup availability, immutable copy behavior가 deterministic한 bounded resource 정확히 세 개를 소유한다.

- [ ] **Step 1: Fixture test 먼저 작성**

다음을 요구하는 test를 포함한 `BarcodeExampleFixturesTest.kt`를 작성한다:

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BarcodeExampleFixturesTest {

    private val fixtures = BarcodeExampleFixtures()
    private val reader: BarcodeReader = ZxingBarcodeReader()

    @Test
    fun `fixtures have pinned sha dimensions and extraction behavior`() {
        val sample = fixtures.bytes(BarcodeExampleFixture.SAMPLE)
        sha256(sample) shouldBeEqualTo
            "5d048dd6769ede80f453ffb6c80fe6745092bf895c429b6104d5cc74d892c44d"
        probeImageDimensions(sample) shouldBeEqualTo ImageDimensions(220, 220)

        val sampleResults = immutableImageOf(sample).extractBarcodes(reader)
        sampleResults.single().text shouldBeEqualTo "bluetape4k-barcode-quickstart"
        sampleResults.single().format shouldBeEqualTo BarcodeFormat.QR_CODE

        val noResult = fixtures.bytes(BarcodeExampleFixture.NO_RESULT)
        sha256(noResult) shouldBeEqualTo
            "86aad41769423ad85a979fefe109d00829044a1eba5d891547499413e3d9ff2b"
        immutableImageOf(noResult).extractBarcodes(reader).shouldBeEmpty()

        sha256(fixtures.bytes(BarcodeExampleFixture.MALFORMED)) shouldBeEqualTo
            "f2e2c6db1745cc40df646dc40c385487c36e4ceb3f1d5c8d6ad1f7620af1ebae"
    }

    @Test
    fun `fixture reads return isolated byte arrays`() {
        val first = fixtures.bytes(BarcodeExampleFixture.SAMPLE)
        val originalFirstByte = first[0]
        first[0] = 0

        fixtures.bytes(BarcodeExampleFixture.SAMPLE)[0] shouldBeEqualTo originalFirstByte
    }
}
```

Internal injected `resourceLoader: (String) -> ByteArray?` constructor를 통해 enum path가 정확히 세 개인지와 missing-resource startup failure도 다룬다.

- [ ] **Step 2: Focused test 실행 후 RED 기록**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeExampleFixturesTest' \
  --console=plain
```

Fixture enum, loader, resource가 아직 없으므로 예상 결과는 FAIL이다. RED로 인정하기 전에 관련 없는 syntax/configuration error는 고친다.

- [ ] **Step 3: Fixed loader contract 추가**

`BarcodeExampleFixtures.kt`를 생성한다:

```kotlin
internal enum class BarcodeExampleFixture(val resource: String) {
    SAMPLE("barcodes/qr.png"),
    NO_RESULT("barcodes/no-result.png"),
    MALFORMED("barcodes/malformed.bin"),
}

internal class BarcodeExampleFixtures internal constructor(
    resourceLoader: (String) -> ByteArray? = ::loadClasspathResource,
) {
    private val resources: Map<BarcodeExampleFixture, ByteArray> =
        BarcodeExampleFixture.entries.associateWith { fixture ->
            requireNotNull(resourceLoader(fixture.resource)) {
                "Required barcode example fixture is missing: ${fixture.resource}"
            }.copyOf()
        }

    fun bytes(fixture: BarcodeExampleFixture): ByteArray =
        resources.getValue(fixture).copyOf()
}
```

Path construction은 enum-only로 유지한다. `loadClasspathResource`는 class loader를 사용하고 stream은 `use`로 닫으며 mutable cached array를 반환하지 않아야 한다.

- [ ] **Step 4: Exact fixture byte 생성/복사와 hash 검증**

`src/main/resources/barcodes/`를 생성한다. ZXing 3.5.4 `MultiFormatWriter`/`MatrixToImageWriter`, payload `bluetape4k-barcode-quickstart`, PNG output으로 220x220 QR을 한 번 생성한다. 이미 review된 220x220 white PNG를 `benchmark/images-benchmark/src/main/resources/bench/barcode/no-result.png`에서 복사한다. `malformed.bin`은 newline 없이 정확히 12 ASCII byte `not-an-image`로 만든다.

Production/test source에는 generator를 남기지 않는다. 계속하기 전에 다음을 실행한다:

```bash
shasum -a 256 \
  examples/spring-boot-barcode-api/src/main/resources/barcodes/qr.png \
  examples/spring-boot-barcode-api/src/main/resources/barcodes/no-result.png \
  examples/spring-boot-barcode-api/src/main/resources/barcodes/malformed.bin
```

예상 hash는 순서대로 다음과 같다:

```text
5d048dd6769ede80f453ffb6c80fe6745092bf895c429b6104d5cc74d892c44d
86aad41769423ad85a979fefe109d00829044a1eba5d891547499413e3d9ff2b
f2e2c6db1745cc40df646dc40c385487c36e4ceb3f1d5c8d6ad1f7620af1ebae
```

- [ ] **Step 5: GREEN 실행과 commit**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeExampleFixturesTest' \
  --console=plain
git add examples/spring-boot-barcode-api/src/main/{kotlin,resources} \
  examples/spring-boot-barcode-api/src/test/kotlin
git commit -m "test: lock barcode quickstart fixtures"
```

예상 결과는 exact hash, dimension, QR payload, empty result, copy isolation을 검증하며 PASS이다.

### Task 3: Wire the Application, Properties, and Provider Boundary

- **Complexity:** Medium
- **Depends on:** Tasks 1-2
- **Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, `references/spring-boot.md`
- **Files:** application, configuration, properties, application.yml, configuration test
**Expected DoD:** Spring starts with validated immutable limits and exposes a provider-neutral `BarcodeReader` whose implementation is ZXing.

- [ ] **Step 1: Write failing configuration tests**

Create `BarcodeApiConfigurationTest.kt` with
`ApplicationContextRunner().withUserConfiguration(BarcodeApiConfiguration::class.java)`.
Cover:

- default 5 MiB/16,777,216/8,192 values;
- fixed service allowlist `image/png`, `image/jpeg`, `image/webp`;
- zero/negative limits and `maxInputBytes > Int.MAX_VALUE` fail binding/startup;
- the bean is declared as `BarcodeReader` and is backed by `ZxingBarcodeReader`;
- all three fixtures load during context startup.

Run RED:

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeApiConfigurationTest' \
  --console=plain
```

Expected: FAIL because application/configuration/properties do not exist.

- [ ] **Step 2: Implement immutable validated properties and beans**

Create `BarcodeApiConfiguration.kt` with this shape:

```kotlin
@ConfigurationProperties(prefix = "example.barcode")
data class BarcodeExampleProperties(
    val maxInputBytes: Long = 5L * 1024L * 1024L,
    val maxInputPixels: Long = 16_777_216L,
    val maxInputSide: Int = 8_192,
) : Serializable {
    init {
        maxInputBytes.requirePositiveNumber("maxInputBytes")
        require(maxInputBytes <= Int.MAX_VALUE) { "maxInputBytes must fit Int" }
        maxInputPixels.requirePositiveNumber("maxInputPixels")
        maxInputSide.requirePositiveNumber("maxInputSide")
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BarcodeExampleProperties::class)
internal class BarcodeApiConfiguration {
    @Bean
    fun barcodeReader(): BarcodeReader = ZxingBarcodeReader()

    @Bean
    fun barcodeExampleFixtures(): BarcodeExampleFixtures = BarcodeExampleFixtures()
}
```

Only this configuration file may import `ZxingBarcodeReader`. Add the
`@SpringBootApplication` entrypoint in its own file. Add English KDoc to the
public application and configuration-properties classes. The configuration,
fixtures, controller, service, advice, and HTTP DTOs remain `internal` because
this non-published module has no public library API.

Set aligned multipart limits in `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB

example:
  barcode:
    max-input-bytes: 5242880
    max-input-pixels: 16777216
    max-input-side: 8192
```

- [ ] **Step 3: Run GREEN and commit**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeApiConfigurationTest' \
  --console=plain
git add examples/spring-boot-barcode-api/src/main \
  examples/spring-boot-barcode-api/src/test/kotlin
git commit -m "feat: configure Spring barcode quickstart"
```

### Task 4: Implement the Coroutine-Aware Extraction Service with TDD

- **Complexity:** High
- **Depends on:** Task 3
- **Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, `kotlin-coroutines-skill`
- **Files:** models, service, service test
**Expected DoD:** one service safely handles uploads and fixtures, all accepted formats, pre-decode limits, provider-neutral DTO mapping, malformed normalization, and cancellation propagation.

- [ ] **Step 1: Define bounded model tests and service behavior tests**

Create `BarcodeExtractionServiceTest.kt` using `runTest` and injected test
dispatchers. Cover these independent cases:

1. QR bytes return count 1, exact text, `QR_CODE`, provider `ZXing`.
2. blank PNG returns count 0 and an empty immutable list.
3. valid JPEG and WebP conversions are accepted and decoded.
4. empty bytes and empty multipart are rejected as `empty_input`.
5. unsupported/missing media type is `unsupported_media_type`.
6. `MultipartFile.size` and actual read bytes above the limit are
   `payload_too_large` before decode.
7. over-limit width/height and total pixels are `payload_too_large` before
   `BarcodeReader` invocation.
8. malformed bytes and missing dimensions become
   `BarcodeException(MALFORMED_INPUT)`.
9. WebP obtains dimensions through metadata fallback when the primary probe is
   unavailable.
10. provider `UNSUPPORTED_FORMAT`, `PROVIDER_UNAVAILABLE`, and `DECODE_FAILED`
    exceptions remain provider-neutral exceptions.
11. reader-thrown `CancellationException` is rethrown unchanged.

Use test seams for `dimensionProbe` and `metadataDimensionProbe`, not static
mocking. Inject `ioDispatcher` and `cpuDispatcher`; verify file byte reads use
the former and probe/decode/reader work uses the latter. For this dispatcher
test, create two named single-thread executors, convert them with
`asCoroutineDispatcher()`, record the executing thread in a custom
`MultipartFile` plus the injected probe/reader, and close both dispatchers with
`use` after the assertion. Do not leak executor threads from tests.

- [ ] **Step 2: Run RED**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeExtractionServiceTest' \
  --console=plain
```

Expected: FAIL because DTOs, request exception, and service do not exist.

- [ ] **Step 3: Add bounded serializable HTTP models**

Create `BarcodeApiModels.kt`:

```kotlin
data class BarcodeExtractionResponse(
    val count: Int,
    val results: List<BarcodeResultResponse>,
) : Serializable {
    init {
        require(count == results.size) { "count must match results.size" }
    }
    private companion object { private const val serialVersionUID: Long = 1L }
}

data class BarcodeResultResponse(
    val text: String,
    val format: BarcodeFormat,
    val provider: String,
) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

data class BarcodeErrorResponse(
    val error: String,
    val reason: String? = null,
    val message: String,
) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

internal class BarcodeRequestException(
    val status: HttpStatus,
    val error: String,
    message: String,
) : RuntimeException(message)
```

Mapping must copy only `text`, `format`, and `provider.name`; never serialize
`BarcodeResult` directly.

- [ ] **Step 4: Implement the service and explicit dispatcher boundary**

Create `BarcodeExtractionService.kt` with this constructor contract:

```kotlin
internal class BarcodeExtractionService(
    private val reader: BarcodeReader,
    private val properties: BarcodeExampleProperties,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val dimensionProbe: (ByteArray) -> ImageDimensions? = ::probeImageDimensions,
    private val metadataDimensionProbe: (ByteArray, Int) -> ImageDimensions? = { bytes, maxBytes ->
        readImageMetadataReport(
            bytes,
            ImageMetadataReadOptions(maxBytes = maxBytes),
        ).dimensions
    },
) {
    suspend fun extract(file: MultipartFile): BarcodeExtractionResponse
    suspend fun extract(bytes: ByteArray): BarcodeExtractionResponse
}
```

After the service compiles, extend the existing internal configuration with:

```kotlin
@Bean
fun barcodeExtractionService(
    reader: BarcodeReader,
    properties: BarcodeExampleProperties,
): BarcodeExtractionService = BarcodeExtractionService(reader, properties)
```

`extract(file)` must validate `isEmpty`, normalized media type, and reported
size before `withContext(ioDispatcher) { file.bytes }`, then recheck actual byte
size. The normalized media type must belong to an internal fixed constant set
containing only PNG, JPEG, and WebP; it is not externally configurable.
`extract(bytes)` must execute the following inside `cpuDispatcher`:

1. reject empty/oversized bytes;
2. resolve dimensions with primary probe, then bounded metadata fallback;
3. reject missing dimensions as `MALFORMED_INPUT`;
4. reject side or pixel excess as `413 payload_too_large`;
5. call `immutableImageOf(bytes).extractBarcodes(reader, BarcodeOptions())`;
6. map only bounded result fields.

Catch order is mandatory:

```kotlin
try {
    // probe, guard, decode, extract, map
} catch (e: CancellationException) {
    throw e
} catch (e: BarcodeRequestException) {
    throw e
} catch (e: BarcodeException) {
    throw e
} catch (e: Exception) {
    throw BarcodeException(
        BarcodeFailureReason.MALFORMED_INPUT,
        "The uploaded file is not a decodable image.",
        e,
    )
}
```

Do not use `runCatching` around suspend code. Do not include filename, content,
path, backend metadata, or the original exception message in caller-facing
text.

- [ ] **Step 5: Run GREEN, the whole module, and commit**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeExtractionServiceTest' \
  --console=plain
./gradlew :spring-boot-barcode-api:test --console=plain
git add examples/spring-boot-barcode-api/src/main/kotlin \
  examples/spring-boot-barcode-api/src/test/kotlin
git commit -m "feat: extract uploaded barcodes safely"
```

### Task 5: Expose the Multipart POST Contract

- **Complexity:** High
- **Depends on:** Task 4
- **Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, `references/spring-boot.md`
- **Files:** controller, exception handler, MockMvc integration test
**Expected DoD:** callers can upload PNG/JPEG/WebP and receive stable success/no-result/error JSON with exact status codes.

- [ ] **Step 1: Write POST MockMvc tests first**

Create `SpringBootBarcodeApiApplicationTest.kt` with `@SpringBootTest` and
`@AutoConfigureMockMvc`. For requests that reach a suspend controller method,
require `request().asyncStarted()` and then `asyncDispatch`. Resolver failures
that occur before controller invocation are tested separately and must not
assume an async lifecycle.

Cover:

- QR multipart: `200`, count 1, exact text/format/provider;
- blank PNG: `200`, count 0, empty results;
- valid JPEG and WebP: accepted content type and `200` response contract;
- malformed image: `400`, `malformed_input`, `MALFORMED_INPUT`, fixed message;
- empty file: `400 empty_input`;
- unsupported or missing content type: `415 unsupported_media_type`;
- service-size overflow: `413 payload_too_large`;
- decoded side/pixel overflow: `413 payload_too_large`;
- omitted `file` part: `400 empty_input` with the example error schema;
- response JSON does not contain `rawBytes`, `rawBackendFormat`, `metadata`,
  `region`, `stackTrace`, uploaded filename, or input bytes.

Run RED:

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationTest' \
  --console=plain
```

Expected: FAIL with 404 because controller and advice do not exist.

- [ ] **Step 2: Implement the thin POST controller**

Create `BarcodeApiController.kt`:

```kotlin
@RestController
@RequestMapping("/api/barcodes")
internal class BarcodeApiController(
    private val extractionService: BarcodeExtractionService,
) {
    @PostMapping("/extract", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun extract(@RequestParam("file") file: MultipartFile): BarcodeExtractionResponse =
        extractionService.extract(file)
}
```

The controller performs no byte read, decode, provider call, or exception
message construction.

- [ ] **Step 3: Implement stable sanitized advice**

Create `BarcodeApiExceptionHandler.kt` with
`@RestControllerAdvice(basePackageClasses = [BarcodeApiController::class])`.
Map:

| Exception/reason | Status | `error` | Fixed message |
|---|---:|---|---|
| `BarcodeRequestException` | exception status | exception error | bounded example-local message |
| `MaxUploadSizeExceededException` | 413 | `payload_too_large` | `The uploaded file exceeds the configured size limit.` |
| `MissingServletRequestPartException` for `file` | 400 | `empty_input` | `The multipart file part is required.` |
| `MALFORMED_INPUT` | 400 | `malformed_input` | `The uploaded file is not a decodable image.` |
| `UNSUPPORTED_FORMAT` | 400 | `unsupported_format` | `The requested barcode format is not supported.` |
| `PROVIDER_UNAVAILABLE` | 503 | `provider_unavailable` | `The barcode provider is unavailable.` |
| all other `BarcodeException` reasons | 500 | lowercase reason | `Barcode extraction failed.` |

Set `reason` only for `BarcodeException` and never return `cause` or raw
`exception.message` for provider failures.

Create `BarcodeApiExceptionHandlerTest.kt` and call the advice with
`MaxUploadSizeExceededException` and a missing-`file` exception directly. This
locks status and JSON DTO mapping because MockMvc's synthetic
`MockMultipartFile` does not exercise the embedded servlet container's byte
limit. Also parameterize `MALFORMED_INPUT`, `UNSUPPORTED_FORMAT`,
`PROVIDER_UNAVAILABLE`, `DECODE_FAILED`, `NO_BARCODE`, `CANCELLED`, and `UNKNOWN`
to lock their exact status/error/reason/fixed-message mapping and prove that a
provider exception message is never echoed. The real resolver limit is covered
by Task 8's bootRun smoke request.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationTest' \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeApiExceptionHandlerTest' \
  --console=plain
git add examples/spring-boot-barcode-api/src/main/kotlin \
  examples/spring-boot-barcode-api/src/test/kotlin
git commit -m "feat: expose barcode upload endpoint"
```

### Task 6: Add the Three Deterministic GET Scenarios

- **Complexity:** Medium
- **Depends on:** Task 5
- **Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`
- **Files:** controller and existing integration test
**Expected DoD:** success, no-result, and malformed contracts are reproducible without caller-owned files and still flow through the same service/advice.

- [ ] **Step 1: Add failing GET integration tests**

Add assertions for:

```text
GET /api/barcodes/sample     -> 200, count 1, expected QR payload
GET /api/barcodes/no-result  -> 200, count 0, results []
GET /api/barcodes/malformed  -> 400, malformed_input, MALFORMED_INPUT
```

The tests must prove GET responses match the POST DTO/error schema. Do not add a
Spring spy solely to prove delegation; the controller has no alternate decode
dependency, and the final source review must confirm that all four methods call
the single constructor-injected service.

Run:

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationTest' \
  --console=plain
```

Expected: FAIL with 404 for all three paths.

- [ ] **Step 2: Add GET mappings through the same service**

Add `BarcodeExampleFixtures` as the controller's second constructor dependency,
then add only these methods:

```kotlin
@GetMapping("/sample")
suspend fun sample(): BarcodeExtractionResponse =
    extractionService.extract(fixtures.bytes(BarcodeExampleFixture.SAMPLE))

@GetMapping("/no-result")
suspend fun noResult(): BarcodeExtractionResponse =
    extractionService.extract(fixtures.bytes(BarcodeExampleFixture.NO_RESULT))

@GetMapping("/malformed")
suspend fun malformed(): BarcodeExtractionResponse =
    extractionService.extract(fixtures.bytes(BarcodeExampleFixture.MALFORMED))
```

Do not introduce separate fixture-specific decode logic.

- [ ] **Step 3: Run GREEN and commit**

```bash
./gradlew :spring-boot-barcode-api:test --console=plain
git add examples/spring-boot-barcode-api/src/main/kotlin \
  examples/spring-boot-barcode-api/src/test/kotlin
git commit -m "feat: add barcode scenario endpoints"
```

### Task 7: Publish the Bilingual Quickstart and Rendered Diagrams

- **Complexity:** High
- **Depends on:** Tasks 1-6
- **Required skills:** `bluetape-diagram`, then `bluetape-writer`
- **Files:** example READMEs, three diagram SVG/PNG pairs, root/provider README locales
**Expected DoD:** an English or Korean reader can start the app, call all four tested endpoints, understand limits/provider boundaries, and see the same verified diagrams.

- [ ] **Step 1: Create diagram sources and render assets**

Use `bluetape-diagram` and create English-label SVG+PNG pairs under
`examples/spring-boot-barcode-api/docs/images/readme-diagrams/`:

1. `barcode-api-scenarios`: POST upload plus three deterministic GET routes;
2. `barcode-api-architecture`: Controller -> shared service -> provider-neutral
   `BarcodeReader` -> ZXing, with fixture loader beside the controller;
3. `barcode-api-sequence`: validate -> IO bytes -> CPU probe/fallback -> decode
   -> extract -> bounded DTO/error.

This task has no measured two-series chart, so the complementary-color chart
rule is N/A. Diagrams should use the repository palette and clearly distinct
route/service/provider colors.

Render every SVG to PNG, inspect the PNGs visually, and verify text is not
clipped. Keep source SVG and PNG dimensions consistent with the diagram skill.

- [ ] **Step 2: Write the English README from tested commands**

Create `README.md` with the language switch, architecture, dependencies,
`bootRun`, configuration, and these exact calls:

```bash
./gradlew :spring-boot-barcode-api:bootRun
curl http://localhost:8080/api/barcodes/sample
curl http://localhost:8080/api/barcodes/no-result
curl http://localhost:8080/api/barcodes/malformed
curl -F 'file=@/path/to/image.webp;type=image/webp' \
  http://localhost:8080/api/barcodes/extract
```

Include tested success/no-result/malformed JSON, PNG/JPEG/WebP allowlist,
encoded/dimension defaults, provider-neutral API plus ZXing dependency boundary,
verified QR/Code 128 scope, and the warning that unauthenticated local examples
need auth, rate limiting, request-log policy, malware scanning, and operational
limits before internet exposure.

- [ ] **Step 3: Localize and link all locale pairs**

Use `bluetape-writer` to create natural `README.ko.md` with source-equivalent
content and the same assets. Update:

- root `README.md` and `README.ko.md` barcode/Examples sections;
- `images-barcode-zxing/README.md` and `README.ko.md` with the runnable
  quickstart link.

Keep contributor-facing link text English in English files and natural Korean
in Korean files.

- [ ] **Step 4: Validate docs and commit**

Run the diagram skill validators, then:

```bash
rg -n 'spring-boot-barcode-api|/api/barcodes/(extract|sample|no-result|malformed)' \
  README.md README.ko.md \
  images-barcode-zxing/README.md images-barcode-zxing/README.ko.md \
  examples/spring-boot-barcode-api/README.md \
  examples/spring-boot-barcode-api/README.ko.md
git diff --check
git add README.md README.ko.md images-barcode-zxing/README.md \
  images-barcode-zxing/README.ko.md examples/spring-boot-barcode-api
git commit -m "docs: add Spring barcode quickstart guide"
```

### Task 8: Close Verification, Review, Lesson, and PR Delivery Gates

- **Complexity:** High
- **Depends on:** Tasks 1-7
- **Required skills:** `verification-before-completion`, `requesting-code-review`, `bluetape-full-feature`
- **Files:** final review, lesson, workflow evidence, PR metadata
**Expected DoD:** current HEAD is reproducibly green, reviewed at P0=0/P1=0, lesson-complete, pushed in an issue-linked PR, and stopped at CI/merge-ready approval.

- [ ] **Step 1: Run targeted tests from a clean test state**

Run sequentially:

```bash
./gradlew :spring-boot-barcode-api:cleanTest \
  :spring-boot-barcode-api:test \
  --no-build-cache --rerun-tasks --console=plain
./gradlew :bluetape4k-images-barcode-api:test \
  :bluetape4k-images-barcode-zxing:test \
  --console=plain
```

Expected: all tests PASS; no native/JNI, OCR, Docker, or Testcontainers runtime
is used.

- [ ] **Step 2: Run build/static/registration verification**

```bash
./gradlew :spring-boot-barcode-api:build --console=plain
./gradlew detekt --console=plain
./gradlew projects --console=plain
actionlint .github/workflows/Examples.yml
git diff --check origin/develop...HEAD
```

Inspect and record:

- example appears once in settings, AGENTS, and Examples matrix;
- `examples/**` keeps it non-published;
- no BOM/catalog coordinate, publication aggregation, Kover/Codecov artifact,
  main `ci.yml`, nightly production job, benchmark, native/JNI, OCR, Docker, or
  Testcontainers change is required;
- only configuration imports the ZXing implementation in main example code;
- DTOs expose no backend metadata/raw bytes.

- [ ] **Step 3: Smoke-start the application**

Start `:spring-boot-barcode-api:bootRun`, wait for port 8080, call all four
README routes including one multipart fixture upload, compare statuses/JSON to
MockMvc expectations, then send one multipart file larger than 5 MiB and verify
the embedded server returns `413 payload_too_large`. Terminate the process
cleanly. A port collision is a rerun condition with a fixed alternate port such
as `--args='--server.port=18080'`, not a product failure.

- [ ] **Step 4: Run final review and close every P0/P1**

Create
`docs/review/2026-07-14-issue-273-spring-barcode-quickstart-code-review.md`.
Review performance, stability, security, operator/Ops, developer/API, and
user/caller lenses plus integration. Re-run after fixes until P0=0/P1=0.
Validate cancellation, WebP fallback, decompression guards, fixture ownership,
sanitized errors, locale parity, rendered diagrams, and registration N/A
evidence explicitly.

- [ ] **Step 5: Satisfy the Type A lesson gate**

Create `docs/lessons/2026-07-14-issue-273-spring-barcode-quickstart.md` with:

- context and approved endpoint/provider boundary;
- why content type, encoded bytes, and decoded dimensions are distinct guards;
- why WebP needs a metadata fallback when ImageIO probe is absent;
- why cancellation and provider-neutral failures need distinct handling;
- exact verification evidence and reusable guidance for future upload examples.

Commit review/lesson and any review fixes with an intentional English message.

- [ ] **Step 6: Verify workflow state and prepare the PR**

Run the active `bluetape-flow.py verify` command for run
`20260714T033629Z-d8d2c59c`, ensure all required checkboxes have fresh evidence,
push `feat/issue-273-barcode-quickstart`, and create a PR targeting `develop`.
The PR must:

- close issue #273;
- inherit milestone `0.4.0`, assignee `debop`, and labels `documentation` and
  `enhancement` from live issue #273;
- list exact verification commands/results;
- state non-published/BOM/Kover/benchmark/native/OCR/container N/A evidence;
- include diagram previews or links and lesson/review paths.

Verify the live PR metadata and current head SHA after creation.

- [ ] **Step 7: Monitor CI and stop at merge-ready**

Wait for required checks. If a check fails, inspect the live log, reproduce and
fix the root cause, rerun targeted/local evidence, push, and re-review the new
head. Once CI is green, re-read reviews and unresolved threads, verify diagrams
and the lesson against the exact PR head, report merge-ready evidence, and stop
for a fresh explicit merge approval. Do not enable auto-merge.

- [ ] **Step 8: Merge, sync, and clean only after fresh approval**

After the user approves the exact merge-ready PR/head, re-read the live head,
checks, reviews, and unresolved threads once more. Rebase-merge the PR, verify
the merge SHA and issue #273 closure, fast-forward the real local `develop`,
then automatically remove the clean merged worktree and local feature branch.
Remove the remote feature branch when GitHub has not already done so. Preserve
any unrelated dirty worktree and report cleanup evidence.

## Rollback

The example has no state or migration. A full rollback removes, as one coherent
unit:

1. `examples/spring-boot-barcode-api/`;
2. the settings mapping;
3. the AGENTS module/command entries;
4. the Examples matrix row;
5. root/provider README locale links;
6. issue-specific review/lesson artifacts if the entire feature is abandoned.

After rollback, rerun `./gradlew projects`, `actionlint`, barcode API/provider
tests, and `git diff --check`. Do not remove or change the existing production
barcode API/provider artifacts or the issue #272 benchmark fixtures.

## Plan Self-Review

- [x] Every issue/spec acceptance criterion maps to an ordered task and command.
- [x] Every behavioral implementation task starts with a focused RED test.
- [x] Producers precede consumers: registration -> fixtures -> configuration -> service -> POST -> GET -> docs -> delivery.
- [x] All referenced types have an owner and signature before later use.
- [x] Upload byte, media type, decoded dimension, malformed input, no-result, provider failure, and cancellation paths are assigned.
- [x] Spring multipart and service-level oversize paths are both assigned.
- [x] New module registration, CI, non-publication, BOM/catalog, Kover/Codecov, benchmark, and heavyweight runtime decisions have explicit evidence.
- [x] English/Korean README parity and three rendered diagram pairs are assigned.
- [x] No unresolved `TODO`, `TBD`, placeholder hash, command, endpoint, status, or response field remains.
- [x] Merge is separated from plan approval, PR creation, and CI completion.
