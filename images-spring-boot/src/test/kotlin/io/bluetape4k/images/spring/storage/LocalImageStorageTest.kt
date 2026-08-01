package io.bluetape4k.images.spring.storage

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.UploadOptions
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LocalImageStorageTest {

    private val tempDir: Path = Files.createTempDirectory("local-image-storage-test")
    private val storage = LocalImageStorage(tempDir, maxSizeBytes = 1024 * 1024L) // 1 MB

    private val key = ImageObjectKey.of("uploads", "photo.jpg")
    private val options = UploadOptions()
    private val sampleBytes = "fake image bytes".toByteArray()

    @Test
    fun `upload bytes stores data and returns correct result`() = runTest {
        val result = storage.upload(key, sampleBytes, options)

        result.key shouldBeEqualTo key
        result.etag.isNotBlank().shouldBeTrue()
        result.sizeBytes shouldBeEqualTo sampleBytes.size.toLong()
        result.contentType shouldBeEqualTo options.contentType
        result.uploadedAt.shouldNotBeNull()
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

        result.key shouldBeEqualTo pathKey
        result.etag.isNotBlank().shouldBeTrue()
        result.sizeBytes shouldBeEqualTo sampleBytes.size.toLong()
        result.contentType shouldBeEqualTo options.contentType
        result.uploadedAt.shouldNotBeNull()
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

        downloaded.contentEquals(sampleBytes).shouldBeTrue()
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
        // upload size check를 통과하도록 permissive storage instance로 먼저 저장합니다.
        val permissiveStorage = LocalImageStorage(tempDir, maxSizeBytes = 1024 * 1024L * 10)
        val bigBytes = ByteArray(10) { it.toByte() }
        val bigKey = ImageObjectKey.of("big", "file.jpg")
        permissiveStorage.upload(bigKey, bigBytes, options)

        // 같은 directory를 바라보는 restrictive storage로 download 제한을 검증합니다.
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

        Files.readAllBytes(destination).contentEquals(sampleBytes).shouldBeTrue()
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
    fun `download to path throws ValidationException when file exceeds maxSizeBytes`() = runTest {
        val permissiveStorage = LocalImageStorage(tempDir, maxSizeBytes = 1024 * 1024L * 10)
        val bigBytes = ByteArray(10) { it.toByte() }
        val bigKey = ImageObjectKey.of("big", "dest-file.jpg")
        permissiveStorage.upload(bigKey, bigBytes, options)

        val restrictiveStorage = LocalImageStorage(tempDir, maxSizeBytes = 4L)
        val destination = Files.createTempFile(tempDir, "oversized-dest-", ".jpg")

        assertFailsWith<ImageStorageException.ValidationException> {
            restrictiveStorage.download(bigKey, destination)
        }
    }

    @Test
    fun `delete removes an existing file`() = runTest {
        storage.upload(key, sampleBytes, options)
        storage.exists(key).shouldBeTrue()

        storage.delete(key)

        storage.exists(key).shouldBeFalse()
    }

    @Test
    fun `delete is idempotent for missing key`() = runTest {
        val missingKey = ImageObjectKey.of("nonexistent", "ghost.jpg")
        // 없는 key 삭제는 예외를 던지면 안 됩니다.
        storage.delete(missingKey)
        storage.exists(missingKey).shouldBeFalse()
    }

    @Test
    fun `exists returns true for existing key`() = runTest {
        storage.upload(key, sampleBytes, options)

        storage.exists(key).shouldBeTrue()
    }

    @Test
    fun `exists returns false for non-existing key`() = runTest {
        val missingKey = ImageObjectKey.of("ghost", "image.jpg")

        storage.exists(missingKey).shouldBeFalse()
    }

    @Test
    fun `list returns uploaded keys under prefix`() = runTest {
        // prefix directory를 walk할 수 있도록 two-level prefix를 가진 file을 upload합니다.
        // key.fullKey = "photos/gallery/img1.jpg"이면 split 결과는 prefix="photos", name="gallery/img1.jpg"입니다.
        // list는 fullKey가 "photos/gallery" directory로 해석되는 ImageObjectKey로 호출됩니다.
        val key1 = ImageObjectKey.of("photos/gallery", "img1.jpg")
        val key2 = ImageObjectKey.of("photos/gallery", "img2.jpg")
        storage.upload(key1, sampleBytes, options)
        storage.upload(key2, sampleBytes, options)

        // fullKey = "photos/gallery"인 prefix key는 img1/img2를 담은 directory로 해석됩니다.
        val listPrefix = ImageObjectKey.of("photos", "gallery")
        val listed = storage.list(listPrefix).toList()

        listed.any { it.fullKey == key1.fullKey }.shouldBeTrue()
        listed.any { it.fullKey == key2.fullKey }.shouldBeTrue()
    }

    @Test
    fun `list returns empty flow when prefix directory does not exist`() = runTest {
        val missingPrefix = ImageObjectKey.of("nonexistent", "prefix")

        val listed = storage.list(missingPrefix).toList()

        listed.isEmpty().shouldBeTrue()
    }

    @Test
    fun `path traversal attempt is rejected by ImageObjectKey of`() {
        // storage에 도달하기 전에 ImageObjectKey.of가 validation하므로 prefix의 ".."는 거부됩니다.
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
