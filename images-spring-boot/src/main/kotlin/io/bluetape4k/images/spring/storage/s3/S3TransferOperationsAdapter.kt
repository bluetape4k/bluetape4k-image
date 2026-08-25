package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.aws.spring.s3.S3TransferOperations
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.file.Path

/**
 * AWS [S3TransferOperations]를 이미지 모듈의 transfer-neutral path capability로 변환합니다.
 *
 * 이 adapter는 [S3TransferOperations] classpath가 확인된 auto-configuration phase에서만
 * 로드됩니다. 따라서 transfer manager가 없는 consumer도 [S3ImageStorage]의 byte/object
 * CRUD를 사용할 수 있습니다.
 */
class S3TransferOperationsAdapter(
    private val delegate: S3TransferOperations,
) : S3PathTransferOperations {

    override suspend fun uploadFile(
        bucket: String,
        key: String,
        source: Path,
        contentType: String?,
    ): String? = delegate.uploadFile(bucket, key, source) {
        putObjectRequest(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build(),
        )
    }.response().eTag()
}
