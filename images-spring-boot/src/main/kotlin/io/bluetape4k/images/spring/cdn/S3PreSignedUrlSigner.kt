package io.bluetape4k.images.spring.cdn

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration

/**
 * [CdnReadSigner]와 [CdnWriteSigner]를 모두 구현하는 AWS S3 presigned URL signer입니다.
 *
 * ## 동작 / 계약
 * - `bluetape4k-aws-spring-boot`의 [S3Operations.presignGet]과 [S3Operations.presignPut]에 위임합니다.
 * - 내부 SDK signing path는 RSA를 사용하며 block될 수 있으므로 `signGet`과 `signPut`은 모두 [Dispatchers.IO]에서 실행합니다.
 * - [expiresIn]은 양수여야 하며 AWS S3 SigV4 최대값인 7일을 넘을 수 없습니다.
 * - 전체 object key는 `keyPrefix/fullKey`이며, 중복 `/`가 생기지 않도록 정규화합니다.
 * - [UploadOptions.contentType]은 presigned PUT request에 전달합니다. 나머지 option인
 *   [UploadOptions.cacheControl]과 [UploadOptions.metadata]는 API 호환성을 위해 받지만 전달하지 않습니다. 현재
 *   [S3Operations.presignPut] surface가 해당 값을 노출하지 않기 때문입니다. 이 header가 필요한 caller는
 *   lower-level AWS SDK로 직접 signing해야 합니다.
 * - 모든 catch block은 [CancellationException]을 먼저 다시 던집니다. SDK / parsing failure는
 *   [ImageStorageException.TransientException]으로 매핑합니다.
 *
 * @param operations S3 operation facade입니다.
 * @param bucket storage를 backing하는 S3 bucket입니다. blank일 수 없습니다.
 * @param keyPrefix 모든 key가 놓이는 bucket 내부 prefix입니다. 비어 있으면 prefix를 붙이지 않습니다.
 */
class S3PreSignedUrlSigner(
    private val operations: S3Operations,
    private val bucket: String,
    private val keyPrefix: String,
) : CdnReadSigner, CdnWriteSigner, Serializable {

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L

        /** presigned URL expiry에 대한 AWS SigV4 hard limit입니다. */
        private val MAX_EXPIRY: Duration = Duration.ofDays(7)
    }

    init {
        bucket.requireNotBlank("bucket")
    }

    /** [keyPrefix]와 [ImageObjectKey.fullKey]를 결합하고 중복 separator를 접습니다. */
    private fun objectKey(key: ImageObjectKey): String {
        if (keyPrefix.isBlank()) return key.fullKey
        return "${keyPrefix.trimEnd('/')}/${key.fullKey}"
    }

    override suspend fun signGet(key: ImageObjectKey, expiresIn: Duration): URI {
        validateExpiry(expiresIn)
        return withContext(Dispatchers.IO) {
            try {
                val url = operations.presignGet(
                    bucket = bucket,
                    key = objectKey(key),
                    duration = expiresIn,
                )
                try {
                    url.toURI()
                } catch (e: URISyntaxException) {
                    throw ImageStorageException.TransientException(
                        key = key,
                        message = "Failed to convert presigned GET URL to URI: ${key.fullKey}",
                        cause = e,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageStorageException) {
                throw e
            } catch (e: Throwable) {
                throw ImageStorageException.TransientException(
                    key = key,
                    message = "Failed to presign GET URL: ${key.fullKey}",
                    cause = e,
                )
            }
        }
    }

    override suspend fun signPut(
        key: ImageObjectKey,
        expiresIn: Duration,
        options: UploadOptions,
    ): URI {
        validateExpiry(expiresIn)
        return withContext(Dispatchers.IO) {
            try {
                val url = operations.presignPut(
                    bucket = bucket,
                    key = objectKey(key),
                    duration = expiresIn,
                    contentType = options.contentType,
                )
                try {
                    url.toURI()
                } catch (e: URISyntaxException) {
                    throw ImageStorageException.TransientException(
                        key = key,
                        message = "Failed to convert presigned PUT URL to URI: ${key.fullKey}",
                        cause = e,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageStorageException) {
                throw e
            } catch (e: Throwable) {
                throw ImageStorageException.TransientException(
                    key = key,
                    message = "Failed to presign PUT URL: ${key.fullKey}",
                    cause = e,
                )
            }
        }
    }

    private fun validateExpiry(expiresIn: Duration) {
        require(!expiresIn.isZero && !expiresIn.isNegative) {
            "expiresIn must be positive: $expiresIn"
        }
        require(expiresIn <= MAX_EXPIRY) {
            "expiresIn must be <= 7 days (S3 SigV4 limit): $expiresIn"
        }
    }
}
