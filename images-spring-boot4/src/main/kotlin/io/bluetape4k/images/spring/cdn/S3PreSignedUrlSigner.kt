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
 * AWS S3 presigned URL signer that implements both [CdnReadSigner] and [CdnWriteSigner].
 *
 * ## Behavior / Contract
 * - Delegates to [S3Operations.presignGet] and [S3Operations.presignPut] from `bluetape4k-aws-spring-boot`.
 * - The underlying SDK signing path uses RSA and can block; both `signGet` and `signPut` hop to
 *   [Dispatchers.IO].
 * - [expiresIn] must be positive and at most 7 days (AWS S3 SigV4 maximum).
 * - The full object key is `keyPrefix/fullKey`, normalized so there is no double `/`.
 * - [UploadOptions.contentType] is forwarded to the presigned PUT request. The remaining options
 *   ([UploadOptions.cacheControl] and [UploadOptions.metadata]) are accepted for API compatibility
 *   but are not forwarded, because the current [S3Operations.presignPut] surface does not expose
 *   them. Callers that need those headers must sign with the lower-level AWS SDK directly.
 * - All catch blocks rethrow [CancellationException] first. SDK / parsing failures are mapped to
 *   [ImageStorageException.TransientException].
 *
 * @param operations the S3 operations facade.
 * @param bucket the S3 bucket that backs the storage; must be non-blank.
 * @param keyPrefix optional bucket-internal prefix that all keys are placed under.
 */
class S3PreSignedUrlSigner(
    private val operations: S3Operations,
    private val bucket: String,
    private val keyPrefix: String,
) : CdnReadSigner, CdnWriteSigner, Serializable {

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L

        /** AWS SigV4 hard limit for presigned URL expiry. */
        private val MAX_EXPIRY: Duration = Duration.ofDays(7)
    }

    init {
        bucket.requireNotBlank("bucket")
    }

    /** Joins [keyPrefix] with [ImageObjectKey.fullKey], collapsing duplicate separators. */
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
