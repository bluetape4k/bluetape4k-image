package io.bluetape4k.images.spring.storage

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.UploadOptions
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LocalImageStorageMetadataTest {

    private val root: Path = Files.createTempDirectory("local-image-storage-metadata")
    private val key = ImageObjectKey.of("uploads", "photo.jpg")
    private val storage = LocalImageStorage(root, maxSizeBytes = 1024 * 1024L)

    @Test
    fun `reads attributes without materializing the body`() = runTest {
        val path = root.resolve(key.fullKey)
        Files.createDirectories(path.parent)
        val bytes = "image bytes".toByteArray()
        Files.write(path, bytes)
        val expectedLastModified = Files.getLastModifiedTime(path).toInstant()

        val metadata = (storage as ImageObjectMetadataReader).readMetadata(key)

        metadata.key shouldBeEqualTo key
        metadata.sizeBytes shouldBeEqualTo bytes.size.toLong()
        metadata.etag shouldBeEqualTo null
        metadata.contentType shouldBeEqualTo null
        metadata.lastModified shouldBeEqualTo expectedLastModified
        Files.readAllBytes(path).contentEquals(bytes).shouldBeTrue()
    }

    @Test
    fun `reports a missing object`() = runTest {
        val error = assertFailsWith<ImageStorageException.NotFoundException> {
            (storage as ImageObjectMetadataReader).readMetadata(key)
        }

        error.key shouldBeEqualTo key
    }

    @Test
    fun `rejects a directory object`() = runTest {
        val directoryKey = ImageObjectKey.of("uploads", "directory")
        val path = root.resolve(directoryKey.fullKey)
        Files.createDirectories(path)

        assertFailsWith<ImageStorageException.ValidationException> {
            (storage as ImageObjectMetadataReader).readMetadata(directoryKey)
        }
    }

    @Test
    fun `rejects a symbolic link object`() = runTest {
        val outside = Files.createTempDirectory("local-image-storage-metadata-outside")
        val linkKey = ImageObjectKey.of("uploads", "linked.jpg")
        val link = root.resolve(linkKey.fullKey)
        Files.createDirectories(link.parent)
        val target = outside.resolve("target.jpg")
        Files.write(target, byteArrayOf(1, 2, 3))
        Files.createSymbolicLink(link, target)

        assertFailsWith<ImageStorageException.ValidationException> {
            (storage as ImageObjectMetadataReader).readMetadata(linkKey)
        }
    }
}
