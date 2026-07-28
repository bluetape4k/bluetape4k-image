package io.bluetape4k.images.spring.cdn

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.UploadOptions
import java.net.URI
import java.time.Duration

/**
 * CDN write(PUT) URL에 서명합니다.
 *
 * ## 동작
 * - [signPut]은 image의 단일 PUT을 허용하고 [expiresIn] 동안 유효한 presigned URL을 생성합니다.
 * - [expiresIn]은 양수여야 하며 backend maximum(S3: 7 days)을 넘을 수 없습니다.
 * - [options]는 signature에 포함되는 content type과 기타 upload metadata를 제어합니다.
 * - 구현체는 broad catch보다 먼저 [kotlinx.coroutines.CancellationException]을 다시 던져야 합니다.
 * - CloudFront-only signer(read-only)가 write operation에 잘못 사용되지 않도록 [CdnReadSigner]와 분리되어 있습니다.
 *
 * @throws IllegalArgumentException [expiresIn]이 zero 또는 negative이면 던집니다.
 * @throws io.bluetape4k.images.spring.ImageStorageException signing이 실패하면 던집니다.
 */
interface CdnWriteSigner {
    suspend fun signPut(key: ImageObjectKey, expiresIn: Duration, options: UploadOptions): URI
}
