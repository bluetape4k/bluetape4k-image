package io.bluetape4k.images.spring.storage.s3

import java.nio.file.Files
import java.nio.file.Path

/** [StatefulS3Operations]와 같은 object state를 사용하는 Path upload fixture입니다. */
internal class StatefulS3TransferOperations(
    private val operations: StatefulS3Operations,
) : S3PathTransferOperations {

    override suspend fun uploadFile(
        bucket: String,
        key: String,
        source: Path,
        contentType: String?,
    ): String? {
        val bytes = Files.readAllBytes(source)
        operations.store(key, bytes, contentType)
        return "fixture-${bytes.contentHashCode()}"
    }
}
