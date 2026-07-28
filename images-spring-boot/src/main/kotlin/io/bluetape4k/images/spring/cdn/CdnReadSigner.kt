package io.bluetape4k.images.spring.cdn

import io.bluetape4k.images.spring.ImageObjectKey
import java.net.URI
import java.time.Duration

/**
 * CDN read(GET) URL에 서명합니다.
 *
 * ## 동작
 * - [signGet]은 [expiresIn] 동안 유효한 presigned URL을 생성합니다.
 * - [expiresIn]은 양수여야 하며 backend maximum(S3: 7 days)을 넘을 수 없습니다.
 * - 구현체는 broad catch보다 먼저 [kotlinx.coroutines.CancellationException]을 다시 던져야 합니다.
 *
 * @throws IllegalArgumentException [expiresIn]이 zero 또는 negative이면 던집니다.
 * @throws io.bluetape4k.images.spring.ImageStorageException signing이 실패하면 던집니다.
 */
interface CdnReadSigner {
    suspend fun signGet(key: ImageObjectKey, expiresIn: Duration): URI
}
