package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.aws.spring.s3.S3ListPage
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.services.s3.model.S3Object
import java.nio.file.Files
import java.nio.file.Path

class S3ImageStorageTest {

    private val operations = mockk<S3Operations>()
    private lateinit var storage: S3ImageStorage

    private val bucket = "images"
    private val key = ImageObjectKey.of("uploads", "photo.jpg")
    private val objectKey = key.fullKey

    @BeforeEach
    fun setUp() {
        clearMocks(operations)
        storage = S3ImageStorage(
            operations = operations,
            properties = ImageStorageProperties(
                backend = ImageStorageProperties.Backend.S3,
                bucket = bucket,
                maxSizeBytes = 4L,
            ),
        )
    }

    @Test
    fun `download fails closed when size precheck fails`() = runTest {
        coEvery {
            operations.listPage(bucket = bucket, prefix = objectKey, maxKeys = 1)
        } throws RuntimeException("list unavailable")
        coEvery { operations.downloadBytes(bucket = bucket, key = objectKey) } returns ByteArray(8)

        val error = assertFailsWith<ImageStorageException.TransientException> {
            storage.download(key)
        }

        error.key shouldBeEqualTo key
        verifySizePrecheck()
        verifyDownloadNotStarted()
        confirmVerified(operations)
    }

    @Test
    fun `download fails closed when size precheck cannot find exact key`() = runTest {
        coEvery {
            operations.listPage(bucket = bucket, prefix = objectKey, maxKeys = 1)
        } returns s3Page()
        coEvery { operations.downloadBytes(bucket = bucket, key = objectKey) } returns ByteArray(8)

        val error = assertFailsWith<ImageStorageException.NotFoundException> {
            storage.download(key)
        }

        error.key shouldBeEqualTo key
        verifySizePrecheck()
        verifyDownloadNotStarted()
        confirmVerified(operations)
    }

    @Test
    fun `download rejects bytes that exceed maxSizeBytes after successful precheck`() = runTest {
        coEvery {
            operations.listPage(bucket = bucket, prefix = objectKey, maxKeys = 1)
        } returns s3Page(s3Object(objectKey, size = 4L))
        coEvery { operations.downloadBytes(bucket = bucket, key = objectKey) } returns ByteArray(8)

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key)
        }

        verifySizePrecheck()
        verifyDownloadedOnce()
        confirmVerified(operations)
    }

    @Test
    fun `download to destination rejects oversized bytes before writing`(@TempDir tempDir: Path) = runTest {
        val destination = tempDir.resolve("oversized.jpg")
        coEvery {
            operations.listPage(bucket = bucket, prefix = objectKey, maxKeys = 1)
        } returns s3Page(s3Object(objectKey, size = 4L))
        coEvery { operations.downloadBytes(bucket = bucket, key = objectKey) } returns ByteArray(8)

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key, destination)
        }

        Files.exists(destination) shouldBeEqualTo false
        verifySizePrecheck()
        verifyDownloadedOnce()
        confirmVerified(operations)
    }

    @Test
    fun `download propagates cancellation from size precheck`() = runTest {
        coEvery {
            operations.listPage(bucket = bucket, prefix = objectKey, maxKeys = 1)
        } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> {
            storage.download(key)
        }

        verifySizePrecheck()
        verifyDownloadNotStarted()
        confirmVerified(operations)
    }

    private fun verifySizePrecheck() {
        coVerify(exactly = 1) {
            operations.listPage(bucket = bucket, prefix = objectKey, maxKeys = 1)
        }
    }

    private fun verifyDownloadNotStarted() {
        coVerify(exactly = 0) {
            operations.downloadBytes(any(), any())
        }
    }

    private fun verifyDownloadedOnce() {
        coVerify(exactly = 1) {
            operations.downloadBytes(bucket = bucket, key = objectKey)
        }
    }

    private fun s3Page(vararg objects: S3Object): S3ListPage =
        S3ListPage(
            objects = objects.toList(),
            isTruncated = false,
            nextContinuationToken = null,
            keyCount = objects.size,
        )

    private fun s3Object(key: String, size: Long): S3Object =
        S3Object.builder()
            .key(key)
            .size(size)
            .build()
}
