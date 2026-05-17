package io.bluetape4k.images.spring.cdn

import io.bluetape4k.images.spring.ImageObjectKey
import java.net.URI
import java.time.Duration

/**
 * Signs CDN read (GET) URLs.
 *
 * ## Behavior
 * - [signGet] produces a presigned URL valid for [expiresIn].
 * - [expiresIn] must be positive and ≤ the backend maximum (S3: 7 days).
 * - Implementations must rethrow [kotlinx.coroutines.CancellationException] before any broad catch.
 *
 * @throws IllegalArgumentException if [expiresIn] is zero or negative
 * @throws io.bluetape4k.images.spring.ImageStorageException if signing fails
 */
interface CdnReadSigner {
    suspend fun signGet(key: ImageObjectKey, expiresIn: Duration): URI
}
