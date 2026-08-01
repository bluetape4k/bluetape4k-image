package io.bluetape4k.images.spring

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test

class UploadOptionsTest {

    @Test
    fun `default contentType is image jpeg`() {
        val options = UploadOptions()
        options.contentType shouldBeEqualTo "image/jpeg"
    }

    @Test
    fun `default cacheControl is set`() {
        val options = UploadOptions()
        options.cacheControl shouldBeEqualTo "public, max-age=31536000"
    }

    @Test
    fun `default metadata is empty`() {
        val options = UploadOptions()
        options.metadata.isEmpty().shouldBeTrue()
    }

    @Test
    fun `image jpeg creates successfully`() {
        val options = UploadOptions(contentType = "image/jpeg")
        options.contentType shouldBeEqualTo "image/jpeg"
    }

    @Test
    fun `image png creates successfully`() {
        val options = UploadOptions(contentType = "image/png")
        options.contentType shouldBeEqualTo "image/png"
    }

    @Test
    fun `image webp creates successfully`() {
        val options = UploadOptions(contentType = "image/webp")
        options.contentType shouldBeEqualTo "image/webp"
    }

    @Test
    fun `image gif creates successfully`() {
        val options = UploadOptions(contentType = "image/gif")
        options.contentType shouldBeEqualTo "image/gif"
    }

    @Test
    fun `image avif creates successfully`() {
        val options = UploadOptions(contentType = "image/avif")
        options.contentType shouldBeEqualTo "image/avif"
    }

    @Test
    fun `image heic creates successfully`() {
        val options = UploadOptions(contentType = "image/heic")
        options.contentType shouldBeEqualTo "image/heic"
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
        options.metadata shouldBeEqualTo metadata
    }

    @Test
    fun `custom cacheControl is stored correctly`() {
        val options = UploadOptions(cacheControl = "no-cache")
        options.cacheControl shouldBeEqualTo "no-cache"
    }

    @Test
    fun `ALLOWED_CONTENT_TYPES contains expected types`() {
        val allowed = UploadOptions.ALLOWED_CONTENT_TYPES
        ("image/jpeg" in allowed).shouldBeTrue()
        ("image/png" in allowed).shouldBeTrue()
        ("image/webp" in allowed).shouldBeTrue()
        ("image/gif" in allowed).shouldBeTrue()
        ("image/avif" in allowed).shouldBeTrue()
        ("image/heic" in allowed).shouldBeTrue()
    }

    @Test
    fun `ALLOWED_CONTENT_TYPES does not contain svg`() {
        ("image/svg+xml" !in UploadOptions.ALLOWED_CONTENT_TYPES).shouldBeTrue()
    }
}
