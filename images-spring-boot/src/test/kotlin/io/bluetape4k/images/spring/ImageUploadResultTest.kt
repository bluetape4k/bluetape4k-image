package io.bluetape4k.images.spring

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ImageUploadResultTest {

    private val key = ImageObjectKey.of("uploads", "photo.jpg")

    @Test
    fun `creates with correct fields`() {
        val uploadedAt = Instant.now()
        val result = ImageUploadResult(
            key = key,
            etag = "abc123",
            sizeBytes = 1024L,
            contentType = "image/jpeg",
            uploadedAt = uploadedAt,
        )

        result.key shouldBeEqualTo key
        result.etag shouldBeEqualTo "abc123"
        result.sizeBytes shouldBeEqualTo 1024L
        result.contentType shouldBeEqualTo "image/jpeg"
        result.uploadedAt shouldBeEqualTo uploadedAt
    }

    @Test
    fun `uploadedAt defaults to approximately now`() {
        val before = Instant.now()
        val result = ImageUploadResult(
            key = key,
            etag = "abc123",
            sizeBytes = 512L,
            contentType = "image/png",
        )
        val after = Instant.now()

        result.uploadedAt.shouldNotBeNull()
        (
            !result.uploadedAt.isBefore(before.minusSeconds(5)) &&
                !result.uploadedAt.isAfter(after.plusSeconds(5))
            ).shouldBeTrue()
    }

    @Test
    fun `equality based on all fields`() {
        val uploadedAt = Instant.parse("2024-01-01T00:00:00Z")
        val a = ImageUploadResult(
            key = key,
            etag = "abc123",
            sizeBytes = 1024L,
            contentType = "image/jpeg",
            uploadedAt = uploadedAt,
        )
        val b = ImageUploadResult(
            key = key,
            etag = "abc123",
            sizeBytes = 1024L,
            contentType = "image/jpeg",
            uploadedAt = uploadedAt,
        )

        a shouldBeEqualTo b
        a.hashCode() shouldBeEqualTo b.hashCode()
    }

    @Test
    fun `different etag produces different equality`() {
        val uploadedAt = Instant.parse("2024-01-01T00:00:00Z")
        val a = ImageUploadResult(
            key = key,
            etag = "abc123",
            sizeBytes = 1024L,
            contentType = "image/jpeg",
            uploadedAt = uploadedAt,
        )
        val b = ImageUploadResult(
            key = key,
            etag = "xyz789",
            sizeBytes = 1024L,
            contentType = "image/jpeg",
            uploadedAt = uploadedAt,
        )

        (a == b).shouldBeFalse()
    }

    @Test
    fun `copy creates new instance with updated field`() {
        val uploadedAt = Instant.parse("2024-06-01T12:00:00Z")
        val original = ImageUploadResult(
            key = key,
            etag = "abc123",
            sizeBytes = 1024L,
            contentType = "image/jpeg",
            uploadedAt = uploadedAt,
        )

        val updated = original.copy(sizeBytes = 2048L, etag = "def456")

        updated.key shouldBeEqualTo key
        updated.etag shouldBeEqualTo "def456"
        updated.sizeBytes shouldBeEqualTo 2048L
        updated.contentType shouldBeEqualTo "image/jpeg"
        updated.uploadedAt shouldBeEqualTo uploadedAt
        (original == updated).shouldBeFalse()
    }
}
