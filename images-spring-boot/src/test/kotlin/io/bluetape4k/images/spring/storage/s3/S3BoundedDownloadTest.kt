package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.aws.spring.s3.S3ObjectMetadata
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.aws.spring.s3.S3Resource
import io.bluetape4k.aws.spring.s3.MicrometerS3Operations
import io.bluetape4k.images.spring.metrics.MetricImageStorage
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import io.bluetape4k.io.ByteLimitExceededException
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class S3BoundedDownloadTest {

    private val key = ImageObjectKey.of("uploads", "photo.jpg")

    @Test
    fun `bounded read through AWS decorator retains image storage success and error metrics`() = runTest {
        val registry = SimpleMeterRegistry()
        try {
            val success = MetricImageStorage(storageFor(ByteArrayInputStream(ByteArray(4)), registry), registry)
            success.download(key).size shouldBeEqualTo 4
            val oversized = MetricImageStorage(storageFor(ByteArrayInputStream(ByteArray(8)), registry), registry)
            assertFailsWith<ImageStorageException.ValidationException> { oversized.download(key) }

            registry.get("images.storage.download.duration").timer().count() shouldBeEqualTo 2L
            registry.get("images.storage.download.errors").counter().count() shouldBeEqualTo 1.0
            registry.find(MicrometerS3Operations.DEFAULT_METER_NAME).tag("operation", "resource")
                .timers().sumOf { it.count() } shouldBeEqualTo 2L
            registry.find(MicrometerS3Operations.DEFAULT_METER_NAME).tag("operation", "download")
                .timers().size shouldBeEqualTo 0
        } finally {
            registry.close()
        }
    }

    @Test
    fun `caller cancellation does not expose bytes and closes stream after read returns`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        var closed = false
        val input = object: ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                started.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS)) { "read was not released" }
                return super.read(bytes, offset, length)
            }
            override fun close() { closed = true }
        }
        val download = async { storageFor(input).download(key) }
        try {
            started.await()
            download.cancel()
        } finally {
            release.countDown()
        }
        download.join()
        download.isCancelled shouldBeEqualTo true
        closed shouldBeEqualTo true
    }

    @Test
    fun `exact limit preserves bytes and closes stream`() = runTest {
        var closed = false
        val expected = byteArrayOf(1, 2, 3, 4)
        val input = object: ByteArrayInputStream(expected) {
            override fun close() { closed = true }
        }

        storageFor(input).download(key).toList() shouldBeEqualTo expected.toList()
        closed shouldBeEqualTo true
    }

    @Test
    fun `read failure closes stream without returning partial bytes`() = runTest {
        var closed = false
        val input = object: InputStream() {
            override fun read(): Int = throw IOException("read failed")
            override fun close() { closed = true }
        }

        assertFailsWith<ImageStorageException.TransientException> { storageFor(input).download(key) }
        closed shouldBeEqualTo true
    }

    @Test
    fun `read cancellation is preserved and closes stream`() = runTest {
        var closed = false
        val cancellation = CancellationException("cancelled")
        val input = object: InputStream() {
            override fun read(): Int = throw cancellation
            override fun close() { closed = true }
        }

        val error = assertFailsWith<CancellationException> { storageFor(input).download(key) }
        error.message shouldBeEqualTo cancellation.message
        closed shouldBeEqualTo true
    }

    private fun storageFor(input: InputStream, registry: SimpleMeterRegistry? = null): S3ImageStorage {
        val operations = mockk<S3Operations>()
        val resource = mockk<S3Resource>()
        coEvery { operations.headObject("images", key.fullKey) } returns S3ObjectMetadata(sizeBytes = 4)
        every { operations.resource("images", key.fullKey) } returns resource
        every { resource.getInputStream() } returns input
        return S3ImageStorage(
            registry?.let { MicrometerS3Operations(operations, it) } ?: operations,
            ImageStorageProperties(backend = ImageStorageProperties.Backend.S3, bucket = "images", maxSizeBytes = 4),
        )
    }

    @Test
    fun `oversized body stops at limit plus one and closes stream`() = runTest {
        val operations = mockk<S3Operations>()
        val resource = mockk<S3Resource>()
        val key = ImageObjectKey.of("uploads", "photo.jpg")
        var consumed = 0
        var closed = false
        val stream = object: InputStream() {
            override fun read(): Int {
                consumed++
                return 1
            }

            override fun close() {
                closed = true
            }
        }
        coEvery { operations.headObject("images", key.fullKey) } returns S3ObjectMetadata(sizeBytes = 4)
        coEvery { operations.downloadBytes("images", key.fullKey) } returns ByteArray(8)
        every { operations.resource("images", key.fullKey) } returns resource
        every { resource.getInputStream() } returns stream
        val storage = S3ImageStorage(
            operations,
            ImageStorageProperties(backend = ImageStorageProperties.Backend.S3, bucket = "images", maxSizeBytes = 4),
        )

        val error = assertFailsWith<ImageStorageException.ValidationException> { storage.download(key) }
        (error.cause as ByteLimitExceededException).maxBytes shouldBeEqualTo 4

        consumed shouldBeEqualTo 5
        closed shouldBeEqualTo true
    }
}
