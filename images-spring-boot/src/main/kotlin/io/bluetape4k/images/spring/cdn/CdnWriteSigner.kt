package io.bluetape4k.images.spring.cdn

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.UploadOptions
import java.net.URI
import java.time.Duration

/**
 * Signs CDN write (PUT) URLs.
 *
 * ## Behavior
 * - [signPut] produces a presigned URL valid for [expiresIn] that allows a single PUT of the image.
 * - [expiresIn] must be positive and ≤ the backend maximum (S3: 7 days).
 * - [options] controls the content type and other upload metadata embedded in the signature.
 * - Implementations must rethrow [kotlinx.coroutines.CancellationException] before any broad catch.
 * - Separated from [CdnReadSigner] so that CloudFront-only signers (read-only) cannot be mistakenly
 *   used for write operations at compile time.
 *
 * @throws IllegalArgumentException if [expiresIn] is zero or negative
 * @throws io.bluetape4k.images.spring.ImageStorageException if signing fails
 */
interface CdnWriteSigner {
    suspend fun signPut(key: ImageObjectKey, expiresIn: Duration, options: UploadOptions): URI
}
