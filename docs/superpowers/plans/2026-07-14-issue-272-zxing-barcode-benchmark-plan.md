# ZXing Barcode Extraction Benchmark 구현 계획

> **Agentic worker 필수 지침:** 이 계획은 task 단위로 구현한다. 구현 표면은 superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans를 사용한다. 진행 추적에는 checkbox(`- [ ]`) 문법을 사용한다.

**목표:** Immutable QR, Code 128, no-result PNG fixture에 대해 재현 가능한 ZXing barcode extraction latency와 throughput evidence를 추가한다.

**아키텍처:** 기존 benchmark module이 main source set에서 provider-neutral strict fixture manifest loader를 소유하고, benchmark source set에서 ZXing-backed benchmark class를 소유한다. 두 kotlinx-benchmark configuration은 같은 parameterized extraction method를 실행하고, 각각 fresh JSON report 하나를 staging한 뒤 environment와 fixture provenance가 포함된 append-only accepted run 하나를 승격한다.

**기술 스택:** Kotlin 2.4, Java 25, Gradle 9.6, kotlinx-benchmark/JMH, kotlinx.serialization JSON, Scrimage `ImmutableImage`, ZXing provider, JUnit 5, bluetape4k assertion, Gradle TestKit.

---

## 승인된 계약

- Issue: [#272](https://github.com/bluetape4k/bluetape4k-image/issues/272)
- Spec: `docs/superpowers/specs/2026-07-14-issue-272-zxing-barcode-benchmark-design.md`
- Repository: `bluetape4k/bluetape4k-image`
- Base: `origin/develop`
- Head: `perf/issue-272-zxing-barcode-benchmark`
- 모든 pre-PR gate가 통과한 뒤 PR 생성을 허용한다.
- Merge에는 fresh merge-ready approval이 필요하다.
- Heavy command와 두 benchmark mode는 sequential로 실행한다.

## 파일 Responsibility Map

| Path | 책임 |
|---|---|
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkModels.kt` | Strict serializable manifest/scenario/expectation model |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkFixtures.kt` | Classpath path/size/hash/dimension validation과 immutable image loading |
| `benchmark/images-benchmark/src/main/resources/bench/barcode/manifest.json` | 정확한 세 fixture provenance와 expectation contract |
| `benchmark/images-benchmark/src/main/resources/bench/barcode/*.png` | Immutable QR, Code 128, blank input |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ZxingBarcodeExtractionBenchmark.kt` | Trial setup과 timed provider extraction만 담당 |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkFixturesTest.kt` | Manifest, resource, security-boundary, provider expectation test |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkContractTest.kt` | Source/configuration/task/evidence lifecycle contract |
| `benchmark/images-benchmark/build.gradle.kts` | Provider configuration, latency/throughput config, fresh report staging, immutable promotion |
| `benchmark/images-benchmark/docs/barcode-extraction-2026-07-14.md` | 상세 run, result, interpretation report |
| `benchmark/images-benchmark/docs/raw/issue-272-20260714-macos-arm64-01/` | Accepted latency, throughput, fixture manifest, run manifest |
| `benchmark/images-benchmark/README.md` / `README.ko.md` | 동등하고 간결한 user-facing benchmark summary |
| `docs/review/2026-07-14-issue-272-zxing-barcode-benchmark-*.md` | Plan/code review convergence evidence |
| `docs/lessons/2026-07-14-issue-272-zxing-barcode-benchmark.md` | Durable benchmark-fixture/evidence lesson |

## Acceptance Traceability

| Issue/spec requirement | Tasks | Proof |
|---|---|---|
| Repository-supported benchmark task | 2, 3 | `tasks --all`, TestKit contract, task execution |
| Immutable deterministic fixture | 1 | fixed PNG, strict manifest, SHA/dimension/provider test |
| QR, linear, success, no-result case | 1, 2 | 정확한 scenario set과 accepted row 여섯 개 |
| Loading/setup과 분리된 decode | 2 | benchmark source contract와 `@Setup` boundary |
| Latency와 throughput | 2, 4 | `avgt ms/op`와 `thrpt ops/s` raw JSON |
| Command/environment/raw/table/caveat | 3, 4, 5 | accepted run manifest, detailed report, README locale |
| Provider ranking/generalization 금지 | 5 | report와 README language review |

## Risk Prediction

| Risk | Signal | Mitigation | Rollback/rerun point |
|---|---|---|---|
| Timed operation에 loading이 섞임 | benchmark method가 resource/bytes/image factory를 참조 | source contract가 reader/image/options field와 extraction call만 허용 | Task 2 RED test로 돌아가 두 mode를 다시 실행 |
| Fixture drift 발생 | hash/dimension/payload mismatch | measurement 전에 loader/provider setup을 fail 처리 | review된 새 fixture set을 다시 생성하고 accepted raw evidence는 절대 편집하지 않음 |
| Provider dependency가 published main surface로 누출 | `implementation(project(":bluetape4k-images-barcode-zxing"))` 등장 | exact configuration contract가 benchmark/test dependency만 허용 | build change를 되돌리고 publication dependency inspection 재실행 |
| Stale JMH report가 승격됨 | report timestamp가 task start보다 이르거나 row contract가 다름 | fresh-start timestamp, exact single report, mode/unit/row validator | failed build staging만 삭제하고 새 run id로 재실행 |
| Accepted evidence 덮어쓰기 | target run directory가 이미 존재 | append-only finalizer가 기존 target을 거부 | 다음 sequence run id를 선택하고 accepted file은 절대 교체하지 않음 |
| 짧은 run을 과해석 | docs가 host/mode/caveat wording을 누락 | documentation contract test와 review lens | docs를 보수하며 bytes가 그대로면 measurement rerun은 불필요 |

### Task 1: Immutable Fixture Contract 고정

**Complexity:** High  
**Depends on:** approved spec과 clean baseline
**Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, `references/testing.md`  
**Files:** model/loader file 두 개, fixture test, manifest, PNG 세 개 생성
**Expected DoD:** strict loader test가 정확히 세 immutable fixture, path/size/hash/dimension bounds, QR/Code 128 expectation, blank no-result behavior를 증명한다.

- [ ] **Step 1: Failing manifest와 fixture test 작성**

bluetape4k assertion을 사용하는 focused test로 `BarcodeBenchmarkFixturesTest.kt`를 작성한다:

```kotlin
class BarcodeBenchmarkFixturesTest {

    @Test
    fun `canonical manifest contains QR Code 128 and no result fixtures`() {
        val manifest = BarcodeBenchmarkFixtures.loadManifest()

        manifest.fixtures.map(BarcodeBenchmarkFixtureEntry::scenario)
            .shouldBeEqualTo(BarcodeBenchmarkScenario.entries.toList())
    }

    @Test
    fun `canonical fixtures match bytes dimensions and provider expectations`() {
        val reader = ZxingBarcodeReader()

        BarcodeBenchmarkScenario.entries.forEach { scenario ->
            val fixture = BarcodeBenchmarkFixtures.load(scenario)
            val results = reader.readBarcodes(fixture.image, fixture.options())
            fixture.verify(results)
        }
    }

    @Test
    fun `fixture resource path rejects traversal absolute paths and oversized bytes`() {
        val validBytes = byteArrayOf(1, 2, 3)

        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.loadForTest(
                manifestJson("../secret.png"),
                BarcodeBenchmarkScenario.QR,
                mapOf("../secret.png" to validBytes),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.loadForTest(
                manifestJson("/tmp/secret.png"),
                BarcodeBenchmarkScenario.QR,
                mapOf("/tmp/secret.png" to validBytes),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.loadForTest(
                manifestJson("bench/barcode/qr.png"),
                BarcodeBenchmarkScenario.QR,
                mapOf("bench/barcode/qr.png" to ByteArray(1_048_577)),
            )
        }
    }

    private fun manifestJson(qrResource: String): ByteArray =
        """
        {
          "schemaVersion": 1,
          "hashAlgorithm": "SHA-256",
          "fixtures": [
            {"scenario":"qr","resource":"$qrResource","width":1,"height":1,"sha256":"${"0".repeat(64)}","expectedText":"qr","expectedFormat":"QR_CODE","provenance":"test"},
            {"scenario":"code-128","resource":"bench/barcode/code-128.png","width":1,"height":1,"sha256":"${"1".repeat(64)}","expectedText":"code","expectedFormat":"CODE_128","provenance":"test"},
            {"scenario":"no-result","resource":"bench/barcode/no-result.png","width":1,"height":1,"sha256":"${"2".repeat(64)}","expectEmpty":true,"provenance":"test"}
          ]
        }
        """.trimIndent().toByteArray()
}
```

Synthetic manifest helper는 test 내부 private로 유지하고, duplicate id, unknown scenario, wrong hash, wrong dimension, missing resource, malformed strict JSON을 각각 설명적인 별도 test로 다룬다.

- [ ] **Step 2: Focused test를 실행하고 RED 기록**

다음을 실행한다:

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests 'io.bluetape4k.images.benchmark.BarcodeBenchmarkFixturesTest' \
  --console=plain
```

예상 결과는 FAIL이다. `BarcodeBenchmarkFixtures`, manifest model, canonical resource가 아직 없기 때문이다. 이 missing contract와 무관한 syntax/import failure는 RED로 인정하기 전에 먼저 고친다.

- [ ] **Step 3: Strict serializable manifest model 구현**

`BarcodeBenchmarkModels.kt`를 다음 형태로 생성한다:

```kotlin
@Serializable
internal enum class BarcodeBenchmarkScenario(val value: String) {
    @SerialName("qr") QR("qr"),
    @SerialName("code-128") CODE_128("code-128"),
    @SerialName("no-result") NO_RESULT("no-result"),
}

@Serializable
internal data class BarcodeBenchmarkFixtureManifest(
    val schemaVersion: Int,
    val hashAlgorithm: String,
    val fixtures: List<BarcodeBenchmarkFixtureEntry>,
): java.io.Serializable {
    init {
        require(schemaVersion == 1) { "unsupported barcode fixture schemaVersion: $schemaVersion" }
        require(hashAlgorithm == "SHA-256") { "unsupported barcode fixture hashAlgorithm: $hashAlgorithm" }
        require(fixtures.map { it.scenario }.toSet() == BarcodeBenchmarkScenario.entries.toSet()) {
            "barcode fixture scenarios must be exactly ${BarcodeBenchmarkScenario.entries.map { it.value }}"
        }
        require(fixtures.map { it.scenario }.distinct().size == fixtures.size) {
            "barcode fixture scenarios must be unique"
        }
    }

    companion object {
        @JvmField val serialVersionUID: Long = 1L
    }
}

@Serializable
internal data class BarcodeBenchmarkFixtureEntry(
    val scenario: BarcodeBenchmarkScenario,
    val resource: String,
    val width: Int,
    val height: Int,
    val sha256: String,
    val expectedText: String? = null,
    val expectedFormat: String? = null,
    val expectEmpty: Boolean = false,
    val provenance: String,
): java.io.Serializable {
    init {
        require(resource.startsWith("bench/barcode/")) { "barcode fixture resource must stay under bench/barcode/" }
        require(!resource.startsWith("/") && ".." !in resource.split('/')) {
            "barcode fixture resource must be normalized and relative: $resource"
        }
        require(width > 0 && height > 0) { "barcode fixture dimensions must be positive" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "barcode fixture sha256 must be lowercase hexadecimal" }
        require(provenance.isNotBlank()) { "barcode fixture provenance must not be blank" }
        require(expectEmpty.xor(expectedText != null && expectedFormat != null)) {
            "barcode fixture must define exactly one success or empty expectation"
        }
    }

    companion object {
        @JvmField val serialVersionUID: Long = 1L
    }
}
```

Positive dimension, 64자리 lowercase hex hash, nonblank provenance, success expectation XOR `expectEmpty`, matching scenario/format에 대한 init validation을 추가한다.

- [ ] **Step 4: Bounded fixture loader 구현**

`BarcodeBenchmarkFixtures.kt`를 생성한다. Strict `Json`, `MessageDigest`, `immutableImageOf(bytes)`, 그리고 negative test용 injectable resource reader를 사용한다:

```kotlin
internal object BarcodeBenchmarkFixtures {
    private const val MANIFEST_RESOURCE = "bench/barcode/manifest.json"
    private const val MAX_FIXTURE_BYTES = 1_048_576
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
    }

    internal fun loadManifest(): BarcodeBenchmarkFixtureManifest =
        decodeManifest(requireResource(MANIFEST_RESOURCE))

    internal fun load(scenario: BarcodeBenchmarkScenario): BarcodeBenchmarkFixture =
        load(scenario, ::classpathResource)

    internal fun loadForTest(
        manifestBytes: ByteArray,
        scenario: BarcodeBenchmarkScenario,
        resources: Map<String, ByteArray>,
    ): BarcodeBenchmarkFixture {
        val manifest = decodeManifest(manifestBytes)
        return load(manifest, scenario, resources::get)
    }

    private fun load(
        scenario: BarcodeBenchmarkScenario,
        resourceReader: (String) -> ByteArray?,
    ): BarcodeBenchmarkFixture = load(loadManifest(), scenario, resourceReader)

    private fun load(
        manifest: BarcodeBenchmarkFixtureManifest,
        scenario: BarcodeBenchmarkScenario,
        resourceReader: (String) -> ByteArray?,
    ): BarcodeBenchmarkFixture {
        val entry = manifest.fixtures.single { it.scenario == scenario }
        val bytes = requireNotNull(resourceReader(entry.resource)) { "barcode fixture resource is missing: ${entry.resource}" }
        require(bytes.size in 1..MAX_FIXTURE_BYTES) { "barcode fixture byte size is out of bounds: ${entry.resource}" }
        require(sha256(bytes) == entry.sha256) { "barcode fixture SHA-256 differs: ${entry.resource}" }
        val image = immutableImageOf(bytes)
        require(image.width == entry.width && image.height == entry.height) {
            "barcode fixture dimensions differ: ${entry.resource}"
        }
        return BarcodeBenchmarkFixture(entry, image)
    }
}
```

`BarcodeBenchmarkFixture`는 non-serializable `ImmutableImage` runtime value를 소유하므로 일반 internal class로 정의한다:

```kotlin
internal class BarcodeBenchmarkFixture(
    internal val entry: BarcodeBenchmarkFixtureEntry,
    internal val image: ImmutableImage,
) {
    internal fun options(): BarcodeOptions =
        entry.expectedFormat?.let { format -> BarcodeOptions(formats = setOf(BarcodeFormat.valueOf(format))) }
            ?: BarcodeOptions()

    internal fun verify(results: List<BarcodeResult>) {
        if (entry.expectEmpty) {
            require(results.isEmpty()) { "barcode fixture must produce no result: ${entry.scenario.value}" }
        } else {
            val result = results.single()
            require(result.text == entry.expectedText) { "barcode fixture payload differs: ${entry.scenario.value}" }
            require(result.format == BarcodeFormat.valueOf(requireNotNull(entry.expectedFormat))) {
                "barcode fixture format differs: ${entry.scenario.value}"
            }
        }
    }
}
```

`BarcodeBenchmarkFixture.options()`와 `verify(results)`는 success case에서 pinned string을 `BarcodeFormat.valueOf`로 parsing하고, text/format이 일치하는 result가 정확히 하나만 있도록 요구하며, `NO_RESULT`에서는 empty list를 요구하도록 구현한다. 기존 hash helper는 codec-matrix 전용이라 두 harness를 coupling하지 않고 classpath byte를 검증할 수 없으므로 raw `MessageDigest`만 사용한다.

- [ ] **Step 5: 기존 provider test fixture로 immutable PNG input 생성**

`images-barcode-zxing/src/test/kotlin/io/bluetape4k/images/barcode/zxing/GenerateBarcodeBenchmarkFixturesTest.kt`를 임시로 만들고 generator test 하나를 둔다:

```kotlin
class GenerateBarcodeBenchmarkFixturesTest {
    @Test
    fun `generate reviewed benchmark fixtures`() {
        val output = Path.of(requireNotNull(System.getProperty("barcode.fixture.output")))
        require(Files.notExists(output)) { "barcode fixture output already exists: $output" }
        Files.createDirectories(output)
        val qr = output.resolve("qr.png")
        val code128 = output.resolve("code-128.png")
        val noResult = output.resolve("no-result.png")
        ZxingBarcodeImageFixtures.barcodeImage("bluetape4k-issue-272-qr", ZxingFormat.QR_CODE)
            .forWriter(PngWriter.MaxCompression).write(qr)
        ZxingBarcodeImageFixtures.barcodeImage("BLUETAPE4K-272", ZxingFormat.CODE_128)
            .forWriter(PngWriter.MaxCompression).write(code128)
        BarcodeTestFixtures.blankImage(ImageDimensions(220, 220))
            .forWriter(PngWriter.MaxCompression).write(noResult)
        output.resolve("manifest.json").writeText(
            """
            {
              "schemaVersion": 1,
              "hashAlgorithm": "SHA-256",
              "fixtures": [
                {
                  "scenario": "qr",
                  "resource": "bench/barcode/qr.png",
                  "width": 220,
                  "height": 220,
                  "sha256": "${sha256(qr)}",
                  "expectedText": "bluetape4k-issue-272-qr",
                  "expectedFormat": "QR_CODE",
                  "provenance": "ZXing MultiFormatWriter 3.5.4 QR_CODE, 220x220; Scrimage PngWriter max compression"
                },
                {
                  "scenario": "code-128",
                  "resource": "bench/barcode/code-128.png",
                  "width": 360,
                  "height": 120,
                  "sha256": "${sha256(code128)}",
                  "expectedText": "BLUETAPE4K-272",
                  "expectedFormat": "CODE_128",
                  "provenance": "ZXing MultiFormatWriter 3.5.4 CODE_128, 360x120; Scrimage PngWriter max compression"
                },
                {
                  "scenario": "no-result",
                  "resource": "bench/barcode/no-result.png",
                  "width": 220,
                  "height": 220,
                  "sha256": "${sha256(noResult)}",
                  "expectEmpty": true,
                  "provenance": "Deterministic white RGB image, 220x220; Scrimage PngWriter max compression"
                }
              ]
            }
            """.trimIndent() + "\n",
        )
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
```

Output property가 `benchmark/images-benchmark/src/main/resources/bench/barcode`를 가리키도록 한 번 실행한 뒤, final diff를 staging하기 전에 temporary test를 삭제한다:

```bash
./gradlew :bluetape4k-images-barcode-zxing:test \
  --tests '*GenerateBarcodeBenchmarkFixturesTest' \
  -Dbarcode.fixture.output="$PWD/benchmark/images-benchmark/src/main/resources/bench/barcode" \
  --console=plain
```

예상 결과는 PNG file 세 개와 generated strict manifest이다. `file`, `identify`, `shasum -a 256` 출력을 기록하고 generated manifest와 일치하는지 검증한다. 최종 branch에는 generator test나 provider-module diff가 남으면 안 된다.

- [ ] **Step 6: Generated strict canonical manifest audit**

`manifest.json`을 parsing하고 각 referenced file의 SHA-256과 dimension을 독립적으로 다시 계산한다. Scenario set과 expectation이 위 generator code와 정확히 일치하는지 확인한다. Mismatch가 있으면 generated file 네 개를 모두 폐기하고 Step 5를 다시 실행하며, 개별 hash나 PNG를 손으로 고치지 않는다.

- [ ] **Step 7: GREEN 실행 후 fixture contract commit**

Focused test, provider test, main compilation을 실행한다:

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests 'io.bluetape4k.images.benchmark.BarcodeBenchmarkFixturesTest' \
  :bluetape4k-images-barcode-zxing:test \
  :bluetape4k-images-benchmark:compileKotlin \
  --console=plain
git diff --check
```

예상 결과는 PASS, temporary generator file 없음, provider-module diff 없음이다. 다음처럼 commit한다:

```bash
git add benchmark/images-benchmark/src/main benchmark/images-benchmark/src/test
git commit -m "test: lock barcode benchmark fixtures"
```

### Task 2: Parameterized ZXing Benchmark와 두 Mode 추가

**Complexity:** High  
**Depends on:** Task 1  
**Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, benchmark hazard gate  
**Files:** benchmark source와 contract test 생성, benchmark Gradle dependency/configuration 수정
**Expected DoD:** 같은 extraction method가 main/published dependency surface를 바꾸지 않고, 정확히 세 scenario를 별도 `avgt ms/op`와 `thrpt ops/s` task로 노출한다.

- [ ] **Step 1: Failing benchmark configuration contract 작성**

`repositoryRoot()`에서 benchmark source와 `build.gradle.kts`를 읽고 다음을 검증하는 `BarcodeBenchmarkContractTest.kt`를 작성한다:

```kotlin
@Test
fun `barcode benchmark isolates setup and measures provider extraction only`() {
    val source = Files.readString(
        repositoryRoot().resolve(
            "benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ZxingBarcodeExtractionBenchmark.kt",
        ),
    )
    source.contains("@Param(\"qr\", \"code-128\", \"no-result\")").shouldBeEqualTo(true)
    source.contains("fun extractBarcodes()").shouldBeEqualTo(true)
    source.contains("reader.readBarcodes(image, options)").shouldBeEqualTo(true)
    source.substringAfter("@Benchmark").substringBeforeLast('}').contains("immutableImageOf").shouldBeEqualTo(false)
}

@Test
fun `latency and throughput configurations share class and fixed execution contract`() {
    val build = Files.readString(repositoryRoot().resolve("benchmark/images-benchmark/build.gradle.kts"))
    listOf("barcodeLatency", "barcodeThroughput").forEach { name ->
        build.contains("register(\"$name\")").shouldBeEqualTo(true)
    }
    build.contains("include(\".*ZxingBarcodeExtractionBenchmark.*\")").shouldBeEqualTo(true)
    build.contains("warmups = BARCODE_BENCHMARK_WARMUPS").shouldBeEqualTo(true)
    build.contains("iterations = BARCODE_BENCHMARK_ITERATIONS").shouldBeEqualTo(true)
    build.contains("add(\"benchmarkImplementation\", project(\":bluetape4k-images-barcode-zxing\"))")
        .shouldBeEqualTo(true)
    build.contains("implementation(project(\":bluetape4k-images-barcode-zxing\"))")
        .shouldBeEqualTo(false)
}
```

또한 `mode = "avgt"`/`outputTimeUnit = "ms"`와 `mode = "thrpt"`/`outputTimeUnit = "s"`가 각각의 named block에 들어 있는지 확인한다.

- [ ] **Step 2: Contract test 실행 후 RED 기록**

단일 class를 실행한다. Benchmark source와 named configuration이 없으므로 예상 결과는 FAIL이다.

- [ ] **Step 3: Benchmark/test provider dependency만 추가**

Dependency block을 다음처럼 수정한다:

```kotlin
testImplementation(project(":bluetape4k-images-barcode-zxing"))
add("benchmarkImplementation", project(":bluetape4k-images-barcode-zxing"))
```

새 catalog alias/version이나 main `implementation` dependency는 추가하지 않는다.

- [ ] **Step 4: Benchmark state와 timed method 구현**

`ZxingBarcodeExtractionBenchmark.kt`를 생성한다:

```kotlin
@State(Scope.Benchmark)
@Threads(1)
class ZxingBarcodeExtractionBenchmark {
    @Param("qr", "code-128", "no-result")
    var scenario: String = BarcodeBenchmarkScenario.QR.value

    private lateinit var reader: ZxingBarcodeReader
    private lateinit var image: ImmutableImage
    private lateinit var options: BarcodeOptions

    @Setup(Level.Trial)
    fun setup() {
        val fixture = BarcodeBenchmarkFixtures.load(
            BarcodeBenchmarkScenario.entries.single { it.value == scenario },
        )
        reader = ZxingBarcodeReader()
        image = fixture.image
        options = fixture.options()
        fixture.verify(reader.readBarcodes(image, options))
    }

    @Benchmark
    fun extractBarcodes(): List<BarcodeResult> =
        reader.readBarcodes(image, options)
}
```

Setup exclusion, 두 Gradle task, metric direction, single-result provider boundary를 설명하는 KDoc을 추가한다. 이번 Epic의 code comment policy에 맞춰 실제 구현 시 KDoc은 한국어로 작성한다. `com.google.zxing`은 import하지 않는다.

- [ ] **Step 5: Fixed configuration 두 개 등록**

Constant와 named configuration을 추가한다:

```kotlin
val BARCODE_BENCHMARK_WARMUPS = 3
val BARCODE_BENCHMARK_ITERATIONS = 5
val BARCODE_BENCHMARK_ITERATION_SECONDS = 1L

register("barcodeLatency") {
    include(".*ZxingBarcodeExtractionBenchmark.*")
    warmups = BARCODE_BENCHMARK_WARMUPS
    iterations = BARCODE_BENCHMARK_ITERATIONS
    iterationTime = BARCODE_BENCHMARK_ITERATION_SECONDS
    iterationTimeUnit = "s"
    mode = "avgt"
    outputTimeUnit = "ms"
    reportFormat = "json"
    advanced("jvmForks", 1)
}

register("barcodeThroughput") {
    include(".*ZxingBarcodeExtractionBenchmark.*")
    warmups = BARCODE_BENCHMARK_WARMUPS
    iterations = BARCODE_BENCHMARK_ITERATIONS
    iterationTime = BARCODE_BENCHMARK_ITERATION_SECONDS
    iterationTimeUnit = "s"
    mode = "thrpt"
    outputTimeUnit = "s"
    reportFormat = "json"
    advanced("jvmForks", 1)
}
```

- [ ] **Step 6: GREEN 실행, benchmark source set compile, task 검증**

다음을 실행한다:

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests 'io.bluetape4k.images.benchmark.BarcodeBenchmarkContractTest' \
  :bluetape4k-images-benchmark:benchmarkClasses \
  :bluetape4k-images-benchmark:tasks --all \
  --console=plain
```

예상 결과는 PASS이며 정확한 generated task `benchmarkBarcodeLatencyBenchmark`와 `benchmarkBarcodeThroughputBenchmark`가 존재해야 한다. Dependency output을 확인해 provider가 benchmark/test configuration에만 나타나는지 검증한다:

```bash
./gradlew :bluetape4k-images-benchmark:dependencies \
  --configuration runtimeClasspath --console=plain
./gradlew :bluetape4k-images-benchmark:dependencies \
  --configuration benchmarkRuntimeClasspath --console=plain
```

예상 결과는 `runtimeClasspath`에 `bluetape4k-images-barcode-zxing`이 없고, `benchmarkRuntimeClasspath`에는 있는 것이다.

- [ ] **Step 7: Benchmark behavior commit**

```bash
git add benchmark/images-benchmark/build.gradle.kts \
  benchmark/images-benchmark/src/benchmark \
  benchmark/images-benchmark/src/test
git commit -m "perf: add ZXing barcode benchmarks"
```

### Task 3: Fresh-report Validation과 Append-only Promotion 추가

**Complexity:** High  
**Depends on:** Task 2  
**Pattern skills:** `test-driven-development`, benchmark hazard gate  
**Files:** contract test와 `build.gradle.kts` 확장
**Expected DoD:** 두 task가 각각 fresh exact-three-row report 하나를 staging하고, finalization이 environment와 fixture provenance를 포함한 immutable run 하나만 승격한다.

- [ ] **Step 1: Failing lifecycle contract test 추가**

`BarcodeBenchmarkContractTest`를 확장해 다음을 요구한다:

- 기존 safe pattern을 따르는 run-id property `barcode.benchmark.runId`
- Finalization에만 필요한 CPU property `barcode.benchmark.cpu`
- 두 generated task가 각각 별도로 기록하는 fresh report timestamp
- Benchmark name, scenario, mode, unit, thread, fork, warmup, measurement, positive finite score에 대한 exact row validation
- Run directory 아래 staged filename `latency.json`과 `throughput.json`
- 이미 존재하는 final target `docs/raw/{validatedRunId}` 거부
- Command, host, JVM, ZXing version, fixture manifest hash, raw JSON hash를 담는 run manifest field

Class를 실행하고, missing lifecycle implementation에서 발생한 실패만 RED로 인정한다.

- [ ] **Step 2: Validated run/report directory 추가**

기존 codec-matrix safety pattern을 따른 Gradle provider를 추가한다:

```kotlin
val barcodeBenchmarkRunId = providers.gradleProperty("barcode.benchmark.runId")
val barcodeBenchmarkCpu = providers.gradleProperty("barcode.benchmark.cpu")
val barcodeBenchmarkRunIdPattern = Regex("issue-272-[0-9]{8}-[a-z0-9-]{3,40}")
val barcodeBenchmarkRunDirectory = barcodeBenchmarkRunId.flatMap { runId ->
    require(barcodeBenchmarkRunIdPattern.matches(runId)) { "invalid barcode benchmark run ID: $runId" }
    layout.buildDirectory.dir("barcode-benchmark/$runId")
}
```

Provider와 task input을 사용한다. 관련 없는 Gradle configuration 중에는 property를 resolve하지 않는다.

- [ ] **Step 3: Mode별 fresh report 하나를 validate하고 stage**

`JsonSlurper`를 사용하는 local `validateBarcodeBenchmarkReport(report, mode, unit)` function을 구현한다. 이 함수는 JSON array가 정확히 세 row이고 scenario set이 `qr`, `code-128`, `no-result`인지 요구해야 한다. 모든 row는 expected benchmark class/method, JMH mode/unit, thread 1, fork 1, warmup 3, measurement 5, finite positive score를 가져야 한다.

`afterEvaluate`에서 두 generated task를 sequential로 구성한다. `doFirst`는 이미 존재하는 staged file을 거부하고 `Instant.now()`를 기록한다. `doLast`는 modified time이 기록된 start보다 이르지 않은 `benchmark.json`을 정확히 하나만 찾고, 이를 validate한 뒤 run directory에 `latency.json` 또는 `throughput.json`으로 atomic copy한다.

- [ ] **Step 4: Append-only finalization 추가**

`finalizeBarcodeBenchmarkEvidence`를 등록한다. 이 task는 다음을 수행해야 한다:

1. Validated run id와 nonblank CPU description을 요구한다.
2. 두 staged JSON file을 요구하고 다시 validate한다.
3. Canonical fixture manifest를 요구하고 모든 SHA-256 값을 계산한다.
4. 이미 존재하는 `docs/raw/{validatedRunId}` target을 거부한다.
5. Temporary sibling directory를 만든다.
6. `latency.json`, `throughput.json`, `fixture-manifest.json`을 복사한다.
7. Exact command, OS/arch, Java vendor/version, processor count/CPU description, ZXing catalog version, fixture-manifest hash, raw hash, mode, unit, timing contract를 포함하는 pretty strict `run-manifest.json`을 Groovy `JsonOutput`으로 작성한다.
8. 완성된 temporary directory를 accepted target으로 atomically move한다.
9. 실패 시 accepted run은 건드리지 않고 temporary directory만 삭제한다.

Append-only collision checking은 explicit finalization attempt마다 실행되어야 하므로 task에 `outputs.upToDateWhen { false }`를 지정한다.

- [ ] **Step 5: GREEN과 failure-path check 실행**

Contract test와 `tasks --all`을 실행한다. Synthetic TestKit fixture로 invalid run id, missing raw file, duplicate target, wrong row set, wrong mode/unit이 실패하는지 증명한다. 예상 결과는 모두 PASS이며, TestKit이 temporary project copy를 사용하므로 test가 `docs/raw/` 아래 file을 만들면 안 된다.

- [ ] **Step 6: Evidence lifecycle support commit**

```bash
git add benchmark/images-benchmark/build.gradle.kts \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkContractTest.kt
git commit -m "test: guard barcode benchmark evidence"
```

### Task 4: Benchmark Evidence 실행과 승인

**Complexity:** Medium  
**Depends on:** Task 3과 clean targeted test
**Pattern skills:** benchmark hazard gate, `verification-before-completion` for evidence claims  
**Files:** finalizer를 통해서만 accepted raw directory 생성
**Expected DoD:** Java 25 run 하나가 같은 immutable fixture/environment manifest에 묶인 latency row 세 개와 throughput row 세 개를 생성한다.

- [ ] **Step 1: Host contract 기록**

다음을 기록한다:

```bash
sw_vers
uname -m
sysctl -n machdep.cpu.brand_string
JAVA25=$(/usr/libexec/java_home -v 25)
"$JAVA25/bin/java" -version
./gradlew --version
```

Run id는 `issue-272-20260714-macos-arm64-01`을 사용한다. 해당 id의 accepted evidence가 이미 존재하면 마지막 numeric sequence만 증가시키고 report path를 업데이트한다. 기존 evidence는 절대 덮어쓰지 않는다.

- [ ] **Step 2: Latency를 sequential로 실행**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeLatencyBenchmark \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  --console=plain
```

예상 결과는 `avgt`, `ms/op`, 세 scenario를 담은 staged `latency.json` 하나이다.

- [ ] **Step 3: Latency 종료 후에만 throughput 실행**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeThroughputBenchmark \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  --console=plain
```

예상 결과는 `thrpt`, `ops/s`, 세 scenario를 담은 staged `throughput.json` 하나이다. Retry-only pass가 있으면 계속 진행하기 전에 원인을 조사한다.

- [ ] **Step 4: Accepted run을 한 번만 finalize**

```bash
CPU="$(sysctl -n machdep.cpu.brand_string)"
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:finalizeBarcodeBenchmarkEvidence \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  -Pbarcode.benchmark.cpu="$CPU" \
  --console=plain
```

예상 결과는 정확히 네 file을 가진 accepted directory와 내부적으로 일치하는 hash이다. 같은 id로 두 번째 finalization을 실행하면 해당 file을 변경하지 않고 실패해야 한다.

- [ ] **Step 5: 여섯 row를 audit하고 raw evidence commit**

두 raw JSON file을 parsing하고 scenario, score, error, mode, unit을 출력한다. `git diff --check`, fixture-manifest equality, run-manifest hash, absolute user path 또는 secret-like value 부재를 검증한다. 다음처럼 commit한다:

```bash
git add benchmark/images-benchmark/docs/raw/issue-272-20260714-macos-arm64-01
git commit -m "perf: record barcode benchmark evidence"
```

### Task 5: Publish the Result Report and README Locale Parity

**Complexity:** Medium  
**Depends on:** Task 4 accepted evidence  
**Pattern skills:** `bluetape-writer` for Korean prose; chart N/A from approved spec  
**Files:** create detailed report; modify both benchmark README files  
**Expected DoD:** commands, environment, hashes, six-row table, directions, and caveats are accurate and equivalent across user-facing locales.

- [ ] **Step 1: Write the detailed English report from raw JSON**

Create `docs/barcode-extraction-2026-07-14.md` with sections:

- environment and exact commands;
- immutable fixture table with dimensions, payload class, and hashes;
- one table containing scenario, latency `ms/op ± error`, throughput `ops/s ±
  error`, and expected result;
- measurement boundary stating PNG load/decode is setup-only;
- interpretation that distinguishes QR, linear, and empty-search work but does
  not rank providers or generalize beyond the host;
- links to the accepted run directory and issue #272.

Do not derive throughput as the reciprocal of latency; use each raw mode's
observed score.

- [ ] **Step 2: Add equivalent concise README sections**

Update `README.md` and `README.ko.md` together. Each section contains the same
three scenario rows, metric directions, report/raw links, and local-snapshot
caveat. Korean prose must be natural technical Korean, not a literal sentence
mapping.

- [ ] **Step 3: Record the chart N/A decision**

In the report/review evidence, state: one provider plus three workload shapes
and two incompatible metric units makes a table clearer than a chart. Confirm no
new SVG/PNG asset or stale README image link exists.

- [ ] **Step 4: Verify documentation and commit**

Run a script that parses raw JSON and compares every displayed numeric value and
link target in the report/README files. Run `git diff --check` and inspect both
locales. Commit:

```bash
git add benchmark/images-benchmark/README.md \
  benchmark/images-benchmark/README.ko.md \
  benchmark/images-benchmark/docs/barcode-extraction-2026-07-14.md
git commit -m "docs: publish barcode benchmark results"
```

### Task 6: Complete Verification, Review, Lesson, and PR Delivery

**Complexity:** High  
**Depends on:** Tasks 1-5  
**Pattern skills:** `verification-before-completion`, Kotlin checklist, Type A verifier/review references  
**Files:** create code-review and lesson artifacts; PR body only after gates pass  
**Expected DoD:** exact approved spec/plan, Kotlin/hazard checklists, P0/P1 convergence, lesson, branch publication, PR metadata, and CI are complete before merge-ready reporting.

- [ ] **Step 1: Run targeted validation from a clean test state**

```bash
./gradlew :bluetape4k-images-benchmark:cleanTest \
  :bluetape4k-images-benchmark:test \
  :bluetape4k-images-benchmark:benchmarkClasses \
  :bluetape4k-images-barcode-api:test \
  :bluetape4k-images-barcode-zxing:test \
  --no-build-cache --console=plain
```

Expected: all affected tests PASS with recorded counts.

- [ ] **Step 2: Run proportional repository checks**

```bash
./gradlew :bluetape4k-images-benchmark:build \
  :bluetape4k-images-benchmark:tasks --all \
  projects detekt --console=plain
git diff --check
```

Inspect generated task names, dependency configurations, main/publication
surface, untracked files, and the absence of module/BOM/catalog/workflow/Kover
changes. Native/JNI, OCR, containers, and chart rendering are evidence-backed
N/A for this pure-JVM existing-module change. Coroutines, cancellation,
concurrent shared state, HTTP, Spring, Exposed, and lifecycle resource ownership
are also N/A because the timed API is synchronous, stateless per call, and uses
an immutable preloaded image without owned closeable resources.

- [ ] **Step 3: Verify exact spec/plan acceptance**

Load the Type A verifier checklist. Map all acceptance rows to current files,
tests, accepted raw evidence, and documentation. A missing or divergent row is
`NEEDS FIX`; a material design change is `NEEDS REVIEW SCOPE`.

- [ ] **Step 4: Run six review perspectives and integrate**

Review performance, stability, security, Ops, developer/API, and user/caller
against the full `origin/develop...HEAD` diff. Because the active interface lacks
an `agent_type` field, use model-routing's explicit main-session fallback and
record that limitation. Create
`docs/review/2026-07-14-issue-272-zxing-barcode-benchmark-code-review.md`, fix
P0/P1, rerun affected proof, and finish at P0=0/P1=0.

- [ ] **Step 5: Write and commit the durable lesson**

Create `docs/lessons/2026-07-14-issue-272-zxing-barcode-benchmark.md` covering:

- immutable fixture bytes rather than runtime generation for longitudinal
  comparisons;
- separate observed latency and throughput modes rather than reciprocal
  conversion;
- provider dependencies confined to benchmark/test configurations;
- fresh-report timestamp and append-only accepted-run guards;
- chart N/A rationale and the existing complementary-pair rule for future
  two-provider charts.

Commit review and lesson artifacts after final validation.

- [ ] **Step 6: Publish the authorized exact branch and create the PR**

After CG-01 through CG-10 and A-01 through A-09 pass:

```bash
git push -u origin perf/issue-272-zxing-barcode-benchmark
gh pr create \
  --repo bluetape4k/bluetape4k-image \
  --base develop \
  --head perf/issue-272-zxing-barcode-benchmark \
  --title 'perf(benchmark): measure ZXing barcode extraction' \
  --assignee debop
```

Write an English PR body that links `Closes #272`, explains why/what before
validation, mirrors milestone `0.4.0` and labels `test`, `performance`, and ends
with `## DoD Status`. Verify live body, metadata, and exact local/remote head.

- [ ] **Step 7: Wait for CI, refresh reviews, and stop at merge-ready**

Wait for required checks on the exact PR head. After green, reread reviews and
threads, verify the lesson and chart N/A artifact, update the final DoD body,
and report CG-16 through CG-18 as PENDING. Do not enable auto-merge.

- [ ] **Step 8: Merge and clean only after fresh approval**

After the user approves the exact merge-ready PR/head, rebase merge, verify the
merge SHA and issue closure, fast-forward the real local `develop`, then
automatically remove the clean merged worktree and local/remote feature branch.
Preserve any unrelated dirty worktree.

## Final Expected Evidence

- Three immutable fixture PNGs plus strict manifest and hashes.
- Two supported Gradle tasks and two accepted raw JSON files.
- Exactly six measured rows with correct mode/unit/direction.
- Detailed report and equivalent English/Korean README sections.
- Targeted tests, benchmark compilation/task listing, build, detekt, projects,
  diff check, dependency-surface inspection, and raw/docs parity proof.
- Spec, plan, plan review, code review, lesson, PR metadata, CI, and fresh merge
  approval evidence.

## Workflow Stop Condition

Normal execution stops after CI and live reviews converge and the exact PR/head
is reported merge-ready. Merge, local sync, and cleanup occur only after the
fresh CG-16 approval.
