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
import org.openjdk.jmh.annotations.OperationsPerInvocation
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.TimeUnit

private const val THROUGHPUT_FILE_COUNT = 64

/**
 * Compares compressed image file IO boundaries under concurrent many-file load.
 *
 * This benchmark intentionally excludes Scrimage decode/encode. It measures
 * how file boundary choices behave when many already-compressed image files are
 * read or written concurrently.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = ["--enable-native-access=ALL-UNNAMED"])
@State(Scope.Benchmark)
class ImageFileIoThroughputBenchmark {

    private lateinit var tempDir: Path
    private lateinit var inputFiles: List<Path>
    private lateinit var homerBytes: ByteArray

    @Setup(Level.Trial)
    fun setup() {
        tempDir = Files.createTempDirectory("bt4k-image-file-io-throughput-")
        val homerPath = requireNotNull(BenchmarkImageSets.thumbnailPath) {
            "homer.jpg fixture is required for file IO throughput benchmarks"
        }
        homerBytes = Files.readAllBytes(homerPath)
        inputFiles = List(THROUGHPUT_FILE_COUNT) { index ->
            tempDir.resolve("input-$index.jpg").also { target ->
                Files.copy(homerPath, target)
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
    @OperationsPerInvocation(THROUGHPUT_FILE_COUNT)
    fun read_path_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            inputFiles.concurrentSum { path ->
                Files.readAllBytes(path).size.toLong()
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    @OperationsPerInvocation(THROUGHPUT_FILE_COUNT)
    fun read_okioSource_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            inputFiles.concurrentSum { path ->
                Files.newInputStream(path).asSource().buffered().use { source ->
                    source.readByteArray().size.toLong()
                }
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    @OperationsPerInvocation(THROUGHPUT_FILE_COUNT)
    fun read_suspendedFileSource_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            inputFiles.concurrentSumSuspending { path ->
                val channel = AsynchronousFileChannel.open(path, READ)
                val source = channel.asSuspendedSource().bufferedSuspended()
                try {
                    source.readByteArray().size.toLong()
                } finally {
                    source.close()
                }
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    @OperationsPerInvocation(THROUGHPUT_FILE_COUNT)
    fun write_path_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            outputFiles("path").concurrentSum { path ->
                Files.write(path, homerBytes, CREATE, WRITE, TRUNCATE_EXISTING)
                Files.size(path)
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    @OperationsPerInvocation(THROUGHPUT_FILE_COUNT)
    fun write_okioSink_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            outputFiles("okio").concurrentSum { path ->
                Files.newOutputStream(path, CREATE, WRITE, TRUNCATE_EXISTING).asSink().buffered().use { sink ->
                    sink.write(homerBytes)
                    sink.flush()
                }
                Files.size(path)
            }
        }
        bh.consume(totalBytes)
    }

    @Benchmark
    @OperationsPerInvocation(THROUGHPUT_FILE_COUNT)
    fun write_suspendedFileSink_concurrent(bh: Blackhole) {
        val totalBytes = runBlocking {
            outputFiles("suspended").concurrentSumSuspending { path ->
                val channel = AsynchronousFileChannel.open(path, CREATE, WRITE, TRUNCATE_EXISTING)
                val sink = channel.asSuspendedSink().bufferedSuspended()
                try {
                    sink.write(homerBytes)
                } finally {
                    sink.close()
                }
                Files.size(path)
            }
        }
        bh.consume(totalBytes)
    }

    private fun outputFiles(prefix: String): List<Path> =
        List(THROUGHPUT_FILE_COUNT) { index ->
            tempDir.resolve("$prefix-output-$index.jpg")
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
}
