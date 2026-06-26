package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.ImageUploadResult
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * S3-backed [ImageStorage] implementation.
 *
 * ## Behavior / Contract
 * - Delegates all S3 calls to [S3Operations] from `bluetape4k-aws-spring-boot`.
 * - All suspend methods hop to [Dispatchers.IO].
 * - All catch blocks rethrow [CancellationException] first; SDK exceptions are mapped to
 *   [ImageStorageException] subclasses via the file-local `toImageStorageException` extension.
 * - [bucket] resolves once at construction from [ImageStorageProperties.bucket]; absence or blank
 *   value raises [IllegalArgumentException].
 * - Upload is rejected (with [ImageStorageException.ValidationException]) when the payload size
 *   exceeds [ImageStorageProperties.maxSizeBytes]. Download performs a list-based size pre-check.
 * - SDK timeout/retry knobs from [ImageStorageProperties.S3] are intended to be applied at the
 *   `S3Client` construction site (see [toClientOverrideConfig]); the current [S3Operations] API does
 *   not expose a per-request override hook, so this class does not pass override config at call time.
 * - [UploadOptions.cacheControl] and [UploadOptions.metadata] are accepted for API compatibility but
 *   are not forwarded — the underlying [S3Operations.upload] does not expose them. Use the
 *   lower-level AWS S3 client directly when these headers are required.
 */
