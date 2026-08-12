package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.aws.spring.s3.S3ListPage
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.aws.spring.s3.S3Resource
import io.bluetape4k.aws.spring.s3.S3TransferOperations
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.services.s3.model.S3Object
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload

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
        val resource = mockk<S3Resource>()
        every { operations.resource(bucket, objectKey) } returns resource
        every { resource.contentLength() } returns 8L

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key, destination)
        }

        Files.exists(destination) shouldBeEqualTo false
        verify(exactly = 1) { operations.resource(bucket, objectKey) }
        verify(exactly = 1) { resource.contentLength() }
        confirmVerified(operations, resource)
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

    @Test
    fun `path upload fails closed instead of loading source into byte array`() = runTest {
        val source = Files.createTempFile("s3-image-storage-source", ".jpg")
        Files.write(source, ByteArray(4) { it.toByte() })
        val response = mockk<software.amazon.awssdk.services.s3.model.PutObjectResponse>()
        coEvery {
            operations.upload(
                bucket = bucket,
                key = objectKey,
                bytes = any(),
                contentType = "image/jpeg",
            )
        } returns response

        assertFailsWith<ImageStorageException.TransientException> {
            storage.upload(key, source, io.bluetape4k.images.spring.UploadOptions())
        }

        coVerify(exactly = 0) {
            operations.upload(bucket = bucket, key = objectKey, bytes = any(), contentType = any())
        }
        confirmVerified(operations)
    }

    @Test
    fun `path upload uses S3 transfer operations`() = runTest {
        val source = Files.createTempFile("s3-image-storage-source", ".jpg")
        Files.write(source, ByteArray(4) { it.toByte() })
        val transfer = mockk<S3TransferOperations>()
        val stagedSource = slot<Path>()
        val completedUpload = mockk<CompletedFileUpload>()
        val response = mockk<PutObjectResponse>()
        every { completedUpload.response() } returns response
        every { response.eTag() } returns "etag"
        coEvery { transfer.uploadFile(bucket, objectKey, capture(stagedSource), any()) } returns completedUpload
        val transferStorage = S3ImageStorage(
            operations = operations,
            properties = ImageStorageProperties(
                backend = ImageStorageProperties.Backend.S3,
                bucket = bucket,
                maxSizeBytes = 4L,
            ),
            transferOperations = transfer,
        )

        val result = transferStorage.upload(key, source, UploadOptions())

        result.etag shouldBeEqualTo "etag"
        result.sizeBytes shouldBeEqualTo 4L
        coVerify(exactly = 1) { transfer.uploadFile(bucket, objectKey, stagedSource.captured, any()) }
        (stagedSource.captured != source).shouldBeEqualTo(true)
        Files.exists(stagedSource.captured).shouldBeEqualTo(false)
        coVerify(exactly = 0) {
            operations.upload(bucket = bucket, key = objectKey, bytes = any(), contentType = any())
        }
        confirmVerified(operations, transfer)
    }

    @Test
    fun `path download streams through resource instead of byte array`() = runTest {
        val destination = Files.createTempFile("s3-image-storage-destination", ".jpg")
        val resource = mockk<S3Resource>()
        every { operations.resource(bucket, objectKey) } returns resource
        every { resource.contentLength() } returns 4L
        every { resource.getInputStream() } returns ByteArrayInputStream(ByteArray(4) { it.toByte() })

        storage.download(key, destination)

        Files.size(destination) shouldBeEqualTo 4L
        verify(exactly = 1) { operations.resource(bucket, objectKey) }
        verify(exactly = 1) { resource.contentLength() }
        verify(exactly = 1) { resource.getInputStream() }
        coVerify(exactly = 0) {
            operations.downloadBytes(bucket = bucket, key = objectKey)
        }
        confirmVerified(operations, resource)
    }

    @Test
    fun `path download preserves destination when the stream is cancelled`() = runTest {
        val destination = Files.createTempFile("s3-image-storage-cancelled", ".jpg")
        val original = "existing".toByteArray()
        Files.write(destination, original)
        val resource = mockk<S3Resource>()
        val cancelledInput = object : ByteArrayInputStream(ByteArray(4)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                throw CancellationException("cancelled")
        }
        every { operations.resource(bucket, objectKey) } returns resource
        every { resource.contentLength() } returns 4L
        every { resource.getInputStream() } returns cancelledInput

        assertFailsWith<CancellationException> {
            storage.download(key, destination)
        }

        Files.readAllBytes(destination).contentEquals(original).shouldBeEqualTo(true)
        verify(exactly = 1) { operations.resource(bucket, objectKey) }
        verify(exactly = 1) { resource.contentLength() }
        verify(exactly = 1) { resource.getInputStream() }
        confirmVerified(operations, resource)
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
