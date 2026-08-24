package io.bluetape4k.images.benchmark

import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.TesseractOcrEngine
import io.bluetape4k.images.ocr.extractText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max

/**
 * 동일 host envelope에서 OCR corpus v2의 cold/warm/throughput/RSS receipt를 생성합니다.
 *
 * 이 실행기는 production API를 변경하지 않고 benchmark source set에서만 동작합니다.
 * `cold`는 call별 engine wrapper 생성, `warm`과 throughput은 하나의 engine
 * wrapper를 재사용합니다. 현재 public `TesseractOcrEngine`은 각 recognize call마다
 * fresh Tess4J client를 만드는 계약이므로 native client cache를 warm 상태로
 * 가정하지 않습니다. RSS는 OS process RSS를 byte로 기록합니다.
 */
object OcrBenchmarkProtocolMain {
    private const val MANIFEST_RESOURCE = "bench/ocr-v2/manifest.json"
    private const val COLD_RUNS = 1
    private const val WARMUP_RUNS = 2
    private const val WARM_RUNS = 3
    private const val THROUGHPUT_WINDOW_MILLIS = 250L

    @JvmStatic
    fun main(args: Array<String>) {
        val arguments = parseArguments(args)
        val manifestBytes = requireNotNull(resource(MANIFEST_RESOURCE)) {
            "OCR corpus v2 manifest is missing: $MANIFEST_RESOURCE"
        }
        val manifest = OcrBenchmarkCorpusV2.decodeManifest(manifestBytes)
        val manifestSha256 = sha256Hex(manifestBytes)
        val tessdata = OcrBenchmarkEnvironment.requireTessdataPath()
        val requiredLanguages = manifest.fixtures.flatMap(OcrBenchmarkCorpusFixtureEntry::languages).distinct().sorted()
        OcrBenchmarkEnvironment.requireLanguages(requiredLanguages)
        val optionsByFixture = manifest.fixtures.associate { fixture ->
            fixture.fixtureId to OcrOptions(
                languages = fixture.languages,
                tessdataPath = tessdata,
            )
        }
        val predictions = linkedMapOf<String, String>()
        val rows = manifest.fixtures.map { fixture ->
            measureFixture(
                fixture = OcrBenchmarkCorpusV2.loadFixture(fixture.fixtureId),
                options = requireNotNull(optionsByFixture[fixture.fixtureId]),
                predictions = predictions,
            )
        }
        val metrics = OcrBenchmarkMetricReceipt.create(manifest, predictions, manifestSha256)
        val receipt = OcrBenchmarkProtocolReceipt(
            schemaVersion = 1,
            issue = 565,
            runId = arguments.runId,
            manifestSha256 = manifestSha256,
            host = OcrBenchmarkHostEnvelope(
                os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
                arch = System.getProperty("os.arch"),
                jvm = "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})",
                tesseract = commandOutput("tesseract", "--version").lineSequence().firstOrNull()
                    ?: error("Tesseract version output is empty"),
                tessdata = tessdata,
                languages = requiredLanguages,
            ),
            protocol = OcrBenchmarkProtocolEnvelope(
                coldRuns = COLD_RUNS,
                warmupRuns = WARMUP_RUNS,
                warmRuns = WARM_RUNS,
                throughputWindowMillis = THROUGHPUT_WINDOW_MILLIS,
                rssUnit = "bytes",
            ),
            rows = rows,
            metrics = metrics,
        )
        OcrBenchmarkProtocolReceiptValidator.validate(receipt, manifest)
        val output = arguments.output.toAbsolutePath().normalize()
        output.parent?.let(Files::createDirectories)
        Files.write(output, OcrBenchmarkProtocolReceipt.encode(receipt))
        println("Wrote OCR corpus v2 protocol receipt: $output")
    }

    private fun measureFixture(
        fixture: OcrBenchmarkCorpusFixture,
        options: OcrOptions,
        predictions: MutableMap<String, String>,
    ): OcrBenchmarkProtocolRow {
        val rssBefore = currentRssBytes()
        var rssPeak = rssBefore
        val coldStart = System.nanoTime()
        val coldOutput = fixture.image.extractText(options, TesseractOcrEngine())
        val coldLatency = System.nanoTime() - coldStart
        fixture.verifyOutput(coldOutput)
        rssPeak = max(rssPeak, currentRssBytes())

        val warmEngine = TesseractOcrEngine()
        repeat(WARMUP_RUNS) {
            fixture.verifyOutput(fixture.image.extractText(options, warmEngine))
            rssPeak = max(rssPeak, currentRssBytes())
        }
        val warmStart = System.nanoTime()
        var lastOutput = coldOutput
        repeat(WARM_RUNS) {
            lastOutput = fixture.image.extractText(options, warmEngine)
            fixture.verifyOutput(lastOutput)
            rssPeak = max(rssPeak, currentRssBytes())
        }
        val warmLatency = (System.nanoTime() - warmStart) / WARM_RUNS

        val throughputStart = System.nanoTime()
        var throughputRuns = 0
        val throughputDeadline = throughputStart + THROUGHPUT_WINDOW_MILLIS * 1_000_000
        while (System.nanoTime() < throughputDeadline || throughputRuns == 0) {
            lastOutput = fixture.image.extractText(options, warmEngine)
            fixture.verifyOutput(lastOutput)
            throughputRuns++
            rssPeak = max(rssPeak, currentRssBytes())
        }
        val throughputElapsed = (System.nanoTime() - throughputStart).coerceAtLeast(1L)
        val throughput = throughputRuns.toDouble() * 1_000_000_000.0 / throughputElapsed
        if (fixture.entry.expectedOutcome == OcrBenchmarkExpectedOutcome.TEXT) {
            predictions[fixture.entry.fixtureId] = lastOutput
        }
        return OcrBenchmarkProtocolRow(
            fixtureId = fixture.entry.fixtureId,
            scenario = fixture.entry.scenario,
            expectedOutcome = fixture.entry.expectedOutcome,
            coldLatencyNanos = coldLatency,
            warmLatencyNanos = warmLatency,
            throughputOpsPerSecond = throughput,
            warmIterations = WARM_RUNS,
            rssBeforeBytes = rssBefore,
            rssPeakBytes = max(rssPeak, rssBefore),
            outputSha256 = sha256Hex(lastOutput.encodeToByteArray()),
        )
    }

    private fun currentRssBytes(): Long {
        val output = commandOutput("ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString())
        val kilobytes = output.trim().toLongOrNull()
            ?: error("Unable to read current process RSS from ps: $output")
        require(kilobytes > 0) { "Current process RSS must be positive" }
        return kilobytes * 1024
    }

    private fun commandOutput(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        require(process.waitFor() == 0) {
            "Command failed (${command.joinToString(" ")}): $output"
        }
        return output
    }

    private fun parseArguments(args: Array<String>): Arguments {
        require(args.size == 4 && args[0] == "--output" && args[2] == "--run-id") {
            "Usage: --output <absolute-path> --run-id <run-id>"
        }
        val output = Path.of(args[1]).toAbsolutePath().normalize()
        require(output.isAbsolute) { "OCR protocol output path must be absolute" }
        val runId = args[3]
        require(runId.matches(Regex("[a-z0-9][a-z0-9._-]{7,79}"))) {
            "OCR protocol run ID is invalid"
        }
        return Arguments(output, runId)
    }

    private fun resource(path: String): ByteArray? =
        OcrBenchmarkProtocolMain::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }

    private data class Arguments(
        val output: Path,
        val runId: String,
    )
}
