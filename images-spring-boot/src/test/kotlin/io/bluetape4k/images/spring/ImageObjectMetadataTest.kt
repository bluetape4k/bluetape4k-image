package io.bluetape4k.images.spring

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.io.serializer.JdkBinarySerializer
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.time.Instant

class ImageObjectMetadataTest {

    companion object: KLogging()

    @Test
    fun `rejects a negative size`() {
        assertFailsWith<IllegalArgumentException> {
            ImageObjectMetadata(
                key = ImageObjectKey.of("uploads", "photo.jpg"),
                sizeBytes = -1,
            )
        }
    }

    @Test
    fun `preserves an opaque quoted ETag and nullable fields`() {
        val key = ImageObjectKey.of("uploads", "photo.jpg")
        val lastModified = Instant.parse("2026-08-15T00:00:01.123Z")

        val metadata = ImageObjectMetadata(
            key = key,
            sizeBytes = 42,
            etag = "\"multipart-token\"",
            contentType = null,
            lastModified = lastModified,
        )

        log.debug { "metadata=$metadata" }
        metadata.key shouldBeEqualTo key
        metadata.sizeBytes shouldBeEqualTo 42L
        metadata.etag shouldBeEqualTo "\"multipart-token\""
        metadata.contentType.shouldBeNull()
        metadata.lastModified shouldBeEqualTo lastModified
    }

    @Test
    fun `round trips through Java serialization`() {
        val metadata = ImageObjectMetadata(
            key = ImageObjectKey.of("uploads", "photo.jpg"),
            sizeBytes = 42,
            etag = "\"opaque\"",
            contentType = "image/jpeg",
            lastModified = Instant.parse("2026-08-15T00:00:01.123Z"),
        )

        val serializer = JdkBinarySerializer(objectInputFilter = null)
        val bytes = serializer.serialize(metadata)
        val restored = serializer.deserialize<ImageObjectMetadata>(bytes).shouldNotBeNull()

        restored shouldBeEqualTo metadata
    }

    @Test
    fun `round trips through Fory serialization`() {
        val metadata = ImageObjectMetadata(
            key = ImageObjectKey.of("uploads", "photo.jpg"),
            sizeBytes = 42,
            etag = "\"opaque\"",
            contentType = "image/jpeg",
            lastModified = Instant.parse("2026-08-15T00:00:01.123Z"),
        )

        val bytes = BinarySerializers.FastFory.serialize(metadata)
        val restored = BinarySerializers.FastFory.deserialize<ImageObjectMetadata>(bytes)

        restored shouldBeEqualTo metadata
    }
}
