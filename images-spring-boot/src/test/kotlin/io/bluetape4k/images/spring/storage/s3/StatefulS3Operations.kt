package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.aws.spring.s3.S3ListPage
import io.bluetape4k.aws.spring.s3.S3ObjectMetadata
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.aws.spring.s3.S3Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Object
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** 네트워크 없이 [S3ImageStorage]의 공통 계약을 실행하기 위한 stateful test fixture입니다. */
internal class StatefulS3Operations : S3Operations {

    private data class StoredObject(
        val bytes: ByteArray,
        val contentType: String?,
    )

    private val objects = ConcurrentHashMap<String, StoredObject>()

    override suspend fun headObject(bucket: String, key: String): S3ObjectMetadata {
        val stored = objects[key] ?: throw NoSuchKeyException.builder().build()
        return S3ObjectMetadata(
            sizeBytes = stored.bytes.size.toLong(),
            etag = etag(stored.bytes),
            contentType = stored.contentType,
        )
    }

    override suspend fun existsBucket(bucket: String): Boolean = true

    override suspend fun upload(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String?,
    ): PutObjectResponse {
        objects[key] = StoredObject(bytes.copyOf(), contentType)
        return PutObjectResponse.builder().eTag(etag(bytes)).build()
    }

    override suspend fun upload(
        bucket: String,
        key: String,
        contents: String,
        charset: Charset,
        contentType: String?,
    ): PutObjectResponse = upload(bucket, key, contents.toByteArray(charset), contentType)

    override suspend fun downloadBytes(bucket: String, key: String): ByteArray =
        objects[key]?.bytes?.copyOf() ?: throw NoSuchKeyException.builder().build()

    override suspend fun downloadText(bucket: String, key: String, charset: Charset): String =
        downloadBytes(bucket, key).toString(charset)

    override suspend fun delete(bucket: String, key: String): DeleteObjectResponse {
        objects.remove(key)
        return DeleteObjectResponse.builder().build()
    }

    override suspend fun listPage(
        bucket: String,
        prefix: String?,
        maxKeys: Int,
        continuationToken: String?,
    ): S3ListPage {
        val listed = listedObjects(prefix).take(maxKeys)
        return S3ListPage(
            objects = listed,
            isTruncated = false,
            nextContinuationToken = null,
            keyCount = listed.size,
        )
    }

    override fun listFlow(bucket: String, prefix: String?, pageSize: Int): Flow<S3Object> =
        listedObjects(prefix).asFlow()

    override fun resource(bucket: String, key: String): S3Resource =
        throw UnsupportedOperationException("Path contract는 STORAGE-3B에서 제공합니다.")

    override fun presignGet(bucket: String, key: String, duration: Duration?): URL =
        URI("https://example.com/$bucket/$key").toURL()

    override fun presignPut(bucket: String, key: String, duration: Duration?, contentType: String?): URL =
        URI("https://example.com/$bucket/$key").toURL()

    private fun listedObjects(prefix: String?): List<S3Object> =
        objects.keys
            .asSequence()
            .filter { prefix == null || it.startsWith(prefix) }
            .sorted()
            .map { key ->
                S3Object.builder()
                    .key(key)
                    .size(objects.getValue(key).bytes.size.toLong())
                    .build()
            }
            .toList()

    private fun etag(bytes: ByteArray): String = "fixture-${bytes.contentHashCode()}"
}
