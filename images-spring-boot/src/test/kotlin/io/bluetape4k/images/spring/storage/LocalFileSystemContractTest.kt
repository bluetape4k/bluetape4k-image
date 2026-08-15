package io.bluetape4k.images.spring.storage

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.UploadOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.UUID
import kotlin.io.path.deleteIfExists

class LocalFileSystemContractTest {

    @TempDir
    lateinit var tempDir: Path

    private val options = UploadOptions()
    private val sampleBytes = "new-image".toByteArray()

    @Test
    fun `default provider probe reports descriptor and replace capabilities`() {
        val capabilities = LocalFileSystemCapabilityProbe.inspect(tempDir)

        println(
            "Local filesystem capability: provider=${capabilities.providerScheme}, " +
                "stream=${capabilities.directoryStreamType.substringAfterLast('.')}, " +
                "secure=${capabilities.supportsSecureDirectoryStream}, " +
                "atomicReplace=${capabilities.supportsAtomicExistingTargetReplace}, " +
                "posix=${capabilities.supportsPosixAttributes}",
        )
        capabilities.providerScheme.isNotBlank().shouldBeTrue()
        capabilities.directoryStreamType.isNotBlank().shouldBeTrue()
        if (capabilities.supportsSecureDirectoryStream) {
            capabilities.supportsAtomicExistingTargetReplace.shouldBeTrue()
        } else {
            capabilities.supportsAtomicExistingTargetReplace.shouldBeFalse()
        }
    }

    @Test
    fun `supported provider matrix covers root nested missing existing and overwrite`() = runTest {
        val capabilities = LocalFileSystemCapabilityProbe.inspect(tempDir)
        assumeTrue(
            capabilities.supportsSecureDirectoryStream && capabilities.supportsAtomicExistingTargetReplace,
            capabilities.unsupportedReason,
        )

        val storage = LocalImageStorage(tempDir, maxSizeBytes = 1024)
        val rootKey = ImageObjectKey.of("uploads", "root.jpg")
        val nestedKey = ImageObjectKey.of("nested/gallery", "photo.jpg")
        val missingParentKey = ImageObjectKey.of("not-provisioned/path", "photo.jpg")
        Files.createDirectories(tempDir.resolve(rootKey.prefix))
        Files.createDirectories(tempDir.resolve(nestedKey.prefix))

        storage.upload(rootKey, "old-image".toByteArray(), options)
        storage.upload(rootKey, sampleBytes, options)
        storage.upload(nestedKey, sampleBytes, options)

        storage.download(rootKey).contentEquals(sampleBytes).shouldBeTrue()
        storage.download(nestedKey).contentEquals(sampleBytes).shouldBeTrue()

        val missingParent = assertFailsWith<ImageStorageException.ValidationException> {
            storage.upload(missingParentKey, sampleBytes, options)
        }
        missingParent.message.orEmpty().contains("must be provisioned").shouldBeTrue()
        Files.exists(tempDir.resolve(missingParentKey.fullKey)).shouldBeFalse()
        temporaryUploadFiles(tempDir).isEmpty().shouldBeTrue()
    }

    @Test
    fun `failed source upload preserves existing target and removes staging`() = runTest {
        val capabilities = LocalFileSystemCapabilityProbe.inspect(tempDir)
        assumeTrue(
            capabilities.supportsSecureDirectoryStream && capabilities.supportsAtomicExistingTargetReplace,
            capabilities.unsupportedReason,
        )

        val storage = LocalImageStorage(tempDir, maxSizeBytes = 1_000_000)
        val key = ImageObjectKey.of("uploads", "failed-source.jpg")
        Files.createDirectories(tempDir.resolve(key.prefix))
        val original = "preserved-image".toByteArray()
        storage.upload(key, original, options)

        val sourceDirectory = Files.createDirectory(tempDir.resolve("source-${UUID.randomUUID()}"))
        assertFailsWith<ImageStorageException.TransientException> {
            storage.upload(key, sourceDirectory, options)
        }

        storage.download(key).contentEquals(original).shouldBeTrue()
        temporaryUploadFiles(tempDir).isEmpty().shouldBeTrue()
    }

    @Test
    fun `parent replacement fails closed without following symbolic links`() = runTest {
        val capabilities = LocalFileSystemCapabilityProbe.inspect(tempDir)
        assumeTrue(capabilities.supportsSecureDirectoryStream, capabilities.unsupportedReason)

        val storage = LocalImageStorage(tempDir, maxSizeBytes = 1024)
        val parent = tempDir.resolve("uploads")
        val key = ImageObjectKey.of("uploads", "replaced.jpg")
        Files.createDirectories(parent)

        val outside = Files.createTempDirectory("local-storage-contract-outside")
        val movedParent = tempDir.resolve("uploads-moved")
        Files.move(parent, movedParent)
        Files.createSymbolicLink(parent, outside)
        try {
            assertFailsWith<ImageStorageException.ValidationException> {
                storage.upload(key, sampleBytes, options)
            }
            Files.exists(outside.resolve("replaced.jpg")).shouldBeFalse()
        } finally {
            Files.deleteIfExists(parent)
            Files.move(movedParent, parent)
            deleteRecursively(outside)
        }
    }

