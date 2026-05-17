package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.UploadOptions
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFailsWith

class LocalImageStorageTest {

    private val tempDir: Path = Files.createTempDirectory("local-image-storage-test")
    private val storage = LocalImageStorage(tempDir, maxSizeBytes = 1024 * 1024L) // 1 MB

    private val key = ImageObjectKey.of("uploads", "photo.jpg")
    private val options = UploadOptions()
    private val sampleBytes = "fake image bytes".toByteArray()

    @Test
    fun `upload bytes stores data and returns correct result`() = runTest {
        val result = storage.upload(key, sampleBytes, options)

        assertEquals(key, result.key)
        assertTrue(result.etag.isNotBlank())
        assertEquals(sampleBytes.size.toLong(), result.sizeBytes)
        assertEquals(options.contentType, result.contentType)
        assertNotNull(result.uploadedAt)
    }

    @Test
    fun `upload bytes throws ValidationException when bytes exceed maxSizeBytes`() = runTest {
        val smallStorage = LocalImageStorage(tempDir, maxSizeBytes = 4L)
        val bigBytes = ByteArray(10) { it.toByte() }

        assertFailsWith<ImageStorageException.ValidationException> {
            smallStorage.upload(key, bigBytes, options)
        }
    }

    @Test
    fun `upload path copies file and returns correct result`() = runTest {
        val sourceFile = Files.createTempFile(tempDir, "source-", ".jpg")
        Files.write(sourceFile, sampleBytes)

        val pathKey = ImageObjectKey.of("originals", "source.jpg")
        val result = storage.upload(pathKey, sourceFile, options)

        assertEquals(pathKey, result.key)
        assertTrue(result.etag.isNotBlank())
        assertEquals(sampleBytes.size.toLong(), result.sizeBytes)
        assertEquals(options.contentType, result.contentType)
        assertNotNull(result.uploadedAt)
    }

    @Test
    fun `upload path throws ValidationException when file exceeds maxSizeBytes`() = runTest {
        val smallStorage = LocalImageStorage(tempDir, maxSizeBytes = 4L)
        val sourceFile = Files.createTempFile(tempDir, "big-", ".jpg")
        Files.write(sourceFile, ByteArray(10) { it.toByte() })

        assertFailsWith<ImageStorageException.ValidationException> {
            smallStorage.upload(key, sourceFile, options)
        }
    }

    @Test
    fun `upload path throws NotFoundException when source file is missing`() = runTest {
        val missingPath = tempDir.resolve("nonexistent-source.jpg")

        assertFailsWith<ImageStorageException.NotFoundException> {
            storage.upload(key, missingPath, options)
        }
    }

    @Test
    fun `download returns uploaded bytes`() = runTest {
        storage.upload(key, sampleBytes, options)

        val downloaded = storage.download(key)

        assertArrayEquals(sampleBytes, downloaded)
    }

    @Test
    fun `download throws NotFoundException when key not found`() = runTest {
        val missingKey = ImageObjectKey.of("missing", "file.jpg")

        assertFailsWith<ImageStorageException.NotFoundException> {
            storage.download(missingKey)
        }
    }

    @Test
    fun `download throws ValidationException when file exceeds maxSizeBytes`() = runTest {
        // Upload with a permissive storage instance (bypasses size check on upload)
        val permissiveStorage = LocalImageStorage(tempDir, maxSizeBytes = 1024 * 1024L * 10)
        val bigBytes = ByteArray(10) { it.toByte() }
        val bigKey = ImageObjectKey.of("big", "file.jpg")
        permissiveStorage.upload(bigKey, bigBytes, options)

        // Download with a restrictive storage pointing to the same directory
        val restrictiveStorage = LocalImageStorage(tempDir, maxSizeBytes = 4L)

        assertFailsWith<ImageStorageException.ValidationException> {
            restrictiveStorage.download(bigKey)
        }
    }

    @Test
    fun `download to path copies content to destination`() = runTest {
        storage.upload(key, sampleBytes, options)

        val destination = Files.createTempFile(tempDir, "dest-", ".jpg")
        storage.download(key, destination)

        assertArrayEquals(sampleBytes, Files.readAllBytes(destination))
    }

    @Test
    fun `download to path throws NotFoundException when key not found`() = runTest {
        val missingKey = ImageObjectKey.of("missing", "photo.jpg")
        val destination = Files.createTempFile(tempDir, "dest-", ".jpg")

        assertFailsWith<ImageStorageException.NotFoundException> {
            storage.download(missingKey, destination)
        }
    }

    @Test
    fun `delete removes an existing file`() = runTest {
        storage.upload(key, sampleBytes, options)
        assertTrue(storage.exists(key))

        storage.delete(key)

        assertFalse(storage.exists(key))
    }

    @Test
    fun `delete is idempotent for missing key`() = runTest {
        val missingKey = ImageObjectKey.of("nonexistent", "ghost.jpg")
        // Should not throw
        storage.delete(missingKey)
        assertFalse(storage.exists(missingKey))
    }

    @Test
    fun `exists returns true for existing key`() = runTest {
        storage.upload(key, sampleBytes, options)

        assertTrue(storage.exists(key))
    }

    @Test
    fun `exists returns false for non-existing key`() = runTest {
        val missingKey = ImageObjectKey.of("ghost", "image.jpg")

        assertFalse(storage.exists(missingKey))
    }

    @Test
    fun `list returns uploaded keys under prefix`() = runTest {
        // Upload files with a two-level prefix so the prefix directory is walkable.
        // key.fullKey = "photos/gallery/img1.jpg" → split gives prefix="photos", name="gallery/img1.jpg"
        // list is called with ImageObjectKey whose fullKey resolves to the "photos/gallery" directory.
        val key1 = ImageObjectKey.of("photos/gallery", "img1.jpg")
        val key2 = ImageObjectKey.of("photos/gallery", "img2.jpg")
        storage.upload(key1, sampleBytes, options)
        storage.upload(key2, sampleBytes, options)

        // prefix key whose fullKey = "photos/gallery" → resolves to the directory containing img1 and img2
        val listPrefix = ImageObjectKey.of("photos", "gallery")
        val listed = storage.list(listPrefix).toList()

        assertTrue(listed.any { it.fullKey == key1.fullKey }, "listed=$listed")
        assertTrue(listed.any { it.fullKey == key2.fullKey }, "listed=$listed")
    }

    @Test
    fun `list returns empty flow when prefix directory does not exist`() = runTest {
        val missingPrefix = ImageObjectKey.of("nonexistent", "prefix")

        val listed = storage.list(missingPrefix).toList()

        assertTrue(listed.isEmpty())
    }

    @Test
    fun `path traversal attempt is rejected by ImageObjectKey of`() {
        // ImageObjectKey.of validates before reaching storage — ".." in prefix is rejected
        assertFailsWith<IllegalArgumentException> {
            ImageObjectKey.of("../etc", "passwd")
        }
    }

    @Test
    fun `path traversal in name is rejected by ImageObjectKey of`() {
        assertFailsWith<IllegalArgumentException> {
            ImageObjectKey.of("uploads", "../secret.txt")
        }
    }
}
