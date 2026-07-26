import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    kotlin("plugin.allopen")           // allOpen 필수
    alias(bt4k.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.benchmark)      // kotlinx-benchmark 플러그인
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
    annotation("kotlinx.benchmark.State")
}

// This harness switches its Kotlin target between Java 21 and Java 25. The
// module does not use atomicfu, and its transformer cannot load stale Java 25
// classes when a Java 21 verification follows in the same worktree.
atomicfu {
    transformJvm = false
}

sourceSets {
    create("benchmark")
}

val vipsImpl = providers.gradleProperty("vips.impl").orElse("java25").get()
require(vipsImpl == "java21" || vipsImpl == "java25") {
    "vips.impl must be java21 or java25: $vipsImpl"
}
val javaVersion = if (vipsImpl == "java21") 21 else 25
val codecMatrixRunId = providers.gradleProperty("codec.matrix.runId")
val codecMatrixSupersedes = providers.gradleProperty("codec.matrix.supersedes")
val codecMatrixReplacesFailedAttempt = providers.gradleProperty("codec.matrix.replacesFailedAttempt")
val barcodeBenchmarkRunId = providers.gradleProperty("barcode.benchmark.runId")
val barcodeBenchmarkCpu = providers.gradleProperty("barcode.benchmark.cpu")
val codecMatrixRunIdPattern = Regex("[a-z0-9][a-z0-9._-]{7,79}")
val barcodeBenchmarkRunIdPattern = Regex("issue-272-[0-9]{8}-[a-z0-9-]{3,40}")
val repositoryDirectory = rootProject.layout.projectDirectory
val codecMatrixSourceDirectory = layout.buildDirectory.dir("generated/codec-matrix-source-fixtures")
val codecMatrixRunDirectoryProvider = codecMatrixRunId.flatMap { runId ->
    require(codecMatrixRunIdPattern.matches(runId)) {
        "codec.matrix.runId must match ${codecMatrixRunIdPattern.pattern}"
    }
    layout.buildDirectory.dir("codec-matrix/$runId")
}
val codecMatrixReportDirectoryProvider = codecMatrixRunId.flatMap { runId ->
    require(codecMatrixRunIdPattern.matches(runId)) {
        "codec.matrix.runId must match ${codecMatrixRunIdPattern.pattern}"
    }
    layout.buildDirectory.dir("reports/benchmarks/codec-matrix/$runId")
}
val barcodeBenchmarkRunDirectoryProvider = barcodeBenchmarkRunId.flatMap { runId ->
    require(barcodeBenchmarkRunIdPattern.matches(runId)) {
        "invalid barcode benchmark run ID: $runId"
    }
    layout.buildDirectory.dir("barcode-benchmark/$runId")
}
val javaToolchains = extensions.getByType<JavaToolchainService>()
val selectedJavaLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(javaVersion))
}
val homebrewVipsLibraryDirectory = file("/opt/homebrew/lib")
val codecMatrixNativeTaskNames = setOf(
    "codecMatrixCapabilityReport",
    "prepareExperimentalCodecMatrixFixtures",
    "benchmarkBenchmark",
    "benchmarkCodecMatrixBenchmark",
    "benchmarkCodecMatrixAvifBenchmark",
    "benchmarkCodecMatrixHeicBenchmark",
    "benchmarkBatchPipelineBenchmark",
)

tasks.withType<JavaExec>().matching { task -> task.name in codecMatrixNativeTaskNames }.configureEach {
    if (homebrewVipsLibraryDirectory.isDirectory) {
        environment("DYLD_LIBRARY_PATH", homebrewVipsLibraryDirectory.absolutePath)
    }
}

fun validatedCodecMatrixRunId(): String {
    val runId = codecMatrixRunId.orNull
    require(runId != null && codecMatrixRunIdPattern.matches(runId)) {
        "codec.matrix.runId must match ${codecMatrixRunIdPattern.pattern}"
    }
    return runId
}

fun codecMatrixRunDirectory() = codecMatrixRunDirectoryProvider.get().asFile

fun codecMatrixReportDirectory() = codecMatrixReportDirectoryProvider.get().asFile

fun validatedBarcodeBenchmarkRunId(): String {
    val runId = barcodeBenchmarkRunId.orNull
    require(runId != null && barcodeBenchmarkRunIdPattern.matches(runId)) {
        "invalid barcode benchmark run ID: $runId"
    }
    return runId
}

fun barcodeBenchmarkRunDirectory() = barcodeBenchmarkRunDirectoryProvider.get().asFile