class S3ImageStorage(
    private val operations: S3Operations,
    private val properties: ImageStorageProperties,
) : ImageStorage, Serializable {

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L

        private const val STATUS_UNAUTHORIZED: Int = 401
        private const val STATUS_FORBIDDEN: Int = 403
        private const val STATUS_NOT_FOUND: Int = 404
        private const val STATUS_CONFLICT: Int = 409
    }

    private val bucket: String = run {
        val configured = properties.bucket
        requireNotNull(configured) { "bluetape4k.images.storage.bucket is required for S3 backend" }
        configured.requireNotBlank("bucket")
        configured
    }

    init {
        require(properties.maxSizeBytes > 0) {
            "bluetape4k.images.storage.maxSizeBytes must be positive: ${properties.maxSizeBytes}"
        }
    }

    /** Joins the optional [ImageStorageProperties.keyPrefix] with the key's [ImageObjectKey.fullKey]. */
    private fun objectKey(key: ImageObjectKey): String {
        val prefix = properties.keyPrefix
        if (prefix.isBlank()) return key.fullKey
        return "${prefix.trimEnd('/')}/${key.fullKey}"
    }

    override suspend fun upload(
        key: ImageObjectKey,
        bytes: ByteArray,
        options: UploadOptions,
    ): ImageUploadResult = withContext(Dispatchers.IO) {
        if (bytes.size > properties.maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Upload exceeds maxSizeBytes (${properties.maxSizeBytes}): ${bytes.size}",
            )
        }
        try {
            val response = operations.upload(
                bucket = bucket,
                key = objectKey(key),
                bytes = bytes,
                contentType = options.contentType,
            )
            ImageUploadResult(
                key = key,
                etag = response.eTag().orEmpty(),
                sizeBytes = bytes.size.toLong(),
                contentType = options.contentType,
                uploadedAt = Instant.now(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toImageStorageException(key)
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
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
        if (size > properties.maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Upload file exceeds maxSizeBytes (${properties.maxSizeBytes}): $size",
            )
        }
        // S3Operations.upload only accepts ByteArray. For very large files, callers should bypass
        // this storage and use the lower-level AWS S3 transfer manager.
        val bytes = try {
            Files.readAllBytes(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
        try {
            val response = operations.upload(
                bucket = bucket,
                key = objectKey(key),
                bytes = bytes,
                contentType = options.contentType,
            )
            ImageUploadResult(
                key = key,
                etag = response.eTag().orEmpty(),
                sizeBytes = size,
                contentType = options.contentType,
                uploadedAt = Instant.now(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toImageStorageException(key)
        }
    }

    override suspend fun download(key: ImageObjectKey): ByteArray = withContext(Dispatchers.IO) {
        // Best-effort size pre-check via listPage(prefix=fullKey, maxKeys=1) — S3Operations exposes
        // no HEAD API. If the object is absent at list time, downloadBytes will surface the real error.
        val preCheckedSize = headSizeOrNull(key)
        if (preCheckedSize != null && preCheckedSize > properties.maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Object exceeds maxSizeBytes (${properties.maxSizeBytes}): $preCheckedSize",
            )
        }
        try {
            operations.downloadBytes(bucket = bucket, key = objectKey(key))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toImageStorageException(key)
        }
    }

    override suspend fun download(key: ImageObjectKey, destination: Path): Unit =
        withContext(Dispatchers.IO) {
            val bytes = download(key)
            try {
                destination.parent?.let { Files.createDirectories(it) }
                Files.write(destination, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw ImageStorageException.TransientException(key = key, cause = e)
            }
        }

    override suspend fun delete(key: ImageObjectKey): Unit = withContext(Dispatchers.IO) {
        try {
            operations.delete(bucket = bucket, key = objectKey(key))
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchKeyException) {
            // idempotent — missing key is not an error
            log.debug(e) { "delete: ${key.fullKey} not found" }
        } catch (e: S3Exception) {
            if (e.statusCode() == STATUS_NOT_FOUND) {
                log.debug(e) { "delete: ${key.fullKey} not found" }
            } else {
                throw e.toImageStorageException(key)
            }
        } catch (e: Throwable) {
            throw e.toImageStorageException(key)
        }
    }

    override suspend fun exists(key: ImageObjectKey): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullKey = objectKey(key)
            val page = operations.listPage(bucket = bucket, prefix = fullKey, maxKeys = 1)
            page.objects.any { it.key() == fullKey }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toImageStorageException(key)
        }
    }

    override fun list(prefix: ImageObjectKey): Flow<ImageObjectKey> = flow {
        val joinedPrefix = objectKey(prefix)
        val keyPrefixValue = properties.keyPrefix
        try {
            operations.listFlow(bucket = bucket, prefix = joinedPrefix).collect { obj ->
                val rawKey = obj.key()
                if (rawKey == null) {
                    return@collect
                }
                val stripped = if (keyPrefixValue.isBlank()) {
                    rawKey
                } else {
                    rawKey.removePrefix("${keyPrefixValue.trimEnd('/')}/")
                }
                val parts = stripped.split("/", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    emit(ImageObjectKey.of(parts[0], parts[1]))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toImageStorageException(prefix)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Best-effort lookup of the object size at [key] using `listPage`. Returns `null` when the size
     * cannot be determined; this method never throws (except for [CancellationException]) — the
     * downstream `downloadBytes` will surface the real error if the object is truly missing.
     */
    private suspend fun headSizeOrNull(key: ImageObjectKey): Long? {
        val fullKey = objectKey(key)
        return try {
            val page = operations.listPage(bucket = bucket, prefix = fullKey, maxKeys = 1)
            page.objects.firstOrNull { it.key() == fullKey }?.size()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log.debug(e) { "Size pre-check failed for ${key.fullKey}; deferring to download" }
            null
        }
    }

    /**
     * Maps SDK [Throwable]s to [ImageStorageException] subclasses. Keeps SDK types confined to this
     * file so [ImageStorageException] itself stays SDK-free.
     *
     * SDK error messages may contain the object key; we sanitize by exposing only [ImageObjectKey.fullKey]
     * in the wrapped message and routing the original throwable to [Throwable.cause].
     */
    private fun Throwable.toImageStorageException(key: ImageObjectKey): ImageStorageException =
        when (this) {
            is ImageStorageException -> this
            is NoSuchKeyException    -> ImageStorageException.NotFoundException(key, cause = this)
            is NoSuchBucketException -> ImageStorageException.AccessDeniedException(key, cause = this)
            is S3Exception           -> when (statusCode()) {
                STATUS_UNAUTHORIZED, STATUS_FORBIDDEN ->
                    ImageStorageException.AccessDeniedException(key, cause = this)
                STATUS_NOT_FOUND                      ->
                    ImageStorageException.NotFoundException(key, cause = this)
                STATUS_CONFLICT                       ->
                    ImageStorageException.ConflictException(key, cause = this)
                else                                  ->
                    ImageStorageException.TransientException(key = key, cause = this)
            }
            else                     -> ImageStorageException.TransientException(key = key, cause = this)
        }
}
