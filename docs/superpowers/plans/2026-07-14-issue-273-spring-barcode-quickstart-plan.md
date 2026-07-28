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

- Default 5 MiB/16,777,216/8,192 value
- Fixed service allowlist `image/png`, `image/jpeg`, `image/webp`
- Zero/negative limit와 `maxInputBytes > Int.MAX_VALUE`가 binding/startup을 실패시킴
- Bean이 `BarcodeReader`로 선언되고 `ZxingBarcodeReader`로 backing됨
- Fixture 세 개가 context startup 중 모두 load됨

RED를 실행한다:

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeApiConfigurationTest' \
  --console=plain
```

Application/configuration/property가 아직 없으므로 예상 결과는 FAIL이다.

- [ ] **Step 2: Immutable validated property와 bean 구현**

`BarcodeApiConfiguration.kt`를 다음 형태로 생성한다:

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

`ZxingBarcodeReader` import는 이 configuration file에서만 허용한다. `@SpringBootApplication` entrypoint는 별도 file에 추가한다. Public application class와 configuration-properties class에는 이번 Epic의 code comment policy에 맞춰 한국어 KDoc을 작성한다. 이 non-published module은 public library API가 없으므로 configuration, fixture, controller, service, advice, HTTP DTO는 `internal`로 유지한다.

`application.yml`에 aligned multipart limit를 설정한다:

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

- [ ] **Step 3: GREEN 실행과 commit**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeApiConfigurationTest' \
  --console=plain
git add examples/spring-boot-barcode-api/src/main \
  examples/spring-boot-barcode-api/src/test/kotlin
git commit -m "feat: configure Spring barcode quickstart"
```

### Task 4: Coroutine-aware Extraction Service를 TDD로 구현

- **Complexity:** High
- **Depends on:** Task 3
- **Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, `kotlin-coroutines-skill`
- **Files:** models, service, service test
**Expected DoD:** Service 하나가 upload와 fixture, 모든 accepted format, pre-decode limit, provider-neutral DTO mapping, malformed normalization, cancellation propagation을 안전하게 처리한다.

- [ ] **Step 1: Bounded model test와 service behavior test 정의**

`runTest`와 injected test dispatcher를 사용하는 `BarcodeExtractionServiceTest.kt`를 작성한다. 다음 independent case를 다룬다:

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

Static mocking 대신 `dimensionProbe`와 `metadataDimensionProbe` test seam을 사용한다. `ioDispatcher`와 `cpuDispatcher`를 주입하고 file byte read는 전자를, probe/decode/reader 작업은 후자를 사용하는지 검증한다. 이 dispatcher test에서는 이름 있는 single-thread executor 두 개를 만들고 `asCoroutineDispatcher()`로 변환한 뒤, custom `MultipartFile`과 injected probe/reader에서 executing thread를 기록한다. Assertion 이후 두 dispatcher는 `use`로 닫는다. Test에서 executor thread를 leak하지 않는다.

- [ ] **Step 2: RED 실행**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeExtractionServiceTest' \
  --console=plain
