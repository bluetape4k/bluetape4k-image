# Issue #273 Spring Boot Barcode Quickstart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a runnable, non-published Spring Boot 4 quickstart that extracts barcodes from bounded PNG/JPEG/WebP multipart uploads and exposes deterministic success, no-result, and malformed-input endpoints backed by ZXing.

**Architecture:** A dedicated `examples/spring-boot-barcode-api` module wires a provider-neutral `BarcodeReader` bean to `ZxingBarcodeReader`. A single coroutine-aware extraction service owns upload validation, dimension probing, image decoding, provider calls, and bounded DTO mapping; both the multipart POST route and three module-owned fixture GET routes call that service. Controller advice owns stable, sanitized HTTP error mapping.

**Tech Stack:** Kotlin 2.4, Java 21, Spring Boot 4 WebMVC, Kotlin coroutines, Scrimage `ImmutableImage`, bluetape4k barcode API, ZXing provider, JUnit 5, MockMvc, bluetape4k assertions, Gradle 9.x.

---

## Approved Contract

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
- Default guards: 5 MiB encoded bytes, 16,777,216 decoded pixels, 8,192 maximum side
- Success and no-result both return `200 OK`; malformed input returns sanitized `400 Bad Request` with reason `MALFORMED_INPUT`.
- The quickstart stores no upload and adds no production artifact, provider, dependency version, BOM entry, benchmark result, or public library API.
- PR creation is authorized only after implementation, verification, review, lesson, and workflow gates pass. Merge requires a later fresh merge-ready approval.
- Implementation proceeds inline in this session after plan approval, matching the user's previously selected execution mode.

## File Responsibility Map

| Path | Responsibility |
|---|---|
| `settings.gradle.kts` | Register `:spring-boot-barcode-api` and map the example directory |
| `AGENTS.md` | Add the example to the module table and targeted command list |
| `.github/workflows/Examples.yml` | Add the example test to the PR/push/daily matrix |
| `examples/spring-boot-barcode-api/build.gradle.kts` | Declare the non-published Spring/ZXing example dependencies and main class |
| `examples/spring-boot-barcode-api/src/main/kotlin/io/bluetape4k/images/examples/spring/barcode/SpringBootBarcodeApiApplication.kt` | Application entrypoint only |
| `.../BarcodeApiConfiguration.kt` | Immutable properties, `BarcodeReader`, fixture, and service beans |
| `.../BarcodeApiModels.kt` | Bounded success/error DTOs and example-local request exception |
| `.../BarcodeExampleFixtures.kt` | Fixed enum-owned classpath resources, startup validation, copy-on-read |
| `.../BarcodeExtractionService.kt` | Upload validation, byte I/O, dimension guard, decode, extraction, DTO mapping |
| `.../BarcodeApiController.kt` | One multipart POST and three fixture GET routes |
| `.../BarcodeApiExceptionHandler.kt` | Stable status/error/reason mapping without payload or stack disclosure |
| `examples/spring-boot-barcode-api/src/main/resources/application.yml` | Spring multipart request/file limits and example defaults |
| `examples/spring-boot-barcode-api/src/main/resources/barcodes/*` | Module-owned QR, blank, and malformed fixtures |
| `examples/spring-boot-barcode-api/src/test/kotlin/.../BarcodeExampleFixturesTest.kt` | Fixture hashes, dimensions, payload, empty result, and byte isolation |
| `.../BarcodeApiConfigurationTest.kt` | Property defaults/validation and provider-neutral bean wiring |
| `.../BarcodeExtractionServiceTest.kt` | Service success, formats, guards, malformed normalization, cancellation |
| `.../SpringBootBarcodeApiApplicationTest.kt` | MockMvc POST/GET/status/JSON integration contract |
| `.../BarcodeApiExceptionHandlerTest.kt` | Resolver-level multipart/missing-part error mapping without assuming MockMvc enforces container limits |
| `examples/spring-boot-barcode-api/src/test/resources/*` | Required JUnit parallelism and test logging configuration |
| `examples/spring-boot-barcode-api/README.md` / `README.ko.md` | Equivalent runnable English/Korean guide and production warnings |
| `examples/spring-boot-barcode-api/docs/images/readme-diagrams/*` | Scenario, architecture, and sequence SVG/PNG pairs |
| `README.md` / `README.ko.md` | Root barcode and Examples links |
| `images-barcode-zxing/README.md` / `README.ko.md` | Provider-to-quickstart link |
| `docs/review/2026-07-14-issue-273-spring-barcode-quickstart-*.md` | Plan and code review convergence evidence |
| `docs/lessons/2026-07-14-issue-273-spring-barcode-quickstart.md` | Required Type A lesson |