    @Test
    fun `root replacement fails closed without writing through the replacement`() = runTest {
        val capabilities = LocalFileSystemCapabilityProbe.inspect(tempDir)
        assumeTrue(capabilities.supportsSecureDirectoryStream, capabilities.unsupportedReason)

        val anchoredRoot = tempDir.resolve("anchored-root")
        val outside = tempDir.resolve("outside-root")
        Files.createDirectories(anchoredRoot)
        Files.createDirectories(outside)
        val storage = LocalImageStorage(anchoredRoot, maxSizeBytes = 1024)
        val movedRoot = tempDir.resolve("anchored-root-moved")
        Files.move(anchoredRoot, movedRoot)
        Files.createSymbolicLink(anchoredRoot, outside)
        try {
            assertFailsWith<ImageStorageException.ValidationException> {
                storage.upload(ImageObjectKey.of("uploads", "escaped.jpg"), sampleBytes, options)
            }
            Files.exists(outside.resolve("uploads").resolve("escaped.jpg")).shouldBeFalse()
        } finally {
            Files.deleteIfExists(anchoredRoot)
            Files.move(movedRoot, anchoredRoot)
        }
    }

    @Test
    fun `posix permission matrix is explicit when the provider enforces it`() = runTest {
        val capabilities = LocalFileSystemCapabilityProbe.inspect(tempDir)
        assumeTrue(capabilities.supportsPosixAttributes, capabilities.unsupportedReason)

        val parent = tempDir.resolve("restricted")
        Files.createDirectories(parent)
        val originalPermissions = Files.getPosixFilePermissions(parent)
        try {
            val readOnly = originalPermissions - setOf(
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.OTHERS_WRITE,
            )
            Files.setPosixFilePermissions(parent, readOnly)
            assumeTrue(!Files.isWritable(parent), "N/A: provider/process still reports the directory writable")

            val storage = LocalImageStorage(tempDir, maxSizeBytes = 1024)
            val error = assertFailsWith<ImageStorageException.AccessDeniedException> {
                storage.upload(ImageObjectKey.of("restricted", "denied.jpg"), sampleBytes, options)
            }
            error.key shouldBeEqualTo ImageObjectKey.of("restricted", "denied.jpg")
            Files.exists(parent.resolve("denied.jpg")).shouldBeFalse()
        } finally {
            Files.setPosixFilePermissions(parent, originalPermissions)
        }
    }

    @Test
    fun `list cancellation preserves the cancellation contract`() = runTest {
        val prefix = ImageObjectKey.of("cancellation", "images")
        val key = ImageObjectKey.of("cancellation/images", "first.jpg")
        Files.createDirectories(tempDir.resolve(key.prefix))
        val storage = LocalImageStorage(tempDir, maxSizeBytes = 1024)
        storage.upload(key, sampleBytes, options)
        val cancellation = CancellationException("contract collector stopped")

        val thrown = assertFailsWith<CancellationException> {
            storage.list(prefix).collect { throw cancellation }
        }

        thrown::class shouldBeEqualTo cancellation::class
        thrown.message shouldBeEqualTo cancellation.message
    }

    @Test
    fun `zipfs capability is unsupported and upload fails closed without side effects`() = runTest {
        val zipPath = Files.createTempFile(tempDir, "storage-", ".zip")
        zipPath.deleteIfExists()
        val uri = URI.create("jar:${zipPath.toUri()}")

        FileSystems.newFileSystem(uri, mapOf("create" to "true")).use { fileSystem ->
            val root = fileSystem.getPath("/")
            val capabilities = LocalFileSystemCapabilityProbe.inspect(root)
            capabilities.providerScheme shouldBeEqualTo fileSystem.provider().scheme
            capabilities.supportsSecureDirectoryStream.shouldBeFalse()
            capabilities.supportsAtomicExistingTargetReplace.shouldBeFalse()

            val storage = LocalImageStorage(root, maxSizeBytes = 1024)
            val prefix = "zipfs-${UUID.randomUUID()}"
            val key = ImageObjectKey.of(prefix, "zipfs.jpg")
            Files.createDirectories(root.resolve(key.prefix))
            val failure = assertFailsWith<ImageStorageException.ValidationException> {
                storage.upload(key, sampleBytes, options)
            }
            failure.key shouldBeEqualTo key
            Files.exists(root.resolve(key.fullKey)).shouldBeFalse()
        }
    }

    private fun temporaryUploadFiles(root: Path): List<Path> =
        Files.walk(root).use { paths ->
            paths.filter { path ->
                path.fileName?.toString()?.startsWith(".") == true &&
                    path.fileName.toString().endsWith(".upload")
            }.toList()
        }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { candidate ->
                try {
                    Files.deleteIfExists(candidate)
                } catch (_: IOException) {
                    // Best-effort fixture cleanup; assertions cover escaped writes.
                }
            }
        }
    }
}
