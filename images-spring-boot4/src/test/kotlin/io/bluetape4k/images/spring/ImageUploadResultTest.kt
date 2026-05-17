package io.bluetape4k.images.spring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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

        assertEquals(key, result.key)
        assertEquals("abc123", result.etag)
        assertEquals(1024L, result.sizeBytes)
        assertEquals("image/jpeg", result.contentType)
        assertEquals(uploadedAt, result.uploadedAt)
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

        assertNotNull(result.uploadedAt)
        assertTrue(
            !result.uploadedAt.isBefore(before.minusSeconds(5)) &&
                !result.uploadedAt.isAfter(after.plusSeconds(5)),
            "uploadedAt should be within 5 seconds of now",
        )
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

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
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

        assertNotEquals(a, b)
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

        assertEquals(key, updated.key)
        assertEquals("def456", updated.etag)
        assertEquals(2048L, updated.sizeBytes)
        assertEquals("image/jpeg", updated.contentType)
        assertEquals(uploadedAt, updated.uploadedAt)
        assertNotEquals(original, updated)
    }
}
