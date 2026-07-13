# ZXing Barcode Extraction Benchmark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reproducible ZXing barcode extraction latency and throughput evidence for immutable QR, Code 128, and no-result PNG fixtures.

**Architecture:** The existing benchmark module owns a provider-neutral, strict fixture manifest loader in its main source set and a ZXing-backed benchmark class in its benchmark source set. Two kotlinx-benchmark configurations execute the same parameterized extraction method, stage one fresh JSON report each, and promote one append-only accepted run with environment and fixture provenance.

**Tech Stack:** Kotlin 2.4, Java 25, Gradle 9.6, kotlinx-benchmark/JMH, kotlinx.serialization JSON, Scrimage `ImmutableImage`, ZXing provider, JUnit 5, bluetape4k assertions, Gradle TestKit.

---

## Approved Contract

- Issue: [#272](https://github.com/bluetape4k/bluetape4k-image/issues/272)
- Spec: `docs/superpowers/specs/2026-07-14-issue-272-zxing-barcode-benchmark-design.md`
- Repository: `bluetape4k/bluetape4k-image`
- Base: `origin/develop`
- Head: `perf/issue-272-zxing-barcode-benchmark`
- PR creation is authorized after all pre-PR gates pass.
- Merge requires a fresh merge-ready approval.
- Heavy commands and the two benchmark modes run sequentially.

## File Responsibility Map

| Path | Responsibility |
|---|---|
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkModels.kt` | Strict serializable manifest/scenario/expectation models |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkFixtures.kt` | Classpath path/size/hash/dimension validation and immutable image loading |
| `benchmark/images-benchmark/src/main/resources/bench/barcode/manifest.json` | Exact three-fixture provenance and expectation contract |
| `benchmark/images-benchmark/src/main/resources/bench/barcode/*.png` | Immutable QR, Code 128, and blank inputs |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ZxingBarcodeExtractionBenchmark.kt` | Trial setup and timed provider extraction only |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkFixturesTest.kt` | Manifest, resource, security-boundary, and provider expectation tests |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkContractTest.kt` | Source/configuration/task/evidence lifecycle contract |
| `benchmark/images-benchmark/build.gradle.kts` | Provider configuration, latency/throughput configs, fresh report staging, immutable promotion |
| `benchmark/images-benchmark/docs/barcode-extraction-2026-07-14.md` | Detailed run, result, and interpretation report |
| `benchmark/images-benchmark/docs/raw/issue-272-20260714-macos-arm64-01/` | Accepted latency, throughput, fixture manifest, and run manifest |
| `benchmark/images-benchmark/README.md` / `README.ko.md` | Equivalent concise user-facing benchmark summary |
| `docs/review/2026-07-14-issue-272-zxing-barcode-benchmark-*.md` | Plan and code review convergence evidence |
| `docs/lessons/2026-07-14-issue-272-zxing-barcode-benchmark.md` | Durable benchmark-fixture/evidence lesson |

## Acceptance Traceability

| Issue/spec requirement | Tasks | Proof |
|---|---|---|
| Repository-supported benchmark tasks | 2, 3 | `tasks --all`, TestKit contract, task execution |
| Immutable deterministic fixtures | 1 | fixed PNGs, strict manifest, SHA/dimension/provider tests |
| QR, linear, success, and no-result cases | 1, 2 | exact scenario set and six accepted rows |
| Decode separated from loading/setup | 2 | benchmark source contract and `@Setup` boundary |
| Latency plus throughput | 2, 4 | `avgt ms/op` and `thrpt ops/s` raw JSON |
| Command/environment/raw/table/caveats | 3, 4, 5 | accepted run manifest, detailed report, README locales |
| No provider ranking/generalization | 5 | report and README language review |

## Risk Prediction

| Risk | Signal | Mitigation | Rollback/rerun point |
|---|---|---|---|
| Loading leaks into timed operation | benchmark method references resource/bytes/image factory | source contract permits only reader/image/options fields and extraction call | return to Task 2 RED test and rerun both modes |
| Fixture drifts | hash/dimension/payload mismatch | fail loader/provider setup before measurement | regenerate a new reviewed fixture set; never edit accepted raw evidence |
| Provider dependency leaks into published main surface | `implementation(project(":bluetape4k-images-barcode-zxing"))` appears | exact configuration contract permits only benchmark/test dependencies | revert build change and rerun publication dependency inspection |
| Stale JMH report is promoted | report timestamp predates task start or row contract differs | fresh-start timestamp, exact single report, mode/unit/row validator | delete only failed build staging and rerun with a new run id |
| Accepted evidence is overwritten | target run directory already exists | append-only finalizer refuses existing target | choose the next sequence run id; never replace accepted files |
| Short run is overinterpreted | docs omit host/mode/caveat wording | documentation contract tests plus review lens | repair docs; measurement need not rerun if bytes remain unchanged |

### Task 1: Lock the Immutable Fixture Contract

**Complexity:** High  
**Depends on:** approved spec and clean baseline  
**Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, `references/testing.md`  
**Files:** create the two model/loader files, fixture test, manifest, and three PNGs  
**Expected DoD:** strict loader tests prove exactly three immutable fixtures, path/size/hash/dimension bounds, QR/Code 128 expectations, and blank no-result behavior.

- [ ] **Step 1: Write the failing manifest and fixture tests**

Create `BarcodeBenchmarkFixturesTest.kt` with focused tests using bluetape4k assertions:

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

Keep synthetic manifest helpers private to the test and cover duplicate ids,
unknown scenario, wrong hash, wrong dimensions, missing resource, and malformed
strict JSON in separate descriptive tests.

- [ ] **Step 2: Run the focused test and capture RED**

Run:

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests 'io.bluetape4k.images.benchmark.BarcodeBenchmarkFixturesTest' \
  --console=plain
```

Expected: FAIL because `BarcodeBenchmarkFixtures`, manifest models, and canonical
resources do not exist. A syntax/import failure unrelated to those missing
contracts must be fixed before accepting RED.

- [ ] **Step 3: Implement strict serializable manifest models**

Create `BarcodeBenchmarkModels.kt` with:

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

Add init validation for positive dimensions, 64 lowercase hex hash, nonblank
provenance, success expectation XOR `expectEmpty`, and matching scenario/format.

- [ ] **Step 4: Implement the bounded fixture loader**

Create `BarcodeBenchmarkFixtures.kt`. Use strict `Json`, `MessageDigest`,
`immutableImageOf(bytes)`, and an injectable resource reader for negative tests:

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

Define `BarcodeBenchmarkFixture` as a normal internal class because it owns a
non-serializable `ImmutableImage` runtime value:

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

Implement `BarcodeBenchmarkFixture.options()` and `verify(results)` so success
parses the pinned string with `BarcodeFormat.valueOf`, requires exactly one
matching text/format, and `NO_RESULT` requires an empty list. Use raw
`MessageDigest` only because the existing hash helper is
codec-matrix-specific and cannot validate classpath bytes without coupling the
two harnesses.

- [ ] **Step 5: Generate immutable PNG inputs through the existing provider test fixture**

Temporarily create
`images-barcode-zxing/src/test/kotlin/io/bluetape4k/images/barcode/zxing/GenerateBarcodeBenchmarkFixturesTest.kt` with one generator test:

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

Run it once with the output property targeting
`benchmark/images-benchmark/src/main/resources/bench/barcode`, then delete the
temporary test before staging any final diff:

```bash
./gradlew :bluetape4k-images-barcode-zxing:test \
  --tests '*GenerateBarcodeBenchmarkFixturesTest' \
  -Dbarcode.fixture.output="$PWD/benchmark/images-benchmark/src/main/resources/bench/barcode" \
  --console=plain
```

Expected: three PNG files plus the generated strict manifest. Record `file`,
`identify`, and `shasum -a 256` outputs and verify they equal the generated
manifest. The final branch must contain no generator test or provider-module
diff.

- [ ] **Step 6: Audit the generated strict canonical manifest**

Parse `manifest.json` and independently recompute each referenced file's
SHA-256 and dimensions. Confirm the scenario set and expectations exactly match
the generator code above. Any mismatch discards all four generated files and
reruns Step 5; do not hand-edit an individual hash or PNG.

- [ ] **Step 7: Run GREEN and commit the fixture contract**

Run the focused test, provider tests, and main compilation:

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests 'io.bluetape4k.images.benchmark.BarcodeBenchmarkFixturesTest' \
  :bluetape4k-images-barcode-zxing:test \
  :bluetape4k-images-benchmark:compileKotlin \
  --console=plain
git diff --check
```

Expected: PASS, no temporary generator file, and no provider-module diff.
Commit:

```bash
git add benchmark/images-benchmark/src/main benchmark/images-benchmark/src/test
git commit -m "test: lock barcode benchmark fixtures"
```

### Task 2: Add the Parameterized ZXing Benchmark and Two Modes

**Complexity:** High  
**Depends on:** Task 1  
**Pattern skills:** `test-driven-development`, `bluetape-kotlin-patterns`, benchmark hazard gate  
**Files:** create benchmark source and contract test; modify benchmark Gradle dependencies/configurations  
**Expected DoD:** the same extraction method exposes exactly three scenarios through separate `avgt ms/op` and `thrpt ops/s` tasks without changing the main/published dependency surface.

- [ ] **Step 1: Write the failing benchmark configuration contract**

Create `BarcodeBenchmarkContractTest.kt` that reads the benchmark source and
`build.gradle.kts` from `repositoryRoot()` and asserts:

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

Also assert `mode = "avgt"`/`outputTimeUnit = "ms"` and
`mode = "thrpt"`/`outputTimeUnit = "s"` occur in their named blocks.

- [ ] **Step 2: Run the contract test and capture RED**

Run the single class. Expected: FAIL because the benchmark source and named
configurations are missing.

- [ ] **Step 3: Add benchmark/test provider dependencies only**

Modify the dependency block:

```kotlin
testImplementation(project(":bluetape4k-images-barcode-zxing"))
add("benchmarkImplementation", project(":bluetape4k-images-barcode-zxing"))
```

Do not add a new catalog alias/version or main `implementation` dependency.

- [ ] **Step 4: Implement the benchmark state and timed method**

Create `ZxingBarcodeExtractionBenchmark.kt`:

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

Add English KDoc documenting setup exclusion, the two Gradle tasks, metric
directions, and the single-result provider boundary. Do not import
`com.google.zxing`.

- [ ] **Step 5: Register the two fixed configurations**

Add constants and named configurations:

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

- [ ] **Step 6: Run GREEN, compile the benchmark source set, and verify tasks**

Run:

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests 'io.bluetape4k.images.benchmark.BarcodeBenchmarkContractTest' \
  :bluetape4k-images-benchmark:benchmarkClasses \
  :bluetape4k-images-benchmark:tasks --all \
  --console=plain
```

Expected: PASS and exact generated tasks
`benchmarkBarcodeLatencyBenchmark` and
`benchmarkBarcodeThroughputBenchmark`. Inspect dependency output to confirm the
provider appears only in benchmark/test configurations:

```bash
./gradlew :bluetape4k-images-benchmark:dependencies \
  --configuration runtimeClasspath --console=plain
./gradlew :bluetape4k-images-benchmark:dependencies \
  --configuration benchmarkRuntimeClasspath --console=plain
```

Expected: `runtimeClasspath` does not contain
`bluetape4k-images-barcode-zxing`; `benchmarkRuntimeClasspath` does.

- [ ] **Step 7: Commit the benchmark behavior**

```bash
git add benchmark/images-benchmark/build.gradle.kts \
  benchmark/images-benchmark/src/benchmark \
  benchmark/images-benchmark/src/test
git commit -m "perf: add ZXing barcode benchmarks"
```

### Task 3: Add Fresh-Report Validation and Append-only Promotion

**Complexity:** High  
**Depends on:** Task 2  
**Pattern skills:** `test-driven-development`, benchmark hazard gate  
**Files:** extend contract test and `build.gradle.kts`  
**Expected DoD:** both tasks stage one fresh exact-three-row report and finalization promotes one immutable run with environment and fixture provenance.

- [ ] **Step 1: Add failing lifecycle contract tests**

Extend `BarcodeBenchmarkContractTest` to require:

- run-id property `barcode.benchmark.runId` with the existing safe pattern;
- CPU property `barcode.benchmark.cpu` required only by finalization;
- fresh report timestamp captured separately for both generated tasks;
- exact row validation for benchmark name, scenarios, mode, unit, threads,
  forks, warmups, measurements, and positive finite score;
- staged filenames `latency.json` and `throughput.json` under the run directory;
- final target `docs/raw/{validatedRunId}` rejected when it already exists;
- run manifest fields for commands, host, JVM, ZXing version, fixture manifest
  hash, and raw JSON hashes.

Run the class and accept RED only from the missing lifecycle implementation.

- [ ] **Step 2: Add validated run and report directories**

Add Gradle providers modeled on the existing codec-matrix safety pattern:

```kotlin
val barcodeBenchmarkRunId = providers.gradleProperty("barcode.benchmark.runId")
val barcodeBenchmarkCpu = providers.gradleProperty("barcode.benchmark.cpu")
val barcodeBenchmarkRunIdPattern = Regex("issue-272-[0-9]{8}-[a-z0-9-]{3,40}")
val barcodeBenchmarkRunDirectory = barcodeBenchmarkRunId.flatMap { runId ->
    require(barcodeBenchmarkRunIdPattern.matches(runId)) { "invalid barcode benchmark run ID: $runId" }
    layout.buildDirectory.dir("barcode-benchmark/$runId")
}
```

Use providers and task inputs; do not resolve the properties during unrelated
Gradle configuration.

- [ ] **Step 3: Validate and stage one fresh report per mode**

Implement a local `validateBarcodeBenchmarkReport(report, mode, unit)` function
using `JsonSlurper`. It must require a JSON array of exactly three rows and the
scenario set `qr`, `code-128`, `no-result`; every row must have the expected
benchmark class/method, JMH mode/unit, thread 1, fork 1, warmup 3, measurement 5,
and finite positive score.

In `afterEvaluate`, configure the two generated tasks sequentially. `doFirst`
rejects an existing staged file and records `Instant.now()`. `doLast` finds
exactly one `benchmark.json` whose modified time is not before the recorded
start, validates it, and atomically copies it to the run directory as
`latency.json` or `throughput.json`.

- [ ] **Step 4: Add append-only finalization**

Register `finalizeBarcodeBenchmarkEvidence`. It must:

1. require the validated run id and nonblank CPU description;
2. require both staged JSON files and validate them again;
3. require the canonical fixture manifest and compute all SHA-256 values;
4. reject an existing `docs/raw/{validatedRunId}` target;
5. create a temporary sibling directory;
6. copy `latency.json`, `throughput.json`, and `fixture-manifest.json`;
7. write pretty strict `run-manifest.json` using Groovy `JsonOutput` with exact
   commands, OS/arch, Java vendor/version, processor count/CPU description,
   ZXing catalog version, fixture-manifest hash, raw hashes, modes, units, and
   timing contract;
8. atomically move the completed temporary directory to the accepted target;
9. delete the temporary directory on failure without touching accepted runs.

Mark the task `outputs.upToDateWhen { false }` because append-only collision
checking must execute on every explicit finalization attempt.

- [ ] **Step 5: Run GREEN and failure-path checks**

Run the contract test and `tasks --all`. Use a synthetic TestKit fixture to
prove invalid run id, missing raw file, duplicate target, wrong row set, and
wrong mode/unit fail. Expected: all PASS; no file under `docs/raw/` is created by
tests because TestKit uses a temporary project copy.

- [ ] **Step 6: Commit evidence lifecycle support**

```bash
git add benchmark/images-benchmark/build.gradle.kts \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/BarcodeBenchmarkContractTest.kt
git commit -m "test: guard barcode benchmark evidence"
```

### Task 4: Execute and Accept the Benchmark Evidence

**Complexity:** Medium  
**Depends on:** Task 3 and clean targeted tests  
**Pattern skills:** benchmark hazard gate, `verification-before-completion` for evidence claims  
**Files:** create accepted raw directory only through the finalizer  
**Expected DoD:** one Java 25 run produces exactly three latency and three throughput rows tied to the same immutable fixture/environment manifest.

- [ ] **Step 1: Capture the host contract**

Record:

```bash
sw_vers
uname -m
sysctl -n machdep.cpu.brand_string
JAVA25=$(/usr/libexec/java_home -v 25)
"$JAVA25/bin/java" -version
./gradlew --version
```

Use run id `issue-272-20260714-macos-arm64-01`. If accepted evidence with that
id already exists, increment only the final numeric sequence and update the
report path; never overwrite.

- [ ] **Step 2: Run latency sequentially**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeLatencyBenchmark \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  --console=plain
```

Expected: one staged `latency.json` with `avgt`, `ms/op`, and three scenarios.

- [ ] **Step 3: Run throughput only after latency exits**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeThroughputBenchmark \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  --console=plain
```

Expected: one staged `throughput.json` with `thrpt`, `ops/s`, and three
scenarios. Investigate any retry-only pass before continuing.

- [ ] **Step 4: Finalize the accepted run once**

```bash
CPU="$(sysctl -n machdep.cpu.brand_string)"
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:finalizeBarcodeBenchmarkEvidence \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  -Pbarcode.benchmark.cpu="$CPU" \
  --console=plain
```

Expected: accepted directory with exactly four files and internally matching
hashes. A second finalization with the same id must fail without changing them.

- [ ] **Step 5: Audit the six rows and commit raw evidence**

Parse both raw JSON files and print scenario, score, error, mode, and unit.
Verify `git diff --check`, fixture-manifest equality, run-manifest hashes, and no
absolute user paths or secret-like values. Commit:

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