All Kotlin files below the example use package
`io.bluetape4k.images.examples.spring.barcode`.

## Acceptance Traceability

| Requirement | Tasks | Proof |
|---|---|---|
| Runnable dedicated Spring Boot module | 1, 3 | `projects`, application context test, `bootRun` smoke |
| Provider-neutral Spring bean backed by ZXing | 3 | bean type test; ZXing import confined to configuration |
| Multipart PNG/JPEG/WebP upload | 4, 5 | service format tests and MockMvc multipart tests |
| Encoded byte, decoded pixel, side, and content-type guards | 3-5 | property validation, focused service tests, HTTP status/code tests |
| Deterministic success/no-result/malformed scenarios | 2, 4, 6 | pinned fixture tests and three GET integration tests |
| One shared extraction service | 4-6 | controller constructor/source review and MockK/MockMvc behavior |
| Bounded response DTO and sanitized errors | 4, 5 | exact JSON assertions and forbidden-field assertions |
| Correct coroutine dispatch and cancellation | 4 | injected dispatcher tests and unchanged `CancellationException` |
| Bilingual docs and three rendered diagrams | 7 | locale parity review and SVG/PNG render validation |
| Complete non-published module registration | 1, 8 | settings/AGENTS/Examples/root/provider links, publication and Kover N/A evidence |
| Merge-ready workflow evidence | 8 | clean verification, P0/P1=0 review, lesson, exact-head PR checks |

## Risk Prediction

| Risk | Signal | Prevention/test | Rollback point |
|---|---|---|---|
| WebP passes the media allowlist but ImageIO cannot probe it | `probeImageDimensions` returns `null` for a valid WebP | use bounded `readImageMetadataReport` fallback; exercise a real WebP | return to Task 4 RED/GREEN |
| Compressed image bomb reaches full decode | pixel/side check occurs after `immutableImageOf` | resolve dimensions and enforce both limits before decode | revert service commit |
| Multipart limit and application limit disagree | container rejects before stable JSON handler or service reads excess bytes | keep the file limit at 5 MiB, allow request-envelope overhead, test the handler directly, and smoke-test a real oversized request | return to Tasks 3, 5, and 8 |
| Error response leaks provider or input detail | handler returns exception message/cause/filename | use fixed messages and bounded DTO; assert absence of filename, bytes, stack, metadata | return to Task 5 |
| Mutable fixture bytes leak across requests | cached `ByteArray` returned directly | clone on load and every read; mutation-isolation test | return to Task 2 |
| Cancellation is normalized as decode failure | broad `catch (Exception)` precedes cancellation | explicitly rethrow `CancellationException`; focused test | return to Task 4 |
| Provider implementation leaks into HTTP/API code | `com.google.zxing` or `ZxingBarcodeReader` appears outside configuration/test generation | dependency/source scan and bounded response DTO | revert offending task |
| New module is locally green but absent from CI | Examples matrix or settings mapping omitted | registration task first; final matrix and `projects` checks | revert Task 1 as one unit |
| Documentation drifts from route/status contract | curl path/status differs from MockMvc tests | copy exact tested examples into both locales; parity review | return to Task 7 |

### Task 1: Register the Non-Published Spring Example Skeleton

- **Complexity:** Medium
- **Depends on:** approved spec and clean baseline
- **Pattern skills:** `bluetape-kotlin-patterns`, `references/module-setup.md`
- **Files:** settings, AGENTS, Examples workflow, new Gradle build, required test resources
**Expected DoD:** Gradle discovers the module, the example has no Maven publication surface, and CI schedules its test on PR/push/daily events.

- [ ] **Step 1: Add the module build and registration chain**

Create `examples/spring-boot-barcode-api/build.gradle.kts`:

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

Add the exact settings mapping:

```kotlin
include("spring-boot-barcode-api")
project(":spring-boot-barcode-api").projectDir = file("examples/spring-boot-barcode-api")
```

Add the module table/command to `AGENTS.md`, and add this matrix entry to
`.github/workflows/Examples.yml`:

```yaml
- example: spring-boot-barcode-api
  gradle_tasks: :spring-boot-barcode-api:test
```

The existing `examples/**` path filter already covers the new directory; do not
duplicate it. Add `junit-platform.properties` and `logback-test.xml` by copying
the repository-standard contents from `examples/spring-boot-image-api`.

- [ ] **Step 2: Verify discovery and non-publication**

Run:

```bash
./gradlew projects --console=plain
./gradlew :spring-boot-barcode-api:tasks --all --console=plain
actionlint .github/workflows/Examples.yml
```

Expected:

- `:spring-boot-barcode-api` maps to `examples/spring-boot-barcode-api`.
- application, `bootRun`, and test tasks exist.
- no Maven Central publication task is introduced for the example.
- `Examples.yml` is valid and contains exactly one matrix row for the module.

- [ ] **Step 3: Commit the registration unit**

```bash
git add settings.gradle.kts AGENTS.md .github/workflows/Examples.yml \
  examples/spring-boot-barcode-api/build.gradle.kts \
  examples/spring-boot-barcode-api/src/test/resources
git commit -m "build: register Spring barcode example"
```

### Task 2: Lock the Module-Owned Fixture Contract

- **Complexity:** High
- **Depends on:** Task 1
- **Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, `references/testing.md`
- **Files:** fixture resources, loader, fixture test
**Expected DoD:** the module owns exactly three bounded resources whose hashes, dimensions, payload/no-result semantics, startup availability, and immutable copy behavior are deterministic.

- [ ] **Step 1: Write fixture tests first**

Create `BarcodeExampleFixturesTest.kt` with tests that require:

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

Also cover exactly three enum paths and missing-resource startup failure through
an internal injected `resourceLoader: (String) -> ByteArray?` constructor.

- [ ] **Step 2: Run the focused test and capture RED**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeExampleFixturesTest' \
  --console=plain
```

Expected: FAIL because fixture enum, loader, and resources do not exist. Fix
unrelated syntax/configuration errors before accepting RED.

- [ ] **Step 3: Add the fixed loader contract**

Create `BarcodeExampleFixtures.kt`:

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

Keep path construction enum-only. `loadClasspathResource` must use the class
loader, close the stream with `use`, and return no mutable cached array.

- [ ] **Step 4: Generate/copy the exact fixture bytes and verify hashes**

Create `src/main/resources/barcodes/`. Generate the 220x220 QR once with ZXing
3.5.4 `MultiFormatWriter`/`MatrixToImageWriter`, payload
`bluetape4k-barcode-quickstart`, and PNG output. Copy the already reviewed
220x220 white PNG from
`benchmark/images-benchmark/src/main/resources/bench/barcode/no-result.png`.
Create `malformed.bin` as the exact 12 ASCII bytes `not-an-image` with no newline.

Do not retain a generator in production/test sources. Before continuing, run:

```bash
shasum -a 256 \
  examples/spring-boot-barcode-api/src/main/resources/barcodes/qr.png \
  examples/spring-boot-barcode-api/src/main/resources/barcodes/no-result.png \
  examples/spring-boot-barcode-api/src/main/resources/barcodes/malformed.bin
```

Expected hashes, in order:

```text
5d048dd6769ede80f453ffb6c80fe6745092bf895c429b6104d5cc74d892c44d
86aad41769423ad85a979fefe109d00829044a1eba5d891547499413e3d9ff2b
f2e2c6db1745cc40df646dc40c385487c36e4ceb3f1d5c8d6ad1f7620af1ebae
```

- [ ] **Step 5: Run GREEN and commit**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeExampleFixturesTest' \
  --console=plain
git add examples/spring-boot-barcode-api/src/main/{kotlin,resources} \
  examples/spring-boot-barcode-api/src/test/kotlin
git commit -m "test: lock barcode quickstart fixtures"
```

Expected: PASS with the exact hashes, dimensions, QR payload, empty result, and
copy isolation.

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
