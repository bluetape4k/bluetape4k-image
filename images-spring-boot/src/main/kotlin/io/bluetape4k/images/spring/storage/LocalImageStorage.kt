package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.ImageUploadResult
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * Local filesystem-backed [ImageStorage].
 *
 * ## Behavior / Contract
 * - All suspend methods hop to [Dispatchers.IO].
 * - Path traversal is prevented: every key is resolved under [rootDir] and the resolved path must
 *   start with the normalized [rootDir]. Otherwise [ImageStorageException.ValidationException] is thrown.
 * - Uploads larger than [maxSizeBytes] are rejected before any bytes are written.
 * - Downloads of objects larger than [maxSizeBytes] are rejected before bytes are read.
 * - [delete] is idempotent — missing keys do not raise.
 * - [list] returns a cold [Flow] of [ImageObjectKey] resolved relative to [rootDir]; cancellation
 *   stops the underlying directory walk.
 * - All catch blocks rethrow [CancellationException] first; [IOException] is wrapped as
 *   [ImageStorageException.TransientException]; [NoSuchFileException] as [ImageStorageException.NotFoundException].
 */
class LocalImageStorage(
    private val rootDir: Path,
    private val maxSizeBytes: Long,
) : ImageStorage, Serializable {

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }

    private val normalizedRoot: Path = rootDir.toAbsolutePath().normalize()

    init {
        require(maxSizeBytes > 0) { "maxSizeBytes must be positive: $maxSizeBytes" }
        Files.createDirectories(normalizedRoot)
    }

    /**
     * Resolves [key] under [rootDir] and asserts the resolved path stays within the root.
     *
     * Throws [ImageStorageException.ValidationException] on traversal attempts.
     */
    private fun resolveKey(key: ImageObjectKey): Path {
        val resolved = normalizedRoot.resolve(key.fullKey).normalize()
        if (!resolved.startsWith(normalizedRoot)) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Path traversal detected for key: ${key.fullKey}",
            )
        }
        return resolved
    }

    override suspend fun upload(
        key: ImageObjectKey,
        bytes: ByteArray,
        options: UploadOptions,
    ): ImageUploadResult = withContext(Dispatchers.IO) {
        if (bytes.size > maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Upload exceeds maxSizeBytes ($maxSizeBytes): ${bytes.size}",
            )
        }
        val target = resolveKey(key)
        try {
            target.parent?.let { Files.createDirectories(it) }
            Files.write(target, bytes)
            ImageUploadResult(
                key = key,
                etag = bytes.size.toString(),
                sizeBytes = bytes.size.toLong(),
                contentType = options.contentType,
                uploadedAt = Instant.now(),
            )
        } catch (e: CancellationException) {
            // best-effort partial cleanup before propagating cancellation
            deletePartialQuietly(target)
            throw e
        } catch (e: IOException) {
            deletePartialQuietly(target)
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    override suspend fun upload(
        key: ImageObjectKey,
        source: Path,
        options: UploadOptions,
    ): ImageUploadResult = withContext(Dispatchers.IO) {
        val size = try {
            Files.size(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchFileException) {
            throw ImageStorageException.NotFoundException(key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
        if (size > maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Upload exceeds maxSizeBytes ($maxSizeBytes): $size",
            )
        }
        val target = resolveKey(key)
        try {
            target.parent?.let { Files.createDirectories(it) }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            ImageUploadResult(
                key = key,
                etag = size.toString(),
                sizeBytes = size,
                contentType = options.contentType,
                uploadedAt = Instant.now(),
            )
        } catch (e: CancellationException) {
            deletePartialQuietly(target)
            throw e
        } catch (e: IOException) {
            deletePartialQuietly(target)
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    override suspend fun download(key: ImageObjectKey): ByteArray = withContext(Dispatchers.IO) {
        val path = resolveKey(key)
        if (!Files.exists(path)) {
            throw ImageStorageException.NotFoundException(key)
        }
        try {
            val size = Files.size(path)
            if (size > maxSizeBytes) {
                throw ImageStorageException.ValidationException(
                    key = key,
                    message = "File exceeds maxSizeBytes ($maxSizeBytes): $size",
                )
            }
            Files.readAllBytes(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchFileException) {
            throw ImageStorageException.NotFoundException(key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    override suspend fun download(key: ImageObjectKey, destination: Path): Unit =
        withContext(Dispatchers.IO) {
            val path = resolveKey(key)
            if (!Files.exists(path)) {
                throw ImageStorageException.NotFoundException(key)
            }
            try {
                destination.parent?.let { Files.createDirectories(it) }
                Files.newInputStream(path).use { input ->
                    Files.newOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSuchFileException) {
                throw ImageStorageException.NotFoundException(key, cause = e)
            } catch (e: IOException) {
                throw ImageStorageException.TransientException(key = key, cause = e)
            }
        }

    override suspend fun delete(key: ImageObjectKey): Unit = withContext(Dispatchers.IO) {
        try {
            Files.deleteIfExists(resolveKey(key))
            Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    override suspend fun exists(key: ImageObjectKey): Boolean = withContext(Dispatchers.IO) {
        try {
            Files.exists(resolveKey(key))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    override fun list(prefix: ImageObjectKey): Flow<ImageObjectKey> = flow {
        val prefixPath = try {
            resolveKey(prefix)
        } catch (e: ImageStorageException.ValidationException) {
            throw e
        }
        if (!Files.exists(prefixPath)) {
            return@flow
        }
        try {
            Files.walk(prefixPath).use { stream ->
                val iterator = stream.filter { Files.isRegularFile(it) }.iterator()
                while (iterator.hasNext()) {
                    val file = iterator.next()
                    val relative = normalizedRoot.relativize(file.toAbsolutePath().normalize())
                    val relativePath = relative.toString().replace('\\', '/')
                    val parts = relativePath.split("/", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        emit(ImageObjectKey.of(parts[0], parts[1]))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = prefix, cause = e)
        }
    }.flowOn(Dispatchers.IO)

    /** Best-effort cleanup of a partially-written file. Never raises. */
    private fun deletePartialQuietly(target: Path) {
        try {
            Files.deleteIfExists(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            log.warn(e) { "Failed to delete partial upload: $target" }
        }
    }
}
