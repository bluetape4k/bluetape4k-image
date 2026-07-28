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
 * local filesystem 기반 [ImageStorage]입니다.
 *
 * ## 동작/계약
 * - 모든 suspend method는 [Dispatchers.IO]로 이동합니다.
 * - path traversal을 방지합니다. 모든 key는 [rootDir] 아래로 resolve되고 resolved path는
 *   normalized [rootDir]로 시작해야 합니다. 그렇지 않으면 [ImageStorageException.ValidationException]을 던집니다.
 * - [maxSizeBytes]보다 큰 upload는 byte를 쓰기 전에 거부합니다.
 * - [maxSizeBytes]보다 큰 object download는 byte를 읽기 전에 거부합니다.
 * - [delete]는 idempotent입니다. missing key는 예외를 일으키지 않습니다.
 * - [list]는 [rootDir] 기준 상대 경로로 resolve된 [ImageObjectKey]의 cold [Flow]를 반환합니다.
 *   cancellation은 underlying directory walk를 중단합니다.
 * - 모든 catch block은 [CancellationException]을 먼저 다시 던집니다. [IOException]은
 *   [ImageStorageException.TransientException]으로, [NoSuchFileException]은
 *   [ImageStorageException.NotFoundException]으로 wrap합니다.
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
     * [key]를 [rootDir] 아래로 resolve하고 resolved path가 root 안에 머무는지 확인합니다.
     *
     * traversal attempt에서는 [ImageStorageException.ValidationException]을 던집니다.
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
            // cancellation을 전파하기 전에 best-effort partial cleanup을 수행합니다.
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
            validateStoredSize(key, path)
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
                validateStoredSize(key, path)
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

    private fun validateStoredSize(key: ImageObjectKey, path: Path) {
        val size = Files.size(path)
        if (size > maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "File exceeds maxSizeBytes ($maxSizeBytes): $size",
            )
        }
    }

    /** partially-written file을 best-effort로 정리합니다. 예외를 일으키지 않습니다. */
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
