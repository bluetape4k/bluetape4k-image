package io.bluetape4k.images.spring

import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UploadOptionsTest {

    @Test
    fun `default contentType is image jpeg`() {
        val options = UploadOptions()
        assertEquals("image/jpeg", options.contentType)
    }

    @Test
    fun `default cacheControl is set`() {
        val options = UploadOptions()
        assertEquals("public, max-age=31536000", options.cacheControl)
    }

    @Test
    fun `default metadata is empty`() {
        val options = UploadOptions()
        assertTrue(options.metadata.isEmpty())
    }

    @Test
    fun `image jpeg creates successfully`() {
        val options = UploadOptions(contentType = "image/jpeg")
        assertEquals("image/jpeg", options.contentType)
    }

    @Test
    fun `image png creates successfully`() {
        val options = UploadOptions(contentType = "image/png")
        assertEquals("image/png", options.contentType)
    }

    @Test
    fun `image webp creates successfully`() {
        val options = UploadOptions(contentType = "image/webp")
        assertEquals("image/webp", options.contentType)
    }

    @Test
    fun `image gif creates successfully`() {
        val options = UploadOptions(contentType = "image/gif")
        assertEquals("image/gif", options.contentType)
    }

    @Test
    fun `image avif creates successfully`() {
        val options = UploadOptions(contentType = "image/avif")
        assertEquals("image/avif", options.contentType)
    }

    @Test
    fun `image heic creates successfully`() {
        val options = UploadOptions(contentType = "image/heic")
        assertEquals("image/heic", options.contentType)
    }

    @Test
    fun `blank contentType throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            UploadOptions(contentType = "")
        }
    }

    @Test
    fun `whitespace-only contentType throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            UploadOptions(contentType = "   ")
        }
    }

    @Test
    fun `image svg xml throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            UploadOptions(contentType = "image/svg+xml")
        }
    }

    @Test
    fun `text plain throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            UploadOptions(contentType = "text/plain")
        }
    }

    @Test
    fun `custom metadata is stored correctly`() {
        val metadata = mapOf("author" to "alice", "source" to "camera")
        val options = UploadOptions(metadata = metadata)
        assertEquals(metadata, options.metadata)
    }

    @Test
    fun `custom cacheControl is stored correctly`() {
        val options = UploadOptions(cacheControl = "no-cache")
        assertEquals("no-cache", options.cacheControl)
    }

    @Test
    fun `ALLOWED_CONTENT_TYPES contains expected types`() {
        val allowed = UploadOptions.ALLOWED_CONTENT_TYPES
        assertTrue("image/jpeg" in allowed)
        assertTrue("image/png" in allowed)
        assertTrue("image/webp" in allowed)
        assertTrue("image/gif" in allowed)
        assertTrue("image/avif" in allowed)
        assertTrue("image/heic" in allowed)
    }

    @Test
    fun `ALLOWED_CONTENT_TYPES does not contain svg`() {
        assertTrue("image/svg+xml" !in UploadOptions.ALLOWED_CONTENT_TYPES)
    }
}
