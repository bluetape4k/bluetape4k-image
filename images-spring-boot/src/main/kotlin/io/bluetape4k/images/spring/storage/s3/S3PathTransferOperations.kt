package io.bluetape4k.images.spring.storage.s3

import java.nio.file.Path

/**
 * S3 object를 파일에서 업로드하는 선택적 path capability입니다.
 *
 * 이 계약은 AWS Transfer Manager API를 노출하지 않으므로 일반적인 S3 byte/object 작업이
 * transfer module의 classpath 유무에 영향을 받지 않습니다. Path upload를 제공할 수 없는
 * 환경에서는 [S3ImageStorage]가 [io.bluetape4k.images.spring.ImageStorageException.TransientException]으로
 * fail closed합니다.
 */
interface S3PathTransferOperations {

    /** [source] 파일을 [bucket]/[key] object로 업로드하고 opaque ETag를 반환합니다. */
    suspend fun uploadFile(
        bucket: String,
        key: String,
        source: Path,
        contentType: String?,
    ): String?
}