```

DTO, request exception, service가 아직 없으므로 예상 결과는 FAIL이다.

- [ ] **Step 3: Bounded serializable HTTP model 추가**

`BarcodeApiModels.kt`를 생성한다:

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

Mapping은 `text`, `format`, `provider.name`만 복사해야 하며, `BarcodeResult`를 직접 serialize하면 안 된다.

- [ ] **Step 4: Service와 explicit dispatcher boundary 구현**

다음 constructor contract를 가진 `BarcodeExtractionService.kt`를 생성한다:

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

Service가 compile된 뒤 existing internal configuration을 다음처럼 확장한다:

```kotlin
@Bean
fun barcodeExtractionService(
    reader: BarcodeReader,
    properties: BarcodeExampleProperties,
): BarcodeExtractionService = BarcodeExtractionService(reader, properties)
```

`extract(file)`은 `withContext(ioDispatcher) { file.bytes }` 이전에 `isEmpty`, normalized media type, reported size를 validate하고, 이후 actual byte size를 다시 확인해야 한다. Normalized media type은 PNG, JPEG, WebP만 포함하는 internal fixed constant set에 속해야 하며 externally configurable하지 않다. `extract(bytes)`는 `cpuDispatcher` 내부에서 다음 순서로 실행해야 한다:

1. Empty/oversized byte를 거부한다.
2. Primary probe로 dimension을 해석하고, 실패하면 bounded metadata fallback을 사용한다.
3. Missing dimension은 `MALFORMED_INPUT`으로 거부한다.
4. Side 또는 pixel 초과는 `413 payload_too_large`로 거부한다.
5. `immutableImageOf(bytes).extractBarcodes(reader, BarcodeOptions())`를 호출한다.
6. Bounded result field만 mapping한다.

Catch order는 다음처럼 고정한다:

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

Suspend code 주변에는 `runCatching`을 사용하지 않는다. Caller-facing text에는 filename, content, path, backend metadata, original exception message를 포함하지 않는다.

- [ ] **Step 5: GREEN, whole module 실행과 commit**

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

Controller와 advice가 아직 없으므로 예상 결과는 404 FAIL이다.

- [ ] **Step 2: Thin POST controller 구현**

`BarcodeApiController.kt`를 생성한다:

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

Controller는 byte read, decode, provider call, exception message construction을 수행하지 않는다.

- [ ] **Step 3: Stable sanitized advice 구현**

`@RestControllerAdvice(basePackageClasses = [BarcodeApiController::class])`를 가진 `BarcodeApiExceptionHandler.kt`를 생성한다. 다음처럼 mapping한다:

| Exception/reason | Status | `error` | Fixed message |
|---|---:|---|---|
| `BarcodeRequestException` | exception status | exception error | bounded example-local message |
| `MaxUploadSizeExceededException` | 413 | `payload_too_large` | `The uploaded file exceeds the configured size limit.` |
| `MissingServletRequestPartException` for `file` | 400 | `empty_input` | `The multipart file part is required.` |
| `MALFORMED_INPUT` | 400 | `malformed_input` | `The uploaded file is not a decodable image.` |
| `UNSUPPORTED_FORMAT` | 400 | `unsupported_format` | `The requested barcode format is not supported.` |
| `PROVIDER_UNAVAILABLE` | 503 | `provider_unavailable` | `The barcode provider is unavailable.` |
| all other `BarcodeException` reasons | 500 | lowercase reason | `Barcode extraction failed.` |

`reason`은 `BarcodeException`에만 설정하고, provider failure에 대해서는 `cause`나 raw `exception.message`를 절대 반환하지 않는다.

`BarcodeApiExceptionHandlerTest.kt`를 만들고 `MaxUploadSizeExceededException`과 missing-`file` exception을 advice에 직접 호출한다. MockMvc의 synthetic `MockMultipartFile`은 embedded servlet container의 byte limit를 실행하지 않으므로, 이 test가 status와 JSON DTO mapping을 고정한다. 또한 `MALFORMED_INPUT`, `UNSUPPORTED_FORMAT`, `PROVIDER_UNAVAILABLE`, `DECODE_FAILED`, `NO_BARCODE`, `CANCELLED`, `UNKNOWN`을 parameterize해 exact status/error/reason/fixed-message mapping을 고정하고 provider exception message가 echo되지 않음을 증명한다. Real resolver limit는 Task 8의 bootRun smoke request에서 다룬다.

- [ ] **Step 4: GREEN 실행과 commit**

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationTest' \
  --tests 'io.bluetape4k.images.examples.spring.barcode.BarcodeApiExceptionHandlerTest' \
  --console=plain
git add examples/spring-boot-barcode-api/src/main/kotlin \
  examples/spring-boot-barcode-api/src/test/kotlin
git commit -m "feat: expose barcode upload endpoint"
```

### Task 6: Deterministic GET Scenario 세 개 추가

