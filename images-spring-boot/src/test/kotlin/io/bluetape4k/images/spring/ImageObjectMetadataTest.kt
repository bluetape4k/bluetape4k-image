package io.bluetape4k.images.spring

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.Instant

class ImageObjectMetadataTest {

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

        metadata.key shouldBeEqualTo key
        metadata.sizeBytes shouldBeEqualTo 42L
        metadata.etag shouldBeEqualTo "\"multipart-token\""
        metadata.contentType shouldBeEqualTo null
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
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { stream -> stream.writeObject(metadata) }
            output.toByteArray()
        }

        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { stream ->
            stream.readObject() as ImageObjectMetadata
        }

        restored shouldBeEqualTo metadata
    }
}
