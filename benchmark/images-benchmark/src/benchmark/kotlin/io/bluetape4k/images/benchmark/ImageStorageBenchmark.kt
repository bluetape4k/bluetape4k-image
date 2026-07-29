package io.bluetape4k.images.benchmark

import io.bluetape4k.aws.spring.s3.S3ListPage
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.aws.spring.s3.S3Resource
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.images.spring.storage.LocalImageStorage
import io.bluetape4k.images.spring.storage.s3.S3ImageStorage
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Object
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Storage API benchmark입니다. S3 method는 in-memory S3Operations double을 사용합니다. credential이나 network 없이
 * storage adapter와 byte materialization overhead를 측정하기 위해서입니다.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class ImageStorageBenchmark {

    @Param("jpeg", "png")
    var format: String = "jpeg"

    private lateinit var payload: ByteArray
    private lateinit var oversizedPayload: ByteArray
    private lateinit var key: ImageObjectKey
    private lateinit var local: ImageStorage
    private lateinit var s3: ImageStorage
    private lateinit var localRoot: Path
    private lateinit var destination: Path

    @Setup
    fun setup() {
        payload = if (format == "jpeg") {
            BenchmarkImageSets.thumbnail.bytes(com.sksamuel.scrimage.nio.JpegWriter(80, false))
        } else {
            BenchmarkImageSets.thumbnail.bytes(com.sksamuel.scrimage.nio.PngWriter(6))
        }
        oversizedPayload = ByteArray(MAX_SIZE_BYTES + 1)
        key = ImageObjectKey.of("bench/items", "payload.$format")
        localRoot = Files.createTempDirectory("bluetape4k-storage-benchmark-")
        destination = localRoot.resolve("download.$format")
        local = LocalImageStorage(localRoot.resolve("local"), MAX_SIZE_BYTES.toLong())
        s3 = S3ImageStorage(
            InMemoryS3Operations(),
            ImageStorageProperties(
                backend = ImageStorageProperties.Backend.S3,
                bucket = "benchmark",
                maxSizeBytes = MAX_SIZE_BYTES.toLong(),
            ),
        )
        runBlocking {
            local.upload(key, payload, UploadOptions(contentType = contentType()))
            s3.upload(key, payload, UploadOptions(contentType = contentType()))
            repeat(LIST_OBJECT_COUNT) { index ->
                val listKey = ImageObjectKey.of("bench/items", "item-$index.$format")
                local.upload(listKey, payload, UploadOptions(contentType = contentType()))
                s3.upload(listKey, payload, UploadOptions(contentType = contentType()))
            }
        }
    }

    @TearDown
    fun tearDown() {
        localRoot.toFile().deleteRecursively()
    }

    @Benchmark
    fun local_uploadBytes(bh: Blackhole) = runBlocking {
        bh.consume(local.upload(key, payload, UploadOptions(contentType = contentType())).sizeBytes)
    }

    @Benchmark
    fun local_downloadBytes(bh: Blackhole) = runBlocking { bh.consume(local.download(key)) }

    @Benchmark
    fun local_downloadToPath(bh: Blackhole) = runBlocking {
        local.download(key, destination)
        bh.consume(Files.size(destination))
    }

    @Benchmark
    fun local_list(bh: Blackhole) = runBlocking {
        bh.consume(local.list(ImageObjectKey.of("bench", "items")).toList().size)
    }

    @Benchmark
    fun local_uploadOverLimit(bh: Blackhole) = runBlocking {
        val result = runCatching {
            local.upload(key, oversizedPayload, UploadOptions(contentType = contentType()))
        }
        bh.consume(result.exceptionOrNull())
    }

    @Benchmark
    fun s3_uploadBytes(bh: Blackhole) = runBlocking {
        bh.consume(s3.upload(key, payload, UploadOptions(contentType = contentType())).sizeBytes)
    }

    @Benchmark
    fun s3_downloadBytes(bh: Blackhole) = runBlocking { bh.consume(s3.download(key)) }

    @Benchmark
    fun s3_downloadToPath(bh: Blackhole) = runBlocking {
        s3.download(key, destination)
        bh.consume(Files.size(destination))
    }

    @Benchmark
    fun s3_list(bh: Blackhole) = runBlocking {
        bh.consume(s3.list(ImageObjectKey.of("bench", "items")).toList().size)
    }

    @Benchmark
    fun s3_uploadOverLimit(bh: Blackhole) = runBlocking {
        val result = runCatching {
            s3.upload(key, oversizedPayload, UploadOptions(contentType = contentType()))
        }
        bh.consume(result.exceptionOrNull())
    }

    private fun contentType(): String = if (format == "jpeg") "image/jpeg" else "image/png"

    private companion object {
        private const val MAX_SIZE_BYTES = 4 * 1024 * 1024
        private const val LIST_OBJECT_COUNT = 8
    }
}

private class InMemoryS3Operations : S3Operations {
    private val objects = ConcurrentHashMap<String, ByteArray>()

    override suspend fun existsBucket(bucket: String): Boolean = true

    override suspend fun upload(bucket: String, key: String, bytes: ByteArray, contentType: String?): PutObjectResponse {
        objects["$bucket/$key"] = bytes.copyOf()
        return PutObjectResponse.builder().eTag(bytes.size.toString()).build()
    }

    override suspend fun upload(bucket: String, key: String, contents: String, charset: java.nio.charset.Charset, contentType: String?): PutObjectResponse =
        upload(bucket, key, contents.toByteArray(charset), contentType)

    override suspend fun downloadBytes(bucket: String, key: String): ByteArray =
        objects["$bucket/$key"]?.copyOf() ?: ByteArray(0)

    override suspend fun downloadText(bucket: String, key: String, charset: java.nio.charset.Charset): String =
        downloadBytes(bucket, key).toString(charset)

    override suspend fun delete(bucket: String, key: String): DeleteObjectResponse {
        objects.remove("$bucket/$key")
        return DeleteObjectResponse.builder().build()
    }

    override suspend fun listPage(bucket: String, prefix: String?, maxKeys: Int, continuationToken: String?): S3ListPage {
        val keys = objects.keys.filter { key -> key.startsWith("$bucket/${prefix.orEmpty()}") }.map { it.substringAfter("$bucket/") }
        val values = keys.take(maxKeys).map { key -> S3Object.builder().key(key).size(objects["$bucket/$key"]!!.size.toLong()).build() }
        return S3ListPage(values, keys.size > maxKeys, null, values.size)
    }

    override fun listFlow(bucket: String, prefix: String?, pageSize: Int): kotlinx.coroutines.flow.Flow<S3Object> = kotlinx.coroutines.flow.flow {
        objects.keys.filter { it.startsWith("$bucket/${prefix.orEmpty()}") }.forEach { full ->
            val key = full.substringAfter("$bucket/")
            emit(S3Object.builder().key(key).size(objects[full]!!.size.toLong()).build())
        }
    }

    override fun resource(bucket: String, key: String): S3Resource = throw UnsupportedOperationException("benchmark resource")

    override fun presignGet(bucket: String, key: String, duration: Duration?): URL = URI("https://example.test/$bucket/$key").toURL()

    override fun presignPut(bucket: String, key: String, duration: Duration?, contentType: String?): URL = URI("https://example.test/$bucket/$key").toURL()
}