- **Complexity:** Medium
- **Depends on:** Task 5
- **Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`
- **Files:** controller and existing integration test
**Expected DoD:** Success, no-result, malformed contract가 caller-owned file 없이 재현 가능하고, 여전히 같은 service/advice를 통해 흐른다.

- [ ] **Step 1: Failing GET integration test 추가**

다음 assertion을 추가한다:

```text
GET /api/barcodes/sample     -> 200, count 1, expected QR payload
GET /api/barcodes/no-result  -> 200, count 0, results []
GET /api/barcodes/malformed  -> 400, malformed_input, MALFORMED_INPUT
```

Test는 GET response가 POST DTO/error schema와 일치함을 증명해야 한다. Delegation 증명만을 위해 Spring spy를 추가하지 않는다. Controller에는 alternate decode dependency가 없으며, final source review가 네 method 모두 단일 constructor-injected service를 호출하는지 확인해야 한다.

다음을 실행한다:

```bash
./gradlew :spring-boot-barcode-api:test \
  --tests 'io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationTest' \
  --console=plain
```

세 path 모두 404로 FAIL하는 것이 예상 결과이다.

- [ ] **Step 2: 같은 service를 통한 GET mapping 추가**

`BarcodeExampleFixtures`를 controller의 두 번째 constructor dependency로 추가한 뒤, 다음 method만 추가한다:

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

Fixture-specific decode logic을 별도로 도입하지 않는다.

- [ ] **Step 3: GREEN 실행과 commit**

```bash
./gradlew :spring-boot-barcode-api:test --console=plain
git add examples/spring-boot-barcode-api/src/main/kotlin \
  examples/spring-boot-barcode-api/src/test/kotlin
git commit -m "feat: add barcode scenario endpoints"
```

### Task 7: Bilingual Quickstart와 Rendered Diagram 게시

- **Complexity:** High
- **Depends on:** Tasks 1-6
- **Required skills:** `bluetape-diagram`, then `bluetape-writer`
- **Files:** example README, diagram SVG/PNG pair 세 개, root/provider README locale
**Expected DoD:** English 또는 Korean reader가 app을 시작하고, tested endpoint 네 개를 호출하며, limit/provider boundary를 이해하고, 같은 verified diagram을 볼 수 있다.

- [ ] **Step 1: Diagram source 생성과 asset render**

`bluetape-diagram`을 사용해 `examples/spring-boot-barcode-api/docs/images/readme-diagrams/` 아래 English-label SVG+PNG pair를 만든다:

1. `barcode-api-scenarios`: POST upload와 deterministic GET route 세 개
2. `barcode-api-architecture`: Controller -> shared service -> provider-neutral `BarcodeReader` -> ZXing, 그리고 controller 옆 fixture loader
3. `barcode-api-sequence`: validate -> IO bytes -> CPU probe/fallback -> decode -> extract -> bounded DTO/error

이 task에는 measured two-series chart가 없으므로 complementary-color chart rule은 N/A이다. Diagram은 repository palette를 사용하고 route/service/provider color를 명확히 구분해야 한다.

모든 SVG를 PNG로 render하고 PNG를 visually inspect하며 text가 clipped되지 않았는지 검증한다. Source SVG와 PNG dimension은 diagram skill과 일치시킨다.

- [ ] **Step 2: Tested command 기반 English README 작성**

Language switch, architecture, dependency, `bootRun`, configuration, 그리고 다음 exact call을 포함한 `README.md`를 작성한다:

```bash
./gradlew :spring-boot-barcode-api:bootRun
curl http://localhost:8080/api/barcodes/sample
curl http://localhost:8080/api/barcodes/no-result
curl http://localhost:8080/api/barcodes/malformed
curl -F 'file=@/path/to/image.webp;type=image/webp' \
  http://localhost:8080/api/barcodes/extract
