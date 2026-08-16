package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.aws.spring.s3.S3TransferOperations
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.transfer.s3.model.CompletedDownload
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload
import software.amazon.awssdk.transfer.s3.model.CompletedUpload
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest
import software.amazon.awssdk.transfer.s3.model.DownloadRequest
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import software.amazon.awssdk.transfer.s3.model.UploadRequest
import java.nio.file.Files
import java.nio.file.Path

/** [StatefulS3Operations]와 같은 object state를 사용하는 Path upload fixture입니다. */
internal class StatefulS3TransferOperations(
    private val operations: StatefulS3Operations,
) : S3TransferOperations {

    override suspend fun upload(
        bucket: String,
        key: String,
        bytes: ByteArray,
        configure: UploadRequest.Builder.() -> Unit,
    ): CompletedUpload = error("byte transfer는 Path contract에서 사용하지 않습니다.")

    override suspend fun uploadFile(
        bucket: String,
        key: String,
        source: Path,
        configure: UploadFileRequest.Builder.() -> Unit,
    ): CompletedFileUpload {
        val request = UploadFileRequest.builder()
            .source(source)
            .putObjectRequest { builder -> builder.bucket(bucket).key(key) }
            .apply(configure)
            .build()
        val bytes = Files.readAllBytes(source)
        operations.store(key, bytes, request.putObjectRequest().contentType())
        return CompletedFileUpload.builder()
            .response(PutObjectResponse.builder().eTag("fixture-${bytes.contentHashCode()}").build())
            .build()
    }

    override suspend fun downloadBytes(
        bucket: String,
        key: String,
        configure: DownloadRequest.UntypedBuilder.() -> Unit,
    ): CompletedDownload<ResponseBytes<GetObjectResponse>> =
        error("byte transfer는 Path contract에서 사용하지 않습니다.")

    override suspend fun downloadFile(
        bucket: String,
        key: String,
        destination: Path,
        configure: DownloadFileRequest.Builder.() -> Unit,
    ): CompletedFileDownload = error("downloadFile은 ImageStorage Path contract에서 사용하지 않습니다.")
}
