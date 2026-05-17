package io.bluetape4k.images.spring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImageObjectKeyTest {

    @Test
    fun `of creates key with correct fields`() {
        val key = ImageObjectKey.of("uploads", "photo.jpg")
        assertEquals("uploads", key.prefix)
        assertEquals("photo.jpg", key.name)
    }

    @Test
    fun `fullKey joins prefix and name with slash`() {
        val key = ImageObjectKey.of("uploads", "photo.jpg")
        assertEquals("uploads/photo.jpg", key.fullKey)
    }

    @Test
    fun `fullKey normalizes trailing slash in prefix`() {
        val key = ImageObjectKey.of("uploads/", "photo.jpg")
        assertEquals("uploads/photo.jpg", key.fullKey)
    }

    @Test
    fun `of allows nested prefix segments`() {
        val key = ImageObjectKey.of("a/b/c", "file.png")
        assertEquals("a/b/c/file.png", key.fullKey)
    }

    @Test
    fun `of allows dot in name`() {
        val key = ImageObjectKey.of("thumb", "img.webp")
        assertEquals("thumb/img.webp", key.fullKey)
    }

    @Test
    fun `of rejects blank prefix`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageObjectKey.of("", "photo.jpg")
        }
    }

    @Test
    fun `of rejects blank name`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageObjectKey.of("uploads", "")
        }
    }

    @Test
    fun `of rejects double-dot in prefix`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageObjectKey.of("../etc", "photo.jpg")
        }
    }

    @Test
    fun `of rejects double-dot in name`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageObjectKey.of("uploads", "../secret.txt")
        }
    }

    @Test
    fun `of rejects special characters in prefix`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageObjectKey.of("uploads;drop", "photo.jpg")
        }
    }

    @Test
    fun `of rejects special characters in name`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageObjectKey.of("uploads", "photo?.jpg")
        }
    }

    @Test
    fun `data class equality based on fields`() {
        val a = ImageObjectKey.of("uploads", "photo.jpg")
        val b = ImageObjectKey.of("uploads", "photo.jpg")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
