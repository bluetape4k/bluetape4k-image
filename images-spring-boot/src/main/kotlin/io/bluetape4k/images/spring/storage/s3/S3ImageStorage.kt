package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.aws.spring.s3.S3ObjectMetadata as AwsS3ObjectMetadata
import io.bluetape4k.aws.spring.s3.S3TransferOperations
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageObjectMetadata
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.ImageUploadResult
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.bluetape4k.images.spring.storage.ImageObjectMetadataReader
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException as NioAccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.jvm.JvmOverloads

/**
 * S3를 backend로 사용하는 [ImageStorage] 구현체입니다.
 *
 * ## 동작 / 계약
 * - byte/object 작업은 `bluetape4k-aws-spring-boot`의 [S3Operations]에 위임하고,
 *   [Path] 작업은 [S3TransferOperations] 또는 S3 resource stream을 사용합니다.
 * - 모든 suspend method는 blocking SDK 경로를 격리하기 위해 [Dispatchers.IO]에서 실행합니다.
 * - 모든 catch block은 [CancellationException]을 먼저 다시 던집니다. SDK exception은 file-local
 *   `toImageStorageException` extension을 통해 [ImageStorageException] 하위 type으로 매핑합니다.
 * - [bucket]은 생성 시 [ImageStorageProperties.bucket]에서 한 번만 해석합니다. 값이 없거나 blank이면
 *   [IllegalArgumentException]을 던집니다.
 * - payload size가 [ImageStorageProperties.maxSizeBytes]를 초과하면 upload는
 *   [ImageStorageException.ValidationException]으로 거부됩니다. download는 단일 `headObject` snapshot으로
 *   시작 전에 object size를 확인하고, body를 반환하거나 destination file을 교체하기 전에 limit과 snapshot의
 *   실제 byte count를 다시 검사합니다. 두 값이 다르면 object 교체 경합으로 보고 fail closed합니다.
 * - [Path] upload는 source를 bounded streaming 임시 snapshot으로 고정한 뒤 [S3TransferOperations]의 file
 *   transfer를 사용합니다. transfer bean이 없으면 source를 `ByteArray`로 적재하지 않고
 *   [ImageStorageException.TransientException]으로 fail closed합니다.
 * - [Path] download는 S3 resource input stream을 destination 임시 파일로 복사한 뒤 atomic replace합니다.
 *   resource property를 metadata source로 사용하거나 `listPage`로 fallback하지 않습니다.
 * - [ImageStorageProperties.S3]의 SDK timeout/retry knob은 `S3Client` 생성 지점에서 적용되어야 합니다
 *   ([toClientOverrideConfig] 참고). 현재 [S3Operations] API는 per-request override hook을 노출하지 않으므로,
 *   이 class는 호출 시점에 override config를 전달하지 않습니다.
 * - [UploadOptions.cacheControl]과 [UploadOptions.metadata]는 API 호환성을 위해 받지만 전달하지 않습니다.
 *   내부 [S3Operations.upload]가 해당 값을 노출하지 않기 때문입니다. 이 header가 필요하면 lower-level AWS S3
 *   client를 직접 사용해야 합니다.
 */