```

Tested success/no-result/malformed JSON, PNG/JPEG/WebP allowlist, encoded/dimension default, provider-neutral API와 ZXing dependency boundary, verified QR/Code 128 scope, 그리고 unauthenticated local example을 internet에 노출하기 전 auth, rate limiting, request-log policy, malware scanning, operational limit가 필요하다는 warning을 포함한다.

- [ ] **Step 3: 모든 locale pair localize와 link**

`bluetape-writer`를 사용해 source-equivalent content와 같은 asset을 가진 자연스러운 `README.ko.md`를 작성한다. 다음을 업데이트한다:

- Root `README.md`와 `README.ko.md`의 barcode/Examples section
- Runnable quickstart link를 포함한 `images-barcode-zxing/README.md`와 `README.ko.md`

English file의 contributor-facing link text는 English로, Korean file의 link text는 자연스러운 한국어로 유지한다.

- [ ] **Step 4: Docs 검증과 commit**

Diagram skill validator를 실행한 뒤 다음을 실행한다:

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

### Task 8: Verification, Review, Lesson, PR Delivery Gate 종료

- **Complexity:** High
- **Depends on:** Tasks 1-7
- **Required skills:** `verification-before-completion`, `requesting-code-review`, `bluetape-full-feature`
- **Files:** final review, lesson, workflow evidence, PR metadata
**Expected DoD:** Current HEAD가 reproducibly green이고, P0=0/P1=0으로 review되며, lesson-complete 상태이고, issue-linked PR에 push된 뒤 CI/merge-ready approval에서 중단한다.

- [ ] **Step 1: Clean test state에서 targeted test 실행**

Sequential로 실행한다:

```bash
./gradlew :spring-boot-barcode-api:cleanTest \
  :spring-boot-barcode-api:test \
  --no-build-cache --rerun-tasks --console=plain
./gradlew :bluetape4k-images-barcode-api:test \
  :bluetape4k-images-barcode-zxing:test \
  --console=plain
