package io.bluetape4k.images.spring

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class ImageObjectKeyTest {

    @Test
    fun `of creates key with correct fields`() {
        val key = ImageObjectKey.of("uploads", "photo.jpg")
        key.prefix shouldBeEqualTo "uploads"
        key.name shouldBeEqualTo "photo.jpg"
    }

    @Test
    fun `fullKey joins prefix and name with slash`() {
        val key = ImageObjectKey.of("uploads", "photo.jpg")
        key.fullKey shouldBeEqualTo "uploads/photo.jpg"
    }

    @Test
    fun `fullKey normalizes trailing slash in prefix`() {
        val key = ImageObjectKey.of("uploads/", "photo.jpg")
        key.fullKey shouldBeEqualTo "uploads/photo.jpg"
    }

    @Test
    fun `of allows nested prefix segments`() {
        val key = ImageObjectKey.of("a/b/c", "file.png")
        key.fullKey shouldBeEqualTo "a/b/c/file.png"
    }

    @Test
    fun `of allows dot in name`() {
        val key = ImageObjectKey.of("thumb", "img.webp")
        key.fullKey shouldBeEqualTo "thumb/img.webp"
    }

    @Test
    fun `of rejects blank prefix`() {
        assertFailsWith<IllegalArgumentException> {
            ImageObjectKey.of("", "photo.jpg")
        }
    }

    @Test
    fun `of rejects blank name`() {
        assertFailsWith<IllegalArgumentException> {
            ImageObjectKey.of("uploads", "")
        }
    }

    @Test
    fun `of rejects double-dot in prefix`() {
        assertFailsWith<IllegalArgumentException> {
            ImageObjectKey.of("../etc", "photo.jpg")
        }
    }

    @Test
    fun `of rejects double-dot in name`() {
        assertFailsWith<IllegalArgumentException> {
            ImageObjectKey.of("uploads", "../secret.txt")
        }
    }

    @Test
    fun `of rejects special characters in prefix`() {
        assertFailsWith<IllegalArgumentException> {
            ImageObjectKey.of("uploads;drop", "photo.jpg")
        }
    }

    @Test
    fun `of rejects special characters in name`() {
        assertFailsWith<IllegalArgumentException> {
            ImageObjectKey.of("uploads", "photo?.jpg")
        }
    }

    @Test
    fun `data class equality based on fields`() {
        val a = ImageObjectKey.of("uploads", "photo.jpg")
        val b = ImageObjectKey.of("uploads", "photo.jpg")
        a shouldBeEqualTo b
        a.hashCode() shouldBeEqualTo b.hashCode()
    }
}
