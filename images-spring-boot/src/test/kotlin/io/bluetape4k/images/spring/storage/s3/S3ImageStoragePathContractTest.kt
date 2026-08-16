package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.bluetape4k.images.spring.storage.AbstractImageStoragePathContractTest
import io.bluetape4k.images.spring.storage.ImageStorage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class S3ImageStoragePathContractTest : AbstractImageStoragePathContractTest() {

    private val operations = StatefulS3Operations()
    private val transferOperations = StatefulS3TransferOperations(operations)

    override val storage: ImageStorage = S3ImageStorage(
        operations = operations,
        properties = ImageStorageProperties(
            backend = ImageStorageProperties.Backend.S3,
            bucket = "contract-images",
            maxSizeBytes = MAX_SIZE_BYTES,
        ),
        transferOperations = transferOperations,
    )

    override suspend fun prepareUpload(key: ImageObjectKey) = Unit

    override suspend fun seedStoredObject(key: ImageObjectKey, bytes: ByteArray) {
        operations.store(key.fullKey, bytes, "image/jpeg")
    }

    override fun assertNoOpenResources() {
        operations.hasOpenInputStreams().not().shouldBeTrue()
    }

    override fun stagedArtifacts(): List<Path> =
        Files.walk(contractDir).use { paths ->
            paths.filter { path ->
                path.fileName.toString().contains(".s3-upload") ||
                    path.fileName.toString().contains(".download")
            }.toList()
        }

    @Test
    fun `streaming 중 size limit 초과는 destination과 resource를 정리한다`() = runTest {
        val key = ImageObjectKey.of("contract/path", "streaming-overflow.jpg")
        val destination = contractDir.resolve("streaming-overflow-destination.jpg")
        val existing = "existing-destination".toByteArray()
        Files.write(destination, existing)
        operations.store(key.fullKey, ByteArray(MAX_SIZE_BYTES.toInt() + 1), "image/jpeg")
        operations.overrideReportedSize(key.fullKey, MAX_SIZE_BYTES)

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key, destination)
        }

        Files.readAllBytes(destination).contentEquals(existing).shouldBeTrue()
        stagedArtifacts().isEmpty().shouldBeTrue()
        assertNoOpenResources()
    }
}
