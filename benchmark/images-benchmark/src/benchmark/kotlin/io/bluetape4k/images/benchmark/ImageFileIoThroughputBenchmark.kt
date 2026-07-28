package io.bluetape4k.images.benchmark

import io.bluetape4k.okio.asSink
import io.bluetape4k.okio.asSource
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.asSuspendedSink
import io.bluetape4k.okio.coroutines.asSuspendedSource
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.TimeUnit

private const val STREAM_BUFFER_SIZE = 128 * 1024L

/**
 * concurrent many-file load에서 compressed image file IO boundary를 비교합니다.
 *
 * 이 benchmark는 의도적으로 Scrimage decode/encode를 제외합니다. 이미 compressed된 image file 여러 개를 동시에
 * 읽거나 쓸 때 file boundary 선택이 어떻게 동작하는지 측정합니다. score는 초당 batch operation 수이며,
 * 초당 file operation 수는 scenario file count를 곱해 계산합니다.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = ["--enable-native-access=ALL-UNNAMED"])
@State(Scope.Benchmark)
class ImageFileIoThroughputBenchmark {

    @Param("cafe-6400", "landscape-6400")
    lateinit var scenario: String

    private lateinit var tempDir: Path
    private lateinit var config: ScenarioConfig
    private lateinit var inputFiles: List<Path>
    private lateinit var payloadBytes: ByteArray

    @Setup(Level.Trial)
    fun setup() {
        tempDir = Files.createTempDirectory("bt4k-image-file-io-throughput-")
        config = ScenarioConfig.of(scenario)
        payloadBytes = Files.readAllBytes(config.fixturePath)
        inputFiles = List(config.readFileCount) { index ->
            tempDir.resolve("input-$index.jpg").also { target ->
                createInputLink(config.fixturePath, target)
            }
        }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    @Benchmark
    fun read_path_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            inputFiles.concurrentSum { path ->
                Files.newInputStream(path).use(::drainInput)
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    fun read_okioSource_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            inputFiles.concurrentSum { path ->
                Files.newInputStream(path).asSource().buffered().use { source ->
                    val sink = Buffer()
                    var totalBytes = 0L
                    while (true) {
                        val read = source.read(sink, STREAM_BUFFER_SIZE)
                        if (read == -1L) break
                        totalBytes += read
                        sink.clear()
                    }
                    totalBytes
                }
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    fun read_suspendedFileSource_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            inputFiles.concurrentSumSuspending { path ->
                val channel = AsynchronousFileChannel.open(path, READ)
                val source = channel.asSuspendedSource().bufferedSuspended()
                try {
                    val sink = Buffer()
                    var totalBytes = 0L
                    while (true) {
                        val read = source.read(sink, STREAM_BUFFER_SIZE)
                        if (read == -1L) break
                        totalBytes += read
                        sink.clear()
                    }
                    totalBytes
                } finally {
                    source.close()
                }
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    fun write_path_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            outputFiles("path").concurrentSum { path ->
                Files.newOutputStream(path, CREATE, WRITE, TRUNCATE_EXISTING).use { output ->
                    output.write(payloadBytes)
                }
                Files.size(path)
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    fun write_okioSink_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            outputFiles("okio").concurrentSum { path ->
                Files.newOutputStream(path, CREATE, WRITE, TRUNCATE_EXISTING).asSink().buffered().use { sink ->
                    sink.write(payloadBytes)
                    sink.flush()
                }
                Files.size(path)
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    fun write_suspendedFileSink_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            outputFiles("suspended").concurrentSumSuspending { path ->
                val channel = AsynchronousFileChannel.open(path, CREATE, WRITE, TRUNCATE_EXISTING)
                val sink = channel.asSuspendedSink().bufferedSuspended()
                try {
                    sink.write(payloadBytes)
                } finally {
                    sink.close()
                }
                Files.size(path)
            }
        }
        bh.consume(totalBytes)
    }

    private fun outputFiles(prefix: String): List<Path> =
        List(config.writeFileCount) { index ->
            tempDir.resolve("$prefix-output-$index.jpg")
        }

    private fun createInputLink(source: Path, target: Path) {
        runCatching {
            Files.createLink(target, source)
        }.getOrElse {
            Files.copy(source, target)
        }
    }

    private fun drainInput(input: java.io.InputStream): Long {
        val buffer = ByteArray(STREAM_BUFFER_SIZE.toInt())
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            totalBytes += read
        }
        return totalBytes
    }

    private suspend fun <T> Iterable<T>.concurrentSum(block: (T) -> Long): Long =
        coroutineScope {
            map { item ->
                async(Dispatchers.IO) {
                    block(item)
                }
            }.awaitAll().sum()
        }

    private suspend fun <T> Iterable<T>.concurrentSumSuspending(block: suspend (T) -> Long): Long =
        coroutineScope {
            map { item ->
                async(Dispatchers.IO) {
                    block(item)
                }
            }.awaitAll().sum()
        }

    private data class ScenarioConfig(
        val fixtureName: String,
        val readFileCount: Int,
        val writeFileCount: Int,
    ) {
        val fixturePath: Path by lazy {
            requireNotNull(BenchmarkImageSets.fixturePath(fixtureName)) {
                "$fixtureName fixture is required for file IO throughput benchmarks"
            }
        }

        companion object {
            fun of(scenario: String): ScenarioConfig =
                when (scenario) {
                    "cafe-6400" -> ScenarioConfig("cafe.jpg", readFileCount = 6_400, writeFileCount = 256)
                    "landscape-6400" -> ScenarioConfig("landscape.jpg", readFileCount = 6_400, writeFileCount = 256)
                    else -> error("Unknown file IO throughput scenario: $scenario")
                }
        }
    }
}
