package io.bluetape4k.images.spring.storage

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.UploadOptions
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** [ImageStorage] 구현체가 공유하는 Path streaming과 destination 안전 계약입니다. */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
abstract class AbstractImageStoragePathContractTest {

    @field:TempDir
    protected lateinit var contractDir: Path

    protected abstract val storage: ImageStorage

    protected abstract suspend fun prepareUpload(key: ImageObjectKey)

    protected abstract suspend fun seedStoredObject(key: ImageObjectKey, bytes: ByteArray)

    protected abstract fun assertNoOpenResources()

    protected abstract fun stagedArtifacts(): List<Path>

    private val options = UploadOptions(contentType = "image/jpeg")

    @Test
    fun `Path upload 결과를 destination Path로 download한다`() = runTest {
        val key = contractKey("round-trip.jpg")
        val bytes = "path-round-trip".toByteArray()
        val source = contractDir.resolve("source.jpg")
        val destination = contractDir.resolve("destination.jpg")
        Files.write(source, bytes)
        prepareUpload(key)

        val uploaded = storage.upload(key, source, options)
        storage.download(key, destination)

        uploaded.key shouldBeEqualTo key
        uploaded.sizeBytes shouldBeEqualTo bytes.size.toLong()
        Files.readAllBytes(source).contentEquals(bytes).shouldBeTrue()
        Files.readAllBytes(destination).contentEquals(bytes).shouldBeTrue()
        assertCleanResources()
    }

    @Test
    fun `oversized Path upload는 기존 object를 보존한다`() = runTest {
        val key = contractKey("oversized-upload.jpg")
        val existing = "existing".toByteArray()
        val source = contractDir.resolve("oversized-source.jpg")
        Files.write(source, ByteArray(MAX_SIZE_BYTES.toInt() + 1) { 1 })
        prepareUpload(key)
        storage.upload(key, existing, options)

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.upload(key, source, options)
        }

        storage.download(key).contentEquals(existing).shouldBeTrue()
        assertCleanResources()
    }

    @Test
    fun `Path upload streaming 실패는 기존 object와 staged cleanup을 보존한다`() = runTest {
        val key = contractKey("failed-stream-upload.jpg")
        val existing = "existing".toByteArray()
        val sourceTarget = contractDir.resolve("source-target.jpg")
        val sourceLink = contractDir.resolve("source-link.jpg")
        Files.write(sourceTarget, "replacement".toByteArray())
        Files.createSymbolicLink(sourceLink, sourceTarget)
        prepareUpload(key)
        storage.upload(key, existing, options)

        assertFailsWith<ImageStorageException.TransientException> {
            storage.upload(key, sourceLink, options)
        }

        storage.download(key).contentEquals(existing).shouldBeTrue()
        assertCleanResources()
    }

    @Test
    fun `missing key Path download는 기존 destination을 보존한다`() = runTest {
        val key = contractKey("missing.jpg")
        val destination = contractDir.resolve("missing-destination.jpg")
        val existing = "existing-destination".toByteArray()
        Files.write(destination, existing)
        prepareUpload(key)

        assertFailsWith<ImageStorageException.NotFoundException> {
            storage.download(key, destination)
        }

        Files.readAllBytes(destination).contentEquals(existing).shouldBeTrue()
        assertCleanResources()
    }

    @Test
    fun `oversized stored object Path download는 기존 destination을 보존한다`() = runTest {
        val key = contractKey("oversized-download.jpg")
        val destination = contractDir.resolve("oversized-destination.jpg")
        val existing = "existing-destination".toByteArray()
        Files.write(destination, existing)
        seedStoredObject(key, ByteArray(MAX_SIZE_BYTES.toInt() + 1) { 2 })

        assertFailsWith<ImageStorageException.ValidationException> {
            storage.download(key, destination)
        }

        Files.readAllBytes(destination).contentEquals(existing).shouldBeTrue()
        assertCleanResources()
    }

    private fun assertCleanResources() {
        stagedArtifacts().isEmpty().shouldBeTrue()
        assertNoOpenResources()
    }

    private fun contractKey(name: String): ImageObjectKey =
        ImageObjectKey.of("contract/path", name)

    protected companion object {
        const val MAX_SIZE_BYTES = 32L
    }
}
