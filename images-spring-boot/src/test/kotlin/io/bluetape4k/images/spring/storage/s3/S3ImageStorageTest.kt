package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.aws.spring.s3.S3ObjectMetadata
import io.bluetape4k.aws.spring.s3.S3Resource
import io.bluetape4k.aws.spring.s3.S3TransferOperations
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageObjectMetadata
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.bluetape4k.images.spring.storage.ImageObjectMetadataReader
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
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
    fun `download fails closed when HEAD precheck fails`() = runTest {
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } throws RuntimeException("HEAD unavailable")
        coEvery { operations.downloadBytes(bucket = bucket, key = objectKey) } returns ByteArray(8)

        val error = assertFailsWith<ImageStorageException.TransientException> {
            storage.download(key)
        }

        error.key shouldBeEqualTo key
        verifyHeadPrecheck()
        verifyDownloadNotStarted()
        confirmVerified(operations)
    }

    @Test
    fun `download maps a missing object from HEAD without reading the body`() = runTest {
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } throws software.amazon.awssdk.services.s3.model.NoSuchKeyException.builder().build()
        coEvery { operations.downloadBytes(bucket = bucket, key = objectKey) } returns ByteArray(8)

        val error = assertFailsWith<ImageStorageException.NotFoundException> {
            storage.download(key)
        }

        error.key shouldBeEqualTo key
        verifyHeadPrecheck()
        verifyDownloadNotStarted()
        confirmVerified(operations)
    }

    @Test
    fun `download maps HEAD access denial without reading the body`() = runTest {
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } throws S3Exception.builder().statusCode(403).build()
        coEvery { operations.downloadBytes(bucket = bucket, key = objectKey) } returns ByteArray(4)

        val error = assertFailsWith<ImageStorageException.AccessDeniedException> {
            storage.download(key)
        }

        error.key shouldBeEqualTo key
        verifyHeadPrecheck()
        verifyDownloadNotStarted()
        confirmVerified(operations)
    }

    @Test
    fun `download rejects bytes that exceed maxSizeBytes after successful HEAD`() = runTest {
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } returns S3ObjectMetadata(sizeBytes = 4L)
        coEvery { operations.downloadBytes(bucket = bucket, key = objectKey) } returns ByteArray(8)

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key)
        }

        verifyHeadPrecheck()
        verifyDownloadedOnce()
        confirmVerified(operations)
    }

    @Test
    fun `download to destination rejects oversized bytes before writing`(@TempDir tempDir: Path) = runTest {
        val destination = tempDir.resolve("oversized.jpg")
        val resource = mockk<S3Resource>()
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } returns S3ObjectMetadata(sizeBytes = 8L)
        every { operations.resource(bucket, objectKey) } returns resource

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key, destination)
        }

        Files.exists(destination) shouldBeEqualTo false
        coVerify(exactly = 1) { operations.headObject(bucket = bucket, key = objectKey) }
        verify(exactly = 0) { operations.resource(any(), any()) }
        confirmVerified(operations, resource)
    }

    @Test
    fun `download propagates cancellation from HEAD precheck`() = runTest {
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> {
            storage.download(key)
        }

        verifyHeadPrecheck()
        verifyDownloadNotStarted()
        confirmVerified(operations)
    }

    @Test
    fun `reads metadata with one HEAD and no body read`() = runTest {
        val lastModified = java.time.Instant.parse("2026-08-15T00:00:01.123Z")
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } returns S3ObjectMetadata(
            sizeBytes = 4,
            etag = "\"opaque\"",
            contentType = "image/jpeg",
            lastModified = lastModified,
        )

        val metadata = (storage as ImageObjectMetadataReader).readMetadata(key)

        metadata shouldBeEqualTo ImageObjectMetadata(
            key = key,
            sizeBytes = 4,
            etag = "\"opaque\"",
            contentType = "image/jpeg",
            lastModified = lastModified,
        )
        coVerify(exactly = 1) { operations.headObject(bucket = bucket, key = objectKey) }
        coVerify(exactly = 0) { operations.downloadBytes(any(), any()) }
        verify(exactly = 0) { operations.resource(any(), any()) }
        coVerify(exactly = 0) { operations.listPage(any(), any(), any(), any()) }
        confirmVerified(operations)
    }

    @Test
    fun `byte download uses HEAD then body and checks snapshot size`() = runTest {
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } returns S3ObjectMetadata(sizeBytes = 4)
        coEvery {
            operations.downloadBytes(bucket = bucket, key = objectKey)
        } returns ByteArray(4) { it.toByte() }

        storage.download(key).size shouldBeEqualTo 4

        coVerifyOrder {
            operations.headObject(bucket = bucket, key = objectKey)
            operations.downloadBytes(bucket = bucket, key = objectKey)
        }
        coVerify(exactly = 1) { operations.headObject(bucket = bucket, key = objectKey) }
        coVerify(exactly = 1) { operations.downloadBytes(bucket = bucket, key = objectKey) }
        confirmVerified(operations)
    }

    @Test
    fun `byte download rejects a smaller body than the HEAD snapshot`() = runTest {
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } returns S3ObjectMetadata(sizeBytes = 4)
        coEvery {
            operations.downloadBytes(bucket = bucket, key = objectKey)
        } returns ByteArray(3)

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key)
        }

        coVerify(exactly = 1) { operations.headObject(bucket = bucket, key = objectKey) }
        coVerify(exactly = 1) { operations.downloadBytes(bucket = bucket, key = objectKey) }
        confirmVerified(operations)
    }

    @Test
    fun `byte download rejects a larger body than the HEAD snapshot`() = runTest {
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } returns S3ObjectMetadata(sizeBytes = 4)
        coEvery {
            operations.downloadBytes(bucket = bucket, key = objectKey)
        } returns ByteArray(5)

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key)
        }

        coVerify(exactly = 1) { operations.headObject(bucket = bucket, key = objectKey) }
        coVerify(exactly = 1) { operations.downloadBytes(bucket = bucket, key = objectKey) }
        confirmVerified(operations)
    }

    @Test
    fun `path download preserves destination and cleans staged file on snapshot mismatch`(@TempDir tempDir: Path) = runTest {
        val destination = tempDir.resolve("photo.jpg")
        val original = "existing".toByteArray()
        Files.write(destination, original)
        val resource = mockk<S3Resource>()
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } returns S3ObjectMetadata(sizeBytes = 4)
        every { operations.resource(bucket, objectKey) } returns resource
        every { resource.getInputStream() } returns ByteArrayInputStream(ByteArray(3))

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key, destination)
        }

        Files.readAllBytes(destination).contentEquals(original).shouldBeEqualTo(true)
        Files.list(tempDir).use { paths ->
            paths.noneMatch { it.fileName.toString().contains(".download") }.shouldBeEqualTo(true)
        }
        coVerify(exactly = 1) { operations.headObject(bucket = bucket, key = objectKey) }
        verify(exactly = 1) { operations.resource(bucket, objectKey) }
        verify(exactly = 1) { resource.getInputStream() }
        verify(exactly = 0) { resource.contentLength() }
        confirmVerified(operations, resource)
    }

    @Test
    fun `cancellation from HEAD is propagated before body read`() = runTest {
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> {
            storage.download(key)
        }

        coVerify(exactly = 1) { operations.headObject(bucket = bucket, key = objectKey) }
        coVerify(exactly = 0) { operations.downloadBytes(any(), any()) }
        verify(exactly = 0) { operations.resource(any(), any()) }
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
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } returns S3ObjectMetadata(sizeBytes = 4L)
        every { operations.resource(bucket, objectKey) } returns resource
        every { resource.getInputStream() } returns ByteArrayInputStream(ByteArray(4) { it.toByte() })

        storage.download(key, destination)

        Files.size(destination) shouldBeEqualTo 4L
        coVerify(exactly = 1) { operations.headObject(bucket = bucket, key = objectKey) }
        verify(exactly = 1) { operations.resource(bucket, objectKey) }
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
        coEvery {
            operations.headObject(bucket = bucket, key = objectKey)
        } returns S3ObjectMetadata(sizeBytes = 4L)
        every { resource.getInputStream() } returns cancelledInput

        assertFailsWith<CancellationException> {
            storage.download(key, destination)
        }

        Files.readAllBytes(destination).contentEquals(original).shouldBeEqualTo(true)
        coVerify(exactly = 1) { operations.headObject(bucket = bucket, key = objectKey) }
        verify(exactly = 1) { operations.resource(bucket, objectKey) }
        verify(exactly = 1) { resource.getInputStream() }
        confirmVerified(operations, resource)
    }

    private fun verifyHeadPrecheck() {
        coVerify(exactly = 1) {
            operations.headObject(bucket = bucket, key = objectKey)
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

}
