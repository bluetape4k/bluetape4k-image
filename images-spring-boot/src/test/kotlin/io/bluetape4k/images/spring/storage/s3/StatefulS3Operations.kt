package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.aws.spring.s3.S3ListPage
import io.bluetape4k.aws.spring.s3.S3ObjectMetadata
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.aws.spring.s3.S3Resource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Object
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** 네트워크 없이 [S3ImageStorage]의 공통 계약을 실행하기 위한 stateful test fixture입니다. */
internal class StatefulS3Operations : S3Operations {

    private data class StoredObject(
        val bytes: ByteArray,
        val contentType: String?,
    )

    private val objects = ConcurrentHashMap<String, StoredObject>()
    private val reportedSizes = ConcurrentHashMap<String, Long>()
    private val activeInputStreams = AtomicInteger()
    private val listInvocations = AtomicInteger()
    private val listEmissions = AtomicInteger()
    private val listEnumerations = AtomicInteger()
    private val activeListCollectors = AtomicInteger()
    private val nextListCancellation = AtomicReference<CancellationException?>()

    override suspend fun headObject(bucket: String, key: String): S3ObjectMetadata {
        val stored = objects[key] ?: throw NoSuchKeyException.builder().build()
        return S3ObjectMetadata(
            sizeBytes = reportedSizes[key] ?: stored.bytes.size.toLong(),
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
        store(key, bytes, contentType)
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
        reportedSizes.remove(key)
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

    override fun listFlow(bucket: String, prefix: String?, pageSize: Int): Flow<S3Object> = flow {
        listInvocations.incrementAndGet()
        activeListCollectors.incrementAndGet()
        try {
            nextListCancellation.getAndSet(null)?.let { throw it }
            listedObjectSequence(prefix).forEach { objectMetadata ->
                currentCoroutineContext().ensureActive()
                listEmissions.incrementAndGet()
                emit(objectMetadata)
            }
        } finally {
            activeListCollectors.decrementAndGet()
        }
    }

    override fun resource(bucket: String, key: String): S3Resource =
        mockk<S3Resource>().also { resource ->
            every { resource.getInputStream() } answers {
                val bytes = load(key)
                activeInputStreams.incrementAndGet()
                object : ByteArrayInputStream(bytes) {
                    private val closed = AtomicBoolean()

                    override fun close() {
                        if (closed.compareAndSet(false, true)) {
                            activeInputStreams.decrementAndGet()
                        }
                        super.close()
                    }
                }
            }
        }

    override fun presignGet(bucket: String, key: String, duration: Duration?): URL =
        URI("https://example.com/$bucket/$key").toURL()

    override fun presignPut(bucket: String, key: String, duration: Duration?, contentType: String?): URL =
        URI("https://example.com/$bucket/$key").toURL()

    internal fun store(key: String, bytes: ByteArray, contentType: String?) {
        objects[key] = StoredObject(bytes.copyOf(), contentType)
        reportedSizes.remove(key)
    }

    internal fun load(key: String): ByteArray =
        objects[key]?.bytes?.copyOf() ?: throw NoSuchKeyException.builder().build()

    internal fun overrideReportedSize(key: String, sizeBytes: Long) {
        reportedSizes[key] = sizeBytes
    }

    internal fun hasOpenInputStreams(): Boolean = activeInputStreams.get() != 0

    internal fun resetListObservations() {
        listInvocations.set(0)
        listEmissions.set(0)
        listEnumerations.set(0)
    }

    internal fun listInvocationCount(): Int = listInvocations.get()

    internal fun listEmissionCount(): Int = listEmissions.get()

    internal fun listEnumerationCount(): Int = listEnumerations.get()

    internal fun hasOpenListCollectors(): Boolean = activeListCollectors.get() != 0

    internal fun cancelNextList(cancellation: CancellationException) {
        nextListCancellation.set(cancellation)
    }

    private fun listedObjects(prefix: String?): List<S3Object> =
        listedObjectSequence(prefix).sortedBy(S3Object::key).toList()

    private fun listedObjectSequence(prefix: String?): Sequence<S3Object> =
        objects.keys
            .asSequence()
            .filter { prefix == null || it.startsWith(prefix) }
            .map { key ->
                listEnumerations.incrementAndGet()
                S3Object.builder()
                    .key(key)
                    .size(objects.getValue(key).bytes.size.toLong())
                    .build()
            }

    private fun etag(bytes: ByteArray): String = "fixture-${bytes.contentHashCode()}"
}