fun validateBarcodeBenchmarkReport(report: File, expectedMode: String, expectedUnit: String) {
    require(report.isFile) { "barcode benchmark report is missing: $report" }
    val rows = JsonSlurper().parse(report) as? List<*>
        ?: throw IllegalArgumentException("barcode benchmark report must be a JSON array")
    val actualScenarios = rows.map { value ->
        val row = value as? Map<*, *>
            ?: throw IllegalArgumentException("barcode benchmark row must be an object")
        require(row["benchmark"] == "io.bluetape4k.images.benchmark.ZxingBarcodeExtractionBenchmark.extractBarcodes") {
            "barcode benchmark name differs"
        }
        require(row["mode"] == expectedMode) {
            "barcode benchmark mode must be $expectedMode"
        }
        require((row["threads"] as? Number)?.toInt() == 1) {
            "barcode benchmark threads must be 1"
        }
        require((row["forks"] as? Number)?.toInt() == 1) {
            "barcode benchmark forks must be 1"
        }
        require((row["warmupIterations"] as? Number)?.toInt() == BARCODE_BENCHMARK_WARMUPS) {
            "barcode benchmark warmups differ"
        }
        require((row["measurementIterations"] as? Number)?.toInt() == BARCODE_BENCHMARK_ITERATIONS) {
            "barcode benchmark measurements differ"
        }
        require(row["warmupTime"] == "1 s" && row["measurementTime"] == "1 s") {
            "barcode benchmark iteration time differs"
        }
        val primaryMetric = row["primaryMetric"] as? Map<*, *>
            ?: throw IllegalArgumentException("barcode benchmark primary metric is missing")
        require(primaryMetric["scoreUnit"] == expectedUnit) {
            "barcode benchmark score unit must be $expectedUnit"
        }
        val score = (primaryMetric["score"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("barcode benchmark score is missing")
        require(score.isFinite() && score > 0.0) {
            "barcode benchmark score must be positive and finite"
        }
        val scoreError = (primaryMetric["scoreError"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("barcode benchmark score error is missing")
        require(scoreError.isFinite() && scoreError >= 0.0) {
            "barcode benchmark score error must be non-negative and finite"
        }
        val params = row["params"] as? Map<*, *>
            ?: throw IllegalArgumentException("barcode benchmark parameters are missing")
        params["scenario"] as? String
            ?: throw IllegalArgumentException("barcode benchmark scenario is missing")
    }.toSet()
    val expectedScenarios = setOf("qr", "code-128", "no-result")
    require(rows.size == expectedScenarios.size && actualScenarios == expectedScenarios) {
        "barcode benchmark row coverage differs"
    }
}

fun validateOcrBenchmarkReport(report: File, expectedMode: String, expectedUnit: String) {
    require(report.isFile) { "OCR benchmark report is missing: $report" }
    val rows = JsonSlurper().parse(report) as? List<*>
        ?: throw IllegalArgumentException("OCR benchmark report must be a JSON array")
    val expectedRows = setOf(
        "clean-text",
        "noisy-scan",
        "rotated-document",
        "multilingual-text",
    ).flatMap { scenario ->
        listOf("extractText", "preprocessAndExtract").map { method -> "$method|$scenario" }
    }.toSet()
    val actualRows = rows.map { value ->
        val row = value as? Map<*, *>
            ?: throw IllegalArgumentException("OCR benchmark row must be an object")
        require(row["mode"] == expectedMode) { "OCR benchmark mode must be $expectedMode" }
        require((row["threads"] as? Number)?.toInt() == 1) { "OCR benchmark threads must be 1" }
        require((row["forks"] as? Number)?.toInt() == 1) { "OCR benchmark forks must be 1" }
        require((row["warmupIterations"] as? Number)?.toInt() == OCR_BENCHMARK_WARMUPS) {
            "OCR benchmark warmups differ"
        }
        require((row["measurementIterations"] as? Number)?.toInt() == OCR_BENCHMARK_ITERATIONS) {
            "OCR benchmark measurements differ"
        }
        require(row["warmupTime"] == "1 s" && row["measurementTime"] == "1 s") {
            "OCR benchmark iteration time differs"
        }
        val primaryMetric = row["primaryMetric"] as? Map<*, *>
            ?: throw IllegalArgumentException("OCR benchmark primary metric is missing")
        require(primaryMetric["scoreUnit"] == expectedUnit) {
            "OCR benchmark score unit must be $expectedUnit"
        }
        val score = (primaryMetric["score"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("OCR benchmark score is missing")
        require(score.isFinite() && score > 0.0) { "OCR benchmark score must be positive and finite" }
        val benchmark = row["benchmark"] as? String
            ?: throw IllegalArgumentException("OCR benchmark name is missing")
        val method = benchmark.substringAfterLast('.')
        require(method in setOf("extractText", "preprocessAndExtract")) {
            "OCR benchmark method differs: $method"
        }
        val params = row["params"] as? Map<*, *>
            ?: throw IllegalArgumentException("OCR benchmark parameters are missing")
        val scenario = params["scenario"] as? String
            ?: throw IllegalArgumentException("OCR benchmark scenario is missing")
        "$method|$scenario"
    }.toSet()
    require(rows.size == expectedRows.size && actualRows == expectedRows) {
        "OCR benchmark row coverage differs"
    }
}

fun validateKtorRouteConcurrencyReport(report: File) {
    require(report.isFile) { "Ktor route concurrency report is missing: $report" }
    val rows = JsonSlurper().parse(report) as? List<*>
        ?: throw IllegalArgumentException("Ktor route concurrency report must be a JSON array")
    val expectedRows = buildSet {
        listOf("1", "10", "30").forEach { concurrency ->
            add("KtorThumbnailConcurrentRejectedBenchmark|$concurrency|-")
        }
        listOf("1", "5", "10", "30").forEach { concurrency ->
            listOf("medium", "photo4k").forEach { fixture ->
                add("KtorThumbnailConcurrentRouteBenchmark|$concurrency|$fixture")
            }
        }
        listOf("10", "30").forEach { concurrency ->
            listOf("medium", "photo4k").forEach { fixture ->
                add("KtorThumbnailMixedConcurrencyBenchmark|$concurrency|$fixture")
            }
        }
    }
    val actualRows = rows.map { value ->
        val row = value as? Map<*, *>
            ?: throw IllegalArgumentException("Ktor route concurrency row must be an object")
        require(row["mode"] == "sample") { "Ktor route concurrency mode must be sample" }
        require((row["threads"] as? Number)?.toInt() == 1) {
            "Ktor route concurrency threads must be 1"
        }
        require((row["forks"] as? Number)?.toInt() == 1) {
            "Ktor route concurrency forks must be 1"
        }
        require((row["warmupIterations"] as? Number)?.toInt() == 3) {
            "Ktor route concurrency warmups must be 3"
        }
        require((row["measurementIterations"] as? Number)?.toInt() == 5) {
            "Ktor route concurrency measurements must be 5"
        }
        require(row["warmupTime"] == "3 s" && row["measurementTime"] == "3 s") {
            "Ktor route concurrency iteration time must be 3 s"
        }
        val primaryMetric = row["primaryMetric"] as? Map<*, *>
            ?: throw IllegalArgumentException("Ktor route concurrency primary metric is missing")
        require(primaryMetric["scoreUnit"] == "ms/op") {
            "Ktor route concurrency score unit must be ms/op"
        }
        val score = (primaryMetric["score"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("Ktor route concurrency score is missing")
        require(score.isFinite() && score > 0.0) {
            "Ktor route concurrency score must be positive and finite"
        }
        val percentiles = primaryMetric["scorePercentiles"] as? Map<*, *>
            ?: throw IllegalArgumentException("Ktor route concurrency percentiles are missing")
        require(listOf("50.0", "95.0", "99.0").all(percentiles::containsKey)) {
            "Ktor route concurrency p50, p95, and p99 must be present"
        }
        val benchmark = row["benchmark"] as? String
            ?: throw IllegalArgumentException("Ktor route concurrency benchmark name is missing")
        val benchmarkClass = benchmark.substringBeforeLast('.').substringAfterLast('.')
        val params = row["params"] as? Map<*, *>
            ?: throw IllegalArgumentException("Ktor route concurrency parameters are missing")
        val concurrency = params["concurrency"] as? String
            ?: throw IllegalArgumentException("Ktor route concurrency parameter is missing")
        "$benchmarkClass|$concurrency|${params["fixture"] ?: "-"}"
    }.toSet()
    require(rows.size == expectedRows.size && actualRows == expectedRows) {
        "Ktor route concurrency row coverage differs"
    }
}

fun stageBarcodeBenchmarkReport(source: File, target: File) {
    require(!target.exists()) { "staged barcode benchmark report already exists: $target" }
    target.parentFile.mkdirs()
    val temporary = target.parentFile.resolve(".${target.name}.tmp-${UUID.randomUUID()}")
    try {
        Files.copy(source.toPath(), temporary.toPath())
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary.toPath())
    }
}

fun barcodeBenchmarkSha256(file: File): String {
    require(file.isFile) { "barcode benchmark input is missing: $file" }
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

fun validateCodecMatrixJmhReport(report: File, expectedMethods: Set<String>) {
    require(report.isFile) { "codec matrix JMH report is missing: $report" }
    val rows = JsonSlurper().parse(report) as? List<*>
        ?: throw IllegalArgumentException("codec matrix JMH report must be a JSON array")
    val expectedRows = expectedMethods.flatMap { method ->
        listOf("web-photo", "profile").map { scenario -> method to scenario }
    }.toSet()
    val actualRows = rows.map { value ->
        val row = value as? Map<*, *>
            ?: throw IllegalArgumentException("codec matrix JMH row must be an object")
        require(row["mode"] == "avgt") { "codec matrix JMH mode must be avgt" }
        require((row["threads"] as? Number)?.toInt() == 1) { "codec matrix JMH threads must be 1" }
        require((row["forks"] as? Number)?.toInt() == 1) { "codec matrix JMH forks must be 1" }
        require((row["warmupIterations"] as? Number)?.toInt() == CODEC_MATRIX_WARMUPS) {
            "codec matrix JMH warmups differ"
        }
        require((row["measurementIterations"] as? Number)?.toInt() == CODEC_MATRIX_ITERATIONS) {
            "codec matrix JMH iterations differ"
        }
        require(row["warmupTime"] == "1 s" && row["measurementTime"] == "1 s") {
            "codec matrix JMH iteration time differs"
        }
        val primaryMetric = row["primaryMetric"] as? Map<*, *>
            ?: throw IllegalArgumentException("codec matrix JMH primary metric is missing")
        require(primaryMetric["scoreUnit"] == "ms/op") { "codec matrix JMH score unit must be ms/op" }
        val benchmark = row["benchmark"] as? String
            ?: throw IllegalArgumentException("codec matrix JMH benchmark name is missing")
        val method = benchmark.substringAfterLast('.')
        val params = row["params"] as? Map<*, *>
            ?: throw IllegalArgumentException("codec matrix JMH parameters are missing")
        val scenario = params["scenario"] as? String
            ?: throw IllegalArgumentException("codec matrix JMH scenario is missing")
        method to scenario
    }.toSet()
    require(actualRows == expectedRows && rows.size == expectedRows.size) {
        "codec matrix JMH row coverage differs"
    }
}

fun requireNoBlockingCodecMatrixEligibility(report: File) {
    require(report.isFile) { "codec matrix eligibility report is missing: $report" }
    val root = JsonSlurper().parse(report) as? Map<*, *>
        ?: throw IllegalArgumentException("codec matrix eligibility report must be an object")
    val cells = root["cells"] as? List<*>
        ?: throw IllegalArgumentException("codec matrix eligibility cells are missing")
    val blocking = cells.map { value ->
        val cell = value as? Map<*, *>
            ?: throw IllegalArgumentException("codec matrix eligibility cell must be an object")
        cell["status"] as? String
            ?: throw IllegalArgumentException("codec matrix eligibility status is missing")
    }.filter { status -> status == "FAILED_SMOKE" || status == "ERROR" }
    require(blocking.isEmpty()) { "codec matrix eligibility contains blocking status: ${blocking.toSet()}" }
}

fun stageCodecMatrixLatency(source: File, target: File) {
    require(!target.exists()) { "staged codec matrix latency already exists: $target" }
    target.parentFile.mkdirs()
    val temporary = target.parentFile.resolve(".${target.name}.tmp-${UUID.randomUUID()}")
    try {
        Files.copy(source.toPath(), temporary.toPath())
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary.toPath())
    }
}

fun copyCodecMatrixInputImmutable(source: File, target: File) {
    require(source.isFile) { "codec matrix input is missing: $source" }
    if (target.exists()) {
        require(source.readBytes().contentEquals(target.readBytes())) {
            "existing staged codec matrix input differs: $target"
        }
        return
    }
    target.parentFile.mkdirs()
    val temporary = target.parentFile.resolve(".${target.name}.tmp-${UUID.randomUUID()}")
    try {
        Files.copy(source.toPath(), temporary.toPath())
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary.toPath())
    }
}

fun writeCodecMatrixTextImmutable(target: File, content: String) {
    if (target.exists()) {
        require(target.readText() == content) { "existing staged codec matrix text differs: $target" }
        return
    }
    target.parentFile.mkdirs()
    val temporary = target.parentFile.resolve(".${target.name}.tmp-${UUID.randomUUID()}")
    try {
        temporary.writeText(content)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary.toPath())
    }
}

fun codecMatrixSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

val CODEC_MATRIX_WARMUPS = 1
val CODEC_MATRIX_ITERATIONS = 3
val CODEC_MATRIX_ITERATION_SECONDS = 1L
val BARCODE_BENCHMARK_WARMUPS = 3
val BARCODE_BENCHMARK_ITERATIONS = 5
val BARCODE_BENCHMARK_ITERATION_SECONDS = 1L
val OCR_BENCHMARK_WARMUPS = 3
val OCR_BENCHMARK_ITERATIONS = 5
val OCR_BENCHMARK_ITERATION_SECONDS = 1L

kotlin {
    jvmToolchain(javaVersion)
    target {
        compilations.getByName("benchmark").associateWith(compilations.getByName("main"))
    }
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
    named("benchmarkImplementation") {
        extendsFrom(
            configurations.getByName("implementation"),
            configurations.getByName("compileOnly"),
            configurations.getByName("testImplementation"),
        )
    }
    named("benchmarkRuntimeOnly") {
        extendsFrom(
            configurations.getByName("runtimeOnly"),
            configurations.getByName("testRuntimeOnly"),
        )
    }
}

benchmark {
    configurations {
        named("main") {
            exclude(".*VipsExperimentalCodecMatrixBenchmark.*")
        }

        register("codecMatrix") {
            include(".*VipsCodecMatrixBenchmark.*")
            warmups = CODEC_MATRIX_WARMUPS
            iterations = CODEC_MATRIX_ITERATIONS
            iterationTime = CODEC_MATRIX_ITERATION_SECONDS
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

        register("codecMatrixAvif") {
            include(".*VipsExperimentalCodecMatrixBenchmark.encodeAvifFromJpeg.*")
            include(".*VipsExperimentalCodecMatrixBenchmark.decodeAvifToJpeg.*")
            warmups = CODEC_MATRIX_WARMUPS
            iterations = CODEC_MATRIX_ITERATIONS
            iterationTime = CODEC_MATRIX_ITERATION_SECONDS
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

        register("codecMatrixHeic") {
            include(".*VipsExperimentalCodecMatrixBenchmark.encodeHeicFromJpeg.*")
            include(".*VipsExperimentalCodecMatrixBenchmark.decodeHeicToJpeg.*")
            warmups = CODEC_MATRIX_WARMUPS
            iterations = CODEC_MATRIX_ITERATIONS
            iterationTime = CODEC_MATRIX_ITERATION_SECONDS
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

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

        register("ocrLatency") {
            include(".*TesseractOcrExtractionBenchmark.*")
            warmups = OCR_BENCHMARK_WARMUPS
            iterations = OCR_BENCHMARK_ITERATIONS
            iterationTime = OCR_BENCHMARK_ITERATION_SECONDS
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

        register("ocrThroughput") {
            include(".*TesseractOcrExtractionBenchmark.*")
            warmups = OCR_BENCHMARK_WARMUPS
            iterations = OCR_BENCHMARK_ITERATIONS
            iterationTime = OCR_BENCHMARK_ITERATION_SECONDS
            iterationTimeUnit = "s"
            mode = "thrpt"
            outputTimeUnit = "s"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

        register("pipelineAllocation") {
            include(".*ImagePipelineBenchmark.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", "definedByJmh")
        }

        register("memoryProfile") {
            include(".*ImageResizeBenchmark.scrimage_scaleTo.*")
            include(".*ImageEncodeBenchmark.scrimage_encodeJpeg.*")
            include(".*ImageEncodeBenchmark.scrimage_encodePng.*")
            include(".*VipsBackendBenchmark.vips_resize.*")
            include(".*VipsBackendBenchmark.vips_thumbnail.*")
            include(".*VipsBackendBenchmark.vips_crop.*")
            include(".*VipsBackendEncodeBenchmark.vips_encodeJpeg.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", "definedByJmh")
        }

        register("ioBoundary") {
            include(".*ImageIoBoundaryBenchmark.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", "definedByJmh")
        }

        register("ioThroughput") {
            include(".*ImageFileIoThroughputBenchmark.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "thrpt"
            outputTimeUnit = "s"
            reportFormat = "json"
            advanced("jvmForks", "definedByJmh")
        }

        register("largeStreaming") {
            include(".*ImageLargeStreamingBenchmark.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", "definedByJmh")
        }

        register("algorithmicHotPaths") {
            include(".*ImageAlgorithmBenchmark.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

        register("batchPipeline") {
            include(".*ImageBatchBenchmark.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

        register("ktorRoute") {
            include(".*KtorThumbnailRouteBenchmark.*")
            include(".*KtorThumbnailRejectedRouteBenchmark.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

        register("ktorRouteConcurrency") {
            include(".*KtorThumbnailConcurrentRouteBenchmark.*")
            include(".*KtorThumbnailConcurrentRejectedBenchmark.*")
            include(".*KtorThumbnailMixedConcurrencyBenchmark.*")
            warmups = 3
            iterations = 5
            iterationTime = 3
            iterationTimeUnit = "s"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

        register("storageLocal") {
            include(".*ImageStorageBenchmark.local_.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }

        register("storageS3") {
            include(".*ImageStorageBenchmark.s3_.*")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            advanced("jvmForks", 1)
        }
    }

    targets {
        register("benchmark") {
            this as kotlinx.benchmark.gradle.JvmBenchmarkTarget
            jmhVersion = libs.versions.jmh.get()
            workingDir = repositoryDirectory.asFile.absolutePath
        }
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${bt4k.versions.spring.boot.get()}")
        mavenBom(bt4k.aws2.bom.get().toString())
        mavenBom("org.jetbrains.kotlin:kotlin-bom:${bt4k.versions.kotlin.get()}")
        mavenBom("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${bt4k.versions.kotlinx.coroutines.get()}")
    }
}

dependencies {
    // core
    implementation(bt4k.bluetape4k.core)
    implementation(bt4k.bluetape4k.logging)
    implementation(bt4k.kotlinx.serialization.json)
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(gradleTestKit())
    testImplementation(project(":bluetape4k-images-barcode-zxing"))
    testImplementation(project(":bluetape4k-images-ktor"))

    // scrimage (images)
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-barcode-api"))
    implementation(libs.scrimage.webp)

    // vips — API 인터페이스는 컴파일 타임에 필요, 구현체는 런타임에만 필요
    add("benchmarkImplementation", project(":bluetape4k-images-vips-api"))
    if (vipsImpl == "java21") {
        add("benchmarkRuntimeOnly", project(":bluetape4k-images-vips-java21"))
    } else {
        add("benchmarkRuntimeOnly", project(":bluetape4k-images-vips-java25"))
    }

    // Benchmark
    add("benchmarkImplementation", project(":bluetape4k-images-barcode-zxing"))
    add("benchmarkImplementation", project(":bluetape4k-images-ktor"))
    add("benchmarkImplementation", project(":bluetape4k-images-ocr"))
    add("benchmarkImplementation", project(":bluetape4k-images-spring-boot"))
    add("benchmarkImplementation", bt4k.bluetape4k.aws.spring.boot)
    add("benchmarkImplementation", libs.aws2.s3)
    add("benchmarkImplementation", libs.ktor.server.test.host)
    add("benchmarkImplementation", "org.springframework:spring-core")
    add("benchmarkImplementation", libs.batik.transcoder)
    add("benchmarkImplementation", libs.batik.codec)
    add("benchmarkImplementation", libs.kotlinx.benchmark.runtime)
    add("benchmarkImplementation", libs.kotlinx.benchmark.runtime.jvm)
    add("benchmarkImplementation", libs.jmh.core)
    add("benchmarkImplementation", libs.jmh.generator.annprocess)
}

tasks.withType<Test>().configureEach {
    systemProperty("codec.matrix.testBackend", vipsImpl)
    providers.gradleProperty("bluetape4kDependenciesCatalogPath").orNull?.let { catalogPath ->
        systemProperty("codec.matrix.testCatalogPath", catalogPath)
    }
}

val syncCodecMatrixSourceFixtures = tasks.register<Sync>("syncCodecMatrixSourceFixtures") {
    description = "Sync the two hash-pinned codec matrix source images"
    from("src/main/resources/bench/cafe.jpg")
    from(repositoryDirectory.file("images/src/test/resources/images/homer.jpg"))
    into(codecMatrixSourceDirectory)
}

val codecMatrixPreflight = tasks.register<JavaExec>("codecMatrixPreflight") {
    description = "Record non-native host and backend eligibility for the codec matrix"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.bluetape4k.images.benchmark.CodecMatrixPreflightMain")
    javaLauncher.set(selectedJavaLauncher)
    workingDir(repositoryDirectory)
    if (vipsImpl == "java25") {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
    inputs.property("backend", vipsImpl)
    inputs.property("runId", codecMatrixRunId)
    outputs.file(codecMatrixRunDirectoryProvider.map { it.file("preflight-$vipsImpl.json") })
    doFirst {
        setArgs(listOf("--backend", vipsImpl, "--run-id", validatedCodecMatrixRunId()))
    }
}

val prepareCodecMatrixFixtures = tasks.register<JavaExec>("prepareCodecMatrixFixtures") {
    description = "Prepare immutable JPEG, PNG, and WebP codec matrix fixtures"
    dependsOn(codecMatrixPreflight, syncCodecMatrixSourceFixtures)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.bluetape4k.images.benchmark.CodecMatrixFixtureMain")
    javaLauncher.set(selectedJavaLauncher)
    workingDir(repositoryDirectory)
    inputs.dir(codecMatrixSourceDirectory)
    inputs.file(codecMatrixRunDirectoryProvider.map { it.file("preflight-$vipsImpl.json") })
    outputs.dir(codecMatrixRunDirectoryProvider.map { it.dir("fixtures") })
    doFirst {
        systemProperty("vips.impl", vipsImpl)
        setArgs(listOf("--run-id", validatedCodecMatrixRunId()))
    }
}

val codecMatrixCapabilityReport = tasks.register<JavaExec>("codecMatrixCapabilityReport") {
    description = "Probe direction-specific codec support and smoke transcodes"
    dependsOn(prepareCodecMatrixFixtures, tasks.named("benchmarkClasses"))
    classpath = sourceSets.named("benchmark").get().runtimeClasspath
    mainClass.set("io.bluetape4k.images.benchmark.CodecMatrixCapabilityMain")
    javaLauncher.set(selectedJavaLauncher)
    workingDir(repositoryDirectory)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    inputs.property("backend", vipsImpl)
    inputs.file(codecMatrixRunDirectoryProvider.map { it.file("preflight-$vipsImpl.json") })
    inputs.file(codecMatrixRunDirectoryProvider.map { it.file("fixtures/manifest.json") })
    outputs.file(codecMatrixReportDirectoryProvider.map { it.file("eligibility-$vipsImpl.json") })
    outputs.file(codecMatrixReportDirectoryProvider.map { it.file("sizes-$vipsImpl.json") })
    doFirst {
        setArgs(listOf("--backend", vipsImpl, "--run-id", validatedCodecMatrixRunId()))
    }
}

val prepareExperimentalCodecMatrixFixtures = tasks.register<JavaExec>("prepareExperimentalCodecMatrixFixtures") {
    description = "Prepare manifest-pinned AVIF and HEIC inputs for eligible directions"
    dependsOn(codecMatrixCapabilityReport)
    classpath = sourceSets.named("benchmark").get().runtimeClasspath
    mainClass.set("io.bluetape4k.images.benchmark.CodecMatrixExperimentalFixtureMain")
    javaLauncher.set(selectedJavaLauncher)
    workingDir(repositoryDirectory)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    inputs.file(codecMatrixReportDirectoryProvider.map { it.file("eligibility-$vipsImpl.json") })
    inputs.file(codecMatrixRunDirectoryProvider.map { it.file("fixtures/manifest.json") })
    outputs.file(codecMatrixRunDirectoryProvider.map { it.file("parameters/parameters-codecMatrixAvif.txt") })
    outputs.file(codecMatrixRunDirectoryProvider.map { it.file("parameters/parameters-codecMatrixHeic.txt") })
    doFirst {
        setArgs(listOf("--backend", vipsImpl, "--run-id", validatedCodecMatrixRunId()))
    }
}

tasks.register<JavaExec>("finalizeCodecMatrixEvidence") {
    description = "Validate and atomically promote append-only codec matrix evidence"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.bluetape4k.images.benchmark.CodecMatrixFinalizeMain")
    javaLauncher.set(selectedJavaLauncher)
    workingDir(repositoryDirectory)
    inputs.property("runId", codecMatrixRunId)
    inputs.property("supersedes", codecMatrixSupersedes.orElse(""))
    inputs.property("replacesFailedAttempt", codecMatrixReplacesFailedAttempt.orElse(""))
    inputs.dir(codecMatrixRunDirectoryProvider.map { it.dir("staging") })
    inputs.dir(codecMatrixReportDirectoryProvider)
    outputs.upToDateWhen { false }
    doFirst {
        val runId = validatedCodecMatrixRunId()
        val stagingDirectory = codecMatrixRunDirectory().resolve("staging")
        val reportDirectory = codecMatrixReportDirectory()
        stagingDirectory.mkdirs()
        val preflightFiles = requireNotNull(codecMatrixRunDirectory().listFiles())
            .filter { file -> file.isFile && file.name.matches(Regex("preflight-java(?:21|25)\\.json")) }
        require(preflightFiles.map { file -> file.name }.toSet() == setOf("preflight-java21.json", "preflight-java25.json")) {
            "finalization requires Java 21 and Java 25 preflight evidence"
        }
        preflightFiles.forEach { preflight ->
            copyCodecMatrixInputImmutable(preflight, stagingDirectory.resolve(preflight.name))
        }

        val eligibilityFiles = requireNotNull(reportDirectory.listFiles())
            .filter { file -> file.isFile && file.name.matches(Regex("eligibility-java(?:21|25)\\.json")) }
        require(eligibilityFiles.isNotEmpty()) { "finalization requires backend-keyed eligibility evidence" }
        eligibilityFiles.forEach { eligibility ->
            copyCodecMatrixInputImmutable(eligibility, stagingDirectory.resolve(eligibility.name))
        }

        eligibilityFiles.map { eligibility -> eligibility.name.substringAfter("eligibility-").substringBefore(".json") }
            .forEach { backend ->
                val stagedSizes = reportDirectory.resolve("sizes-$backend-staged.json")
                val sizes = stagedSizes.takeIf(File::isFile) ?: reportDirectory.resolve("sizes-$backend.json")
                copyCodecMatrixInputImmutable(sizes, stagingDirectory.resolve(sizes.name))
            }

        listOf(
            codecMatrixRunDirectory().resolve("fixtures/manifest.json") to
                    stagingDirectory.resolve("fixtures/manifest.json"),
            codecMatrixRunDirectory().resolve("fixtures/experimental-java25/manifest.json") to
                    stagingDirectory.resolve("fixtures/experimental-java25/manifest.json"),
        ).filter { (source, _) -> source.isFile }.forEach { (source, target) ->
            target.parentFile.mkdirs()
            copyCodecMatrixInputImmutable(source, target)
        }

        codecMatrixSupersedes.orNull?.let { supersedes ->
            require(codecMatrixRunIdPattern.matches(supersedes) && supersedes != runId) {
                "codec.matrix.supersedes must name a different valid run ID"
            }
            require(layout.projectDirectory.file("docs/raw/$supersedes/run-manifest.json").asFile.isFile) {
                "codec.matrix.supersedes does not identify accepted evidence: $supersedes"
            }
        }
        codecMatrixReplacesFailedAttempt.orNull?.let { failedRunId ->
            require(codecMatrixRunIdPattern.matches(failedRunId) && failedRunId != runId) {
                "codec.matrix.replacesFailedAttempt must name a different valid run ID"
            }
            require(layout.projectDirectory.file("docs/raw/failed/$failedRunId/attempt-manifest.json").asFile.isFile) {
                "codec.matrix.replacesFailedAttempt does not identify failed evidence: $failedRunId"
            }
        }
        val arguments = mutableListOf("--run-id", runId)
        codecMatrixSupersedes.orNull?.let { arguments += listOf("--supersedes", it) }
        codecMatrixReplacesFailedAttempt.orNull?.let {
            arguments += listOf("--replaces-failed-attempt", it)
        }
        setArgs(arguments)
    }
}

tasks.withType<Jar>().configureEach {
    if (name == "benchmarkBenchmarkJar") {
        inputs.file(layout.projectDirectory.file("build.gradle.kts"))
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.EC")
    }
}

tasks.register("stageCodecMatrixProfilerJar") {
    description = "Stage the exact generated JMH jar for focused GC profiling"
    val benchmarkJar = tasks.named<Jar>("benchmarkBenchmarkJar")
    val archiveFile = benchmarkJar.flatMap(Jar::getArchiveFile)
    val stagedJar = codecMatrixRunDirectoryProvider.map { directory ->
        directory.file("staging/codec-matrix-profiler-$vipsImpl.jar")
    }
    val stagedHash = codecMatrixRunDirectoryProvider.map { directory ->
        directory.file("staging/codec-matrix-profiler-$vipsImpl.jar.sha256")
    }
    dependsOn(benchmarkJar)
    inputs.file(archiveFile)
    inputs.files(fileTree("src/benchmark/kotlin") { include("**/*.kt") })
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
    outputs.files(stagedJar, stagedHash)
    doLast {
        validatedCodecMatrixRunId()
        val archive = archiveFile.get().asFile
        require(archive.isFile) { "generated codec matrix profiler jar is missing: $archive" }
        val newestSourceTimestamp = (
                sequenceOf(layout.projectDirectory.file("build.gradle.kts").asFile) +
                        fileTree("src/benchmark/kotlin") { include("**/*.kt") }.files.asSequence()
                )
            .map(File::lastModified)
            .maxOrNull() ?: error("codec matrix benchmark sources are missing")
        require(archive.lastModified() >= newestSourceTimestamp) {
            "generated codec matrix profiler jar is stale"
        }
        JarFile(archive).use { jar ->
            val entries = jar.entries().asSequence().map { entry -> entry.name }.toSet()
            listOf("VipsCodecMatrixBenchmark", "VipsExperimentalCodecMatrixBenchmark").forEach { className ->
                require(entries.contains("io/bluetape4k/images/benchmark/$className.class")) {
                    "generated codec matrix profiler jar lacks $className"
                }
                require(entries.any { entry ->
                    entry.startsWith("io/bluetape4k/images/benchmark/jmh_generated/${className}_") &&
                            entry.endsWith("_jmhTest.class")
                }) { "generated codec matrix profiler jar lacks generated JMH classes for $className" }
            }
        }
        val targetJar = stagedJar.get().asFile
        copyCodecMatrixInputImmutable(archive, targetJar)
        val hash = codecMatrixSha256(targetJar)
        writeCodecMatrixTextImmutable(
            stagedHash.get().asFile,
            "$hash  ${targetJar.name}\n",
        )
    }
}

tasks.register("finalizeBarcodeBenchmarkEvidence") {
    description = "Promote one validated append-only ZXing barcode benchmark run"
    group = "verification"
    inputs.property("runId", barcodeBenchmarkRunId)
    inputs.property("cpu", barcodeBenchmarkCpu)
    outputs.upToDateWhen { false }
    doLast {
        val runId = validatedBarcodeBenchmarkRunId()
        val cpu = barcodeBenchmarkCpu.orNull?.trim()
        require(!cpu.isNullOrEmpty()) { "barcode.benchmark.cpu must not be blank" }

        val runDirectory = barcodeBenchmarkRunDirectory()
        val latency = runDirectory.resolve("latency.json")
        val throughput = runDirectory.resolve("throughput.json")
        require(latency.isFile) { "staged barcode latency report is missing: $latency" }
        require(throughput.isFile) { "staged barcode throughput report is missing: $throughput" }
        validateBarcodeBenchmarkReport(latency, "avgt", "ms/op")
        validateBarcodeBenchmarkReport(throughput, "thrpt", "ops/s")

        val fixtureManifest = layout.projectDirectory.file(
            "src/main/resources/bench/barcode/manifest.json",
        ).asFile
        require(fixtureManifest.isFile) { "barcode fixture manifest is missing: $fixtureManifest" }
        val fixtureManifestSha256 = barcodeBenchmarkSha256(fixtureManifest)
        val latencySha256 = barcodeBenchmarkSha256(latency)
        val throughputSha256 = barcodeBenchmarkSha256(throughput)

        val acceptedRoot = layout.projectDirectory.dir("docs/raw").asFile
        acceptedRoot.mkdirs()
        val target = acceptedRoot.resolve(runId)
        require(!target.exists()) { "accepted barcode benchmark run already exists: $target" }
        val temporary = acceptedRoot.resolve(".$runId.tmp-${UUID.randomUUID()}")
        try {
            Files.createDirectory(temporary.toPath())
            Files.copy(latency.toPath(), temporary.resolve("latency.json").toPath())
            Files.copy(throughput.toPath(), temporary.resolve("throughput.json").toPath())
            Files.copy(fixtureManifest.toPath(), temporary.resolve("fixture-manifest.json").toPath())

            val manifest = linkedMapOf<String, Any>(
                "schemaVersion" to 1,
                "runId" to runId,
                "commands" to listOf(
                    "./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeLatencyBenchmark " +
                        "-Pbarcode.benchmark.runId=$runId --console=plain",
                    "./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeThroughputBenchmark " +
                        "-Pbarcode.benchmark.runId=$runId --console=plain",
                    "./gradlew :bluetape4k-images-benchmark:finalizeBarcodeBenchmarkEvidence " +
                        "-Pbarcode.benchmark.runId=$runId -Pbarcode.benchmark.cpu=<host-cpu> --console=plain",
                ),
                "environment" to linkedMapOf(
                    "osName" to System.getProperty("os.name"),
                    "osVersion" to System.getProperty("os.version"),
                    "osArch" to System.getProperty("os.arch"),
                    "processorCount" to Runtime.getRuntime().availableProcessors(),
                    "cpu" to cpu,
                ),
                "jvm" to linkedMapOf(
                    "vendor" to System.getProperty("java.vendor"),
                    "version" to System.getProperty("java.version"),
                ),
                "provider" to linkedMapOf(
                    "name" to "ZXing",
                    "version" to libs.versions.zxing.get(),
                ),
                "fixtures" to linkedMapOf(
                    "path" to "fixture-manifest.json",
                    "fixtureManifestSha256" to fixtureManifestSha256,
                ),
                "artifacts" to listOf(
                    linkedMapOf(
                        "path" to "latency.json",
                        "latencySha256" to latencySha256,
                        "mode" to "avgt",
                        "unit" to "ms/op",
                    ),
                    linkedMapOf(
                        "path" to "throughput.json",
                        "throughputSha256" to throughputSha256,
                        "mode" to "thrpt",
                        "unit" to "ops/s",
                    ),
                ),
                "protocol" to linkedMapOf(
                    "threads" to 1,
                    "forks" to 1,
                    "warmups" to BARCODE_BENCHMARK_WARMUPS,
                    "measurements" to BARCODE_BENCHMARK_ITERATIONS,
                    "iterationSeconds" to BARCODE_BENCHMARK_ITERATION_SECONDS,
                ),
            )
            temporary.resolve("run-manifest.json").writeText(
                JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n",
            )
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            if (temporary.exists()) {
                temporary.deleteRecursively()
            }
        }
    }
}

afterEvaluate {
    val codecMatrixExecutionTaskNames = setOf(
        "benchmarkBenchmark",
        "benchmarkCodecMatrixBenchmark",
        "benchmarkCodecMatrixAvifBenchmark",
        "benchmarkCodecMatrixHeicBenchmark",
    )
    tasks.matching { task ->
        task.name == "benchmarkBenchmark" || task.name == "benchmarkCodecMatrixBenchmark"
    }.configureEach {
        dependsOn(prepareCodecMatrixFixtures)
    }

    val codecMatrixBenchmarkStarts = ConcurrentHashMap<String, Instant>()
    val barcodeBenchmarkStarts = ConcurrentHashMap<String, Instant>()
    val ocrBenchmarkStarts = ConcurrentHashMap<String, Instant>()
    val ktorRouteConcurrencyStarts = ConcurrentHashMap<String, Instant>()

    tasks.matching { task -> task.name == "benchmarkKtorRouteConcurrencyBenchmark" }.configureEach {
        doFirst {
            ktorRouteConcurrencyStarts[name] = Instant.now()
        }
        doLast {
            val startedAt = requireNotNull(ktorRouteConcurrencyStarts.remove(name))
            val reportRoot = layout.buildDirectory.dir("reports/benchmarks/ktorRouteConcurrency").get().asFile
            val reports = if (reportRoot.isDirectory) {
                Files.walk(reportRoot.toPath()).use { paths ->
                    paths.filter { path ->
                        Files.isRegularFile(path) &&
                                path.fileName.toString() == "benchmark.json" &&
                                !Files.getLastModifiedTime(path).toInstant().isBefore(startedAt)
                    }.map { it.toFile() }.toList()
                }
            } else {
                emptyList()
            }
            require(reports.size == 1) {
                "expected one fresh Ktor route concurrency report but found ${reports.size}"
            }
            validateKtorRouteConcurrencyReport(reports.single())
        }
    }

    tasks.matching { task ->
        task.name == "benchmarkBarcodeLatencyBenchmark" ||
                task.name == "benchmarkBarcodeThroughputBenchmark"
    }.configureEach {
        val latencyMode = name == "benchmarkBarcodeLatencyBenchmark"
        val configurationName = if (latencyMode) "barcodeLatency" else "barcodeThroughput"
        val stagedName = if (latencyMode) "latency.json" else "throughput.json"
        val expectedMode = if (latencyMode) "avgt" else "thrpt"
        val expectedUnit = if (latencyMode) "ms/op" else "ops/s"
        if (!latencyMode) {
            mustRunAfter("benchmarkBarcodeLatencyBenchmark")
        }
        doFirst {
            val staged = barcodeBenchmarkRunDirectory().resolve(stagedName)
            require(!staged.exists()) { "staged barcode benchmark report already exists: $staged" }
            barcodeBenchmarkStarts[name] = Instant.now()
        }
        doLast {
            val startedAt = requireNotNull(barcodeBenchmarkStarts.remove(name))
            val reportRoot = layout.buildDirectory.dir("reports/benchmarks/$configurationName").get().asFile
            val reports = if (reportRoot.isDirectory) {
                Files.walk(reportRoot.toPath()).use { paths ->
                    paths.filter { path ->
                        Files.isRegularFile(path) &&
                                path.fileName.toString() == "benchmark.json" &&
                                !Files.getLastModifiedTime(path).toInstant().isBefore(startedAt)
                    }.map { it.toFile() }.toList()
                }
            } else {
                emptyList()
            }
            require(reports.size == 1) {
                "expected one fresh barcode benchmark report but found ${reports.size}"
            }
            val report = reports.single()
            validateBarcodeBenchmarkReport(report, expectedMode, expectedUnit)
            stageBarcodeBenchmarkReport(report, barcodeBenchmarkRunDirectory().resolve(stagedName))
        }
    }

    tasks.matching { task ->
        task.name == "benchmarkOcrLatencyBenchmark" ||
                task.name == "benchmarkOcrThroughputBenchmark"
    }.configureEach {
        val latencyMode = name == "benchmarkOcrLatencyBenchmark"
        val configurationName = if (latencyMode) "ocrLatency" else "ocrThroughput"
        val expectedMode = if (latencyMode) "avgt" else "thrpt"
        val expectedUnit = if (latencyMode) "ms/op" else "ops/s"
        if (!latencyMode) {
            mustRunAfter("benchmarkOcrLatencyBenchmark")
        }
        doFirst {
            ocrBenchmarkStarts[name] = Instant.now()
        }
        doLast {
            val startedAt = requireNotNull(ocrBenchmarkStarts.remove(name))
            val reportRoot = layout.buildDirectory.dir("reports/benchmarks/$configurationName").get().asFile
            val reports = if (reportRoot.isDirectory) {
                Files.walk(reportRoot.toPath()).use { paths ->
                    paths.filter { path ->
                        Files.isRegularFile(path) &&
                                path.fileName.toString() == "benchmark.json" &&
                                !Files.getLastModifiedTime(path).toInstant().isBefore(startedAt)
                    }.map { it.toFile() }.toList()
                }
            } else {
                emptyList()
            }
            require(reports.size == 1) {
                "expected one fresh OCR benchmark report but found ${reports.size}"
            }
            validateOcrBenchmarkReport(reports.single(), expectedMode, expectedUnit)
        }
    }

    tasks.matching { task -> task.name == "benchmarkCodecMatrixBenchmark" }.configureEach {
        doFirst {
            require(!codecMatrixRunDirectory().resolve("staging/latency-$vipsImpl-codecMatrix.json").exists()) {
                "staged codec matrix latency already exists for this run ID"
            }
            codecMatrixBenchmarkStarts[name] = Instant.now()
        }
        doLast {
            val startedAt = requireNotNull(codecMatrixBenchmarkStarts.remove(name))
            val reportRoot = layout.buildDirectory.dir("reports/benchmarks/codecMatrix").get().asFile
            val reports = if (reportRoot.isDirectory) {
                Files.walk(reportRoot.toPath()).use { paths ->
                    paths.filter { path ->
                        Files.isRegularFile(path) &&
                                path.fileName.toString() == "benchmark.json" &&
                                !Files.getLastModifiedTime(path).toInstant().isBefore(startedAt)
                    }.map { it.toFile() }.toList()
                }
            } else {
                emptyList()
            }
            require(reports.size == 1) { "expected one fresh codec matrix JMH report but found ${reports.size}" }
            validateCodecMatrixJmhReport(
                reports.single(),
                setOf("encodePngFromJpeg", "decodePngToJpeg", "encodeWebpFromJpeg", "decodeWebpToJpeg"),
            )
            stageCodecMatrixLatency(
                reports.single(),
                codecMatrixRunDirectory().resolve("staging/latency-$vipsImpl-codecMatrix.json"),
            )
        }
    }

    tasks.matching { task ->
        task.name == "benchmarkCodecMatrixAvifBenchmark" || task.name == "benchmarkCodecMatrixHeicBenchmark"
    }.configureEach {
        dependsOn(prepareExperimentalCodecMatrixFixtures)
        val format = if (name.contains("Avif")) "codecMatrixAvif" else "codecMatrixHeic"
        val parameterFile = provider {
            codecMatrixRunDirectory().resolve("parameters/parameters-$format.txt")
        }
        onlyIf("at least one experimental codec direction is eligible") {
            val file = parameterFile.get()
            file.isFile && file.readLines().any { line -> line.startsWith("include:") }
        }
        doFirst {
            codecMatrixBenchmarkStarts[name] = Instant.now()
            val parameterFileValue = parameterFile.get()
            require(parameterFileValue.isFile) { "codec matrix parameter file is missing: $parameterFileValue" }
            val eligibilityFile = codecMatrixReportDirectory().resolve("eligibility-$vipsImpl.json")
            requireNoBlockingCodecMatrixEligibility(eligibilityFile)
            val reportLine = parameterFileValue.readLines().single { line -> line.startsWith("reportFile:") }
            require(!file(reportLine.substringAfter("reportFile:")).exists()) {
                "experimental codec matrix latency already exists for this run ID"
            }
            (this as JavaExec).setArgs(listOf(parameterFileValue.absolutePath))
            systemProperty(
                "codec.matrix.eligibility",
                eligibilityFile.absolutePath,
            )
        }
        doLast {
            val startedAt = requireNotNull(codecMatrixBenchmarkStarts.remove(name))
            val parameterLines = parameterFile.get().readLines()
            val methods = parameterLines.filter { line -> line.startsWith("include:") }.map { line ->
                line.substringAfter("VipsExperimentalCodecMatrixBenchmark.").substringBefore(".*")
            }.toSet()
            val reportLine = parameterLines.single { line -> line.startsWith("reportFile:") }
            val report = file(reportLine.substringAfter("reportFile:"))
            require(!Files.getLastModifiedTime(report.toPath()).toInstant().isBefore(startedAt)) {
                "experimental codec matrix JMH report is stale"
            }
            validateCodecMatrixJmhReport(report, methods)
        }
    }

    tasks.matching { task -> task.name == "benchmarkStorageS3Benchmark" }.configureEach {
        doFirst {
            require(providers.gradleProperty("storage.s3.enabled").orNull == "true") {
                "S3 storage benchmark is opt-in: pass -Pstorage.s3.enabled=true"
            }
        }
    }

    tasks.withType<JavaExec>().matching { task -> task.name in codecMatrixExecutionTaskNames }.configureEach {
        javaLauncher.set(selectedJavaLauncher)
        workingDir(repositoryDirectory)
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        doFirst {
            systemProperty("codec.matrix.backend", vipsImpl)
            systemProperty("codec.matrix.runId", validatedCodecMatrixRunId())
            systemProperty(
                "codec.matrix.preflight",
                codecMatrixRunDirectory().resolve("preflight-$vipsImpl.json").absolutePath,
            )
            systemProperty(
                "codec.matrix.fixtureManifest",
                codecMatrixRunDirectory().resolve("fixtures/manifest.json").absolutePath,
            )
        }
    }
}
