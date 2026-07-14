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
    alias(libs.plugins.kotlin.serialization)
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
val codecMatrixRunIdPattern = Regex("[a-z0-9][a-z0-9._-]{7,79}")
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
    }

    targets {
        register("benchmark") {
            this as kotlinx.benchmark.gradle.JvmBenchmarkTarget
            jmhVersion = libs.versions.jmh.get()
            workingDir = repositoryDirectory.asFile.absolutePath
        }
    }
}

dependencies {
    // core
    implementation(libs.bluetape4k.core)
    implementation(libs.bluetape4k.logging)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.bluetape4k.junit5)
    testImplementation(gradleTestKit())
    testImplementation(project(":bluetape4k-images-barcode-zxing"))

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
