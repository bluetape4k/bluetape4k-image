package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.forSuspendWriter
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.suspendLoadImage
import io.bluetape4k.images.suspendWrite
import io.bluetape4k.images.coroutines.SuspendJpegWriter
import io.bluetape4k.okio.asSink
import io.bluetape4k.okio.asSource
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.asSuspendedSink
import io.bluetape4k.okio.coroutines.asSuspendedSource
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
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.TimeUnit

/**
 * Compares image load/write boundary choices for the same source image.
 *
 * This benchmark is intentionally about boundary overhead and compressed-file
 * staging, not about changing Scrimage's internal decode/encode cost.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = ["--enable-native-access=ALL-UNNAMED"])
@State(Scope.Benchmark)
class ImageIoBoundaryBenchmark {

    private lateinit var tempDir: Path
    private lateinit var homerPath: Path
    private lateinit var landscapePath: Path
    private lateinit var homerBytes: ByteArray
    private lateinit var image: ImmutableImage

    private val writer = SuspendJpegWriter.Default

    @Setup(Level.Trial)
    fun setup() {
        tempDir = Files.createTempDirectory("bt4k-image-io-boundary-")
        homerPath = requireNotNull(BenchmarkImageSets.thumbnailPath) {
            "homer.jpg fixture is required for IO boundary benchmarks"
        }
        landscapePath = requireNotNull(BenchmarkImageSets.photo4kPath) {
            "landscape.jpg fixture is required for IO boundary benchmarks"
        }
        homerBytes = Files.readAllBytes(homerPath)
        image = BenchmarkImageSets.thumbnail
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    @Benchmark
    fun load_homer_byteArray(bh: Blackhole) {
        bh.consume(immutableImageOf(homerBytes))
    }

    @Benchmark
    fun load_homer_inputStream(bh: Blackhole) {
        Files.newInputStream(homerPath).use { input ->
            bh.consume(immutableImageOf(input))
        }
    }

    @Benchmark
    fun load_homer_path(bh: Blackhole) {
        bh.consume(immutableImageOf(homerPath))
    }

    @Benchmark
    fun load_homer_okioSource(bh: Blackhole) {
        Files.newInputStream(homerPath).asSource().buffered().use { source ->
            bh.consume(immutableImageOf(source))
        }
    }

    @Benchmark
    fun load_homer_suspendedFileSource(bh: Blackhole) {
        val channel = AsynchronousFileChannel.open(homerPath, READ)
        val image = runBlocking {
            suspendLoadImage(channel.asSuspendedSource())
        }
        bh.consume(image)
    }

    @Benchmark
    fun load_landscape_path(bh: Blackhole) {
        bh.consume(immutableImageOf(landscapePath))
    }

    @Benchmark
    fun load_landscape_suspendedFileSource(bh: Blackhole) {
        val channel = AsynchronousFileChannel.open(landscapePath, READ)
        val image = runBlocking {
            suspendLoadImage(channel.asSuspendedSource())
        }
        bh.consume(image)
    }

    @Benchmark
    fun write_homer_byteArray(bh: Blackhole) {
        val bytes = runBlocking {
            image.suspendWriteToBytes()
        }
        bh.consume(bytes)
    }

    @Benchmark
    fun write_homer_outputStream(bh: Blackhole) {
        val output = createOutput("output-stream")
        try {
            Files.newOutputStream(output).use { stream ->
                runBlocking {
                    image.forSuspendWriter(writer).write(stream)
                }
            }
            bh.consume(Files.size(output))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Benchmark
    fun write_homer_path(bh: Blackhole) {
        val output = createOutput("path")
        try {
            val bytes = runBlocking {
                image.suspendWrite(writer, output)
            }
            bh.consume(bytes)
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Benchmark
    fun write_homer_okioSink(bh: Blackhole) {
        val output = createOutput("okio-sink")
        try {
            Files.newOutputStream(output).asSink().buffered().use { sink ->
                runBlocking {
                    image.suspendWrite(writer, sink)
                }
            }
            bh.consume(Files.size(output))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Benchmark
    fun write_homer_suspendedFileSink(bh: Blackhole) {
        val output = createOutput("suspended-sink")
        try {
            val channel = AsynchronousFileChannel.open(output, WRITE, CREATE, TRUNCATE_EXISTING)
            runBlocking {
                image.suspendWrite(writer, channel.asSuspendedSink())
            }
            bh.consume(Files.size(output))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private suspend fun ImmutableImage.suspendWriteToBytes(): ByteArray =
        this.forSuspendWriter(writer).bytes()

    private fun createOutput(prefix: String): Path =
        Files.createTempFile(tempDir, "$prefix-", ".jpg")
}