class S3ImageStorage @JvmOverloads constructor(
    private val operations: S3Operations,
    private val properties: ImageStorageProperties,
    private val transferOperations: S3TransferOperations? = null,
) : ImageStorage, ImageObjectMetadataReader {

    companion object : KLogging() {
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

    /** 선택 사항인 [ImageStorageProperties.keyPrefix]와 key의 [ImageObjectKey.fullKey]를 하나의 S3 object key로 결합합니다. */
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
        } catch (e: NoSuchFileException) {
            throw ImageStorageException.NotFoundException(key, cause = e)
        } catch (e: NioAccessDeniedException) {
            throw ImageStorageException.AccessDeniedException(key = key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
        if (size > properties.maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Upload file exceeds maxSizeBytes (${properties.maxSizeBytes}): $size",
            )
        }
        val transfer = transferOperations ?: throw ImageStorageException.TransientException(
            key = key,
            message = "S3 path upload requires S3TransferOperations",
        )
        var staged: Path? = null
        try {
            val parent = source.toAbsolutePath().normalize().parent ?: Path.of(".").toAbsolutePath()
            staged = Files.createTempFile(parent, ".${source.fileName}.", ".s3-upload")
            val stagedSize = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input ->
                Files.newOutputStream(staged).use { output ->
                    copyWithLimit(input, output, key)
                }
            }
            forceFile(staged)
            val response = transfer.uploadFile(
                bucket = bucket,
                key = objectKey(key),
                source = staged,
            ) {
                putObjectRequest(
                    PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey(key))
                        .contentType(options.contentType)
                        .build(),
                )
            }.response()
            ImageUploadResult(
                key = key,
                etag = response.eTag().orEmpty(),
                sizeBytes = stagedSize,
                contentType = options.contentType,
                uploadedAt = Instant.now(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toImageStorageException(key)
        } finally {
            staged?.let(::deletePartialQuietly)
        }
    }

    override suspend fun download(key: ImageObjectKey): ByteArray = withContext(Dispatchers.IO) {
        val metadata = headObject(key)
        validateDownloadSize(key, metadata.sizeBytes)
        try {
            val bytes = operations.downloadBytes(bucket = bucket, key = objectKey(key))
            validateDownloadSize(key, bytes.size.toLong())
            validateSnapshotSize(key, metadata.sizeBytes, bytes.size.toLong())
            bytes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toImageStorageException(key)
        }
    }

    override suspend fun readMetadata(key: ImageObjectKey): ImageObjectMetadata = withContext(Dispatchers.IO) {
        headObject(key).toImageMetadata(key)
    }

    override suspend fun download(key: ImageObjectKey, destination: Path): Unit =
        withContext(Dispatchers.IO) {
            val metadata = headObject(key)
            validateDownloadSize(key, metadata.sizeBytes)
            val resource = try {
                operations.resource(bucket = bucket, key = objectKey(key))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw e.toImageStorageException(key)
            }
            val destinationParent = destination.toAbsolutePath().parent ?: Path.of(".").toAbsolutePath()
            var staged: Path? = null
            try {
                Files.createDirectories(destinationParent)
                staged = Files.createTempFile(destinationParent, ".${destination.fileName}.", ".download")
                val actualSize = resource.getInputStream().use { input ->
                    Files.newOutputStream(staged).use { output ->
                        copyWithLimit(input, output, key)
                    }
                }
                validateSnapshotSize(key, metadata.sizeBytes, actualSize)
                Files.move(
                    staged,
                    destination.toAbsolutePath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (e: CancellationException) {
                staged?.let(::deletePartialQuietly)
                throw e
            } catch (e: Throwable) {
                staged?.let(::deletePartialQuietly)
                throw e.toImageStorageException(key)
            }
        }

    override suspend fun delete(key: ImageObjectKey): Unit = withContext(Dispatchers.IO) {
        try {
            operations.delete(bucket = bucket, key = objectKey(key))
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchKeyException) {
            // idempotent: 없는 key 삭제는 오류가 아닙니다.
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

    private suspend fun headObject(key: ImageObjectKey): AwsS3ObjectMetadata =
        try {
            operations.headObject(bucket = bucket, key = objectKey(key))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log.debug(e) { "Metadata HEAD failed for ${key.fullKey}; failing closed" }
            throw e.toImageStorageException(key)
        }

    private fun AwsS3ObjectMetadata.toImageMetadata(key: ImageObjectKey): ImageObjectMetadata =
        ImageObjectMetadata(
            key = key,
            sizeBytes = sizeBytes,
            etag = etag,
            contentType = contentType,
            lastModified = lastModified,
        )

    private fun validateSnapshotSize(key: ImageObjectKey, expected: Long, actual: Long) {
        if (actual != expected) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Object size changed during download: expected $expected but received $actual",
            )
        }
    }

    /** S3 byte-array download 전후에 [ImageStorageProperties.maxSizeBytes] 제한을 적용합니다. */
    private fun validateDownloadSize(key: ImageObjectKey, size: Long) {
        if (size > properties.maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Object exceeds maxSizeBytes (${properties.maxSizeBytes}): $size",
            )
        }
    }

    private fun copyWithLimit(input: java.io.InputStream, output: java.io.OutputStream, key: ImageObjectKey): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            copied += count
            validateDownloadSize(key, copied)
            output.write(buffer, 0, count)
        }
        return copied
    }

    private fun forceFile(path: Path) {
        FileChannel.open(path, StandardOpenOption.WRITE).use { channel ->
            channel.force(true)
        }
    }

    private fun deletePartialQuietly(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            log.debug(e) { "Failed to delete partial download: $path" }
        }
    }

    /**
     * SDK [Throwable]을 [ImageStorageException] 하위 type으로 변환합니다. SDK type을 이 file 안에 가둬
     * [ImageStorageException] 자체가 SDK-free contract로 남도록 합니다.
     *
     * SDK error message에는 object key가 포함될 수 있습니다. wrapping message에는 [ImageObjectKey.fullKey]만
     * 노출하고 원본 throwable은 [Throwable.cause]로 연결해 외부 노출면을 제한합니다.
     */
    private fun Throwable.toImageStorageException(key: ImageObjectKey): ImageStorageException =
        when (this) {
            is ImageStorageException -> this
            is NioAccessDeniedException -> ImageStorageException.AccessDeniedException(key, cause = this)
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