```

예상 결과는 모든 test PASS이며 native/JNI, OCR, Docker, Testcontainers runtime은 사용하지 않는다.

- [ ] **Step 2: Build/static/registration verification 실행**

```bash
./gradlew :spring-boot-barcode-api:build --console=plain
./gradlew detekt --console=plain
./gradlew projects --console=plain
actionlint .github/workflows/Examples.yml
git diff --check origin/develop...HEAD
```

다음을 inspect하고 기록한다:

- Example이 settings, AGENTS, Examples matrix에 한 번씩 나타난다.
- `examples/**`가 이를 non-published로 유지한다.
- BOM/catalog coordinate, publication aggregation, Kover/Codecov artifact, main `ci.yml`, nightly production job, benchmark, native/JNI, OCR, Docker, Testcontainers change가 필요하지 않다.
- Main example code에서는 configuration만 ZXing implementation을 import한다.
- DTO가 backend metadata/raw byte를 노출하지 않는다.

- [ ] **Step 3: Application smoke-start**

`:spring-boot-barcode-api:bootRun`을 시작하고 port 8080을 기다린다. Multipart fixture upload 하나를 포함해 README route 네 개를 모두 호출하고 status/JSON을 MockMvc expectation과 비교한다. 그런 다음 5 MiB보다 큰 multipart file 하나를 보내 embedded server가 `413 payload_too_large`를 반환하는지 검증한다. Process는 clean하게 종료한다. Port collision은 product failure가 아니라 `--args='--server.port=18080'` 같은 fixed alternate port로 rerun할 조건이다.

- [ ] **Step 4: Final review 실행과 모든 P0/P1 종료**

`docs/review/2026-07-14-issue-273-spring-barcode-quickstart-code-review.md`를 생성한다. Performance, stability, security, operator/Ops, developer/API, user/caller lens와 integration을 review한다. Fix 이후 P0=0/P1=0이 될 때까지 다시 실행한다. Cancellation, WebP fallback, decompression guard, fixture ownership, sanitized error, locale parity, rendered diagram, registration N/A evidence를 명시적으로 검증한다.

- [ ] **Step 5: Type A lesson gate 충족**

`docs/lessons/2026-07-14-issue-273-spring-barcode-quickstart.md`를 만들고 다음을 포함한다:

- Context와 approved endpoint/provider boundary
- Content type, encoded byte, decoded dimension이 서로 다른 guard인 이유
- ImageIO probe가 없을 때 WebP에 metadata fallback이 필요한 이유
- Cancellation과 provider-neutral failure를 구분해 처리해야 하는 이유
- Exact verification evidence와 향후 upload example을 위한 reusable guidance

Review/lesson과 review fix는 intentional English message로 commit한다.

- [ ] **Step 6: Workflow state 검증과 PR 준비**

Run `20260714T033629Z-d8d2c59c`에 대한 active `bluetape-flow.py verify` command를 실행하고, 모든 required checkbox에 fresh evidence가 있는지 확인한다. `feat/issue-273-barcode-quickstart`를 push하고 `develop`을 target으로 PR을 만든다. PR은 다음을 만족해야 한다:

- Issue #273을 close한다.
- Live issue #273에서 milestone `0.4.0`, assignee `debop`, label `documentation`, `enhancement`를 이어받는다.
- Exact verification command/result를 나열한다.
- Non-published/BOM/Kover/benchmark/native/OCR/container N/A evidence를 명시한다.
- Diagram preview 또는 link와 lesson/review path를 포함한다.

생성 후 live PR metadata와 current head SHA를 검증한다.

- [ ] **Step 7: CI monitoring과 merge-ready 중단**

Required check를 기다린다. Check가 실패하면 live log를 inspect하고 root cause를 재현/수정한 뒤 targeted/local evidence를 다시 실행하고 push하며 새 head를 re-review한다. CI가 green이면 review와 unresolved thread를 다시 읽고 exact PR head 기준으로 diagram과 lesson을 검증한다. Merge-ready evidence를 보고하고 fresh explicit merge approval을 위해 중단한다. Auto-merge는 켜지 않는다.

- [ ] **Step 8: Fresh approval 이후에만 merge, sync, clean**

사용자가 exact merge-ready PR/head를 승인한 뒤 live head, check, review, unresolved thread를 한 번 더 읽는다. PR을 rebase-merge하고 merge SHA와 issue #273 closure를 검증하며, 실제 local `develop`을 fast-forward한 다음 clean merged worktree와 local feature branch를 자동으로 제거한다. GitHub가 remote feature branch를 아직 제거하지 않았다면 제거한다. Unrelated dirty worktree는 보존하고 cleanup evidence를 보고한다.

## Rollback

Example에는 state나 migration이 없다. Full rollback은 다음을 하나의 coherent unit으로 제거한다:

1. `examples/spring-boot-barcode-api/`;
2. the settings mapping;
3. the AGENTS module/command entries;
4. the Examples matrix row;
5. root/provider README locale links;
6. issue-specific review/lesson artifacts if the entire feature is abandoned.

Rollback 이후 `./gradlew projects`, `actionlint`, barcode API/provider test, `git diff --check`를 다시 실행한다. 기존 production barcode API/provider artifact나 issue #272 benchmark fixture는 제거하거나 변경하지 않는다.

## Plan Self-Review

- [x] 모든 issue/spec acceptance criterion이 ordered task와 command에 mapping된다.
- [x] 모든 behavioral implementation task는 focused RED test로 시작한다.
- [x] Producer가 consumer보다 앞선다: registration -> fixtures -> configuration -> service -> POST -> GET -> docs -> delivery.
- [x] 이후 사용 전에 모든 referenced type에 owner와 signature가 있다.
- [x] Upload byte, media type, decoded dimension, malformed input, no-result, provider failure, cancellation path가 배정되어 있다.
- [x] Spring multipart와 service-level oversize path가 모두 배정되어 있다.
- [x] New module registration, CI, non-publication, BOM/catalog, Kover/Codecov, benchmark, heavyweight runtime decision에 explicit evidence가 있다.
- [x] English/Korean README parity와 rendered diagram pair 세 개가 배정되어 있다.
- [x] 해결되지 않은 `TODO`, `TBD`, placeholder hash, command, endpoint, status, response field가 없다.
- [x] Merge는 plan approval, PR creation, CI completion과 분리되어 있다.
