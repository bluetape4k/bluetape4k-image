package io.bluetape4k.images.spring

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class UploadOptionsTest {

    companion object: KLogging()

    @Test
    fun `default contentType is image jpeg`() {
        val options = UploadOptions()

        log.debug { "options=$options" }
        options.contentType shouldBeEqualTo "image/jpeg"
    }

    @Test
    fun `default cacheControl is set`() {
        val options = UploadOptions()

        log.debug { "options=$options" }
        options.cacheControl shouldBeEqualTo "public, max-age=31536000"
    }

    @Test
    fun `default metadata is empty`() {
        val options = UploadOptions()

        log.debug { "options=$options" }
        options.metadata.shouldBeEmpty()
    }

    @Test
    fun `image jpeg creates successfully`() {
        val options = UploadOptions(contentType = "image/jpeg")

        log.debug { "options=$options" }
        options.contentType shouldBeEqualTo "image/jpeg"
    }

    @Test
    fun `image png creates successfully`() {
        val options = UploadOptions(contentType = "image/png")

        log.debug { "options=$options" }
        options.contentType shouldBeEqualTo "image/png"
    }

    @Test
    fun `image webp creates successfully`() {
        val options = UploadOptions(contentType = "image/webp")

        log.debug { "options=$options" }
        options.contentType shouldBeEqualTo "image/webp"
    }

    @Test
    fun `image gif creates successfully`() {
        val options = UploadOptions(contentType = "image/gif")

        log.debug { "options=$options" }
        options.contentType shouldBeEqualTo "image/gif"
    }

    @Test
    fun `image avif creates successfully`() {
        val options = UploadOptions(contentType = "image/avif")

        log.debug { "options=$options" }
        options.contentType shouldBeEqualTo "image/avif"
    }

    @Test
    fun `image heic creates successfully`() {
        val options = UploadOptions(contentType = "image/heic")

        log.debug { "options=$options" }
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

        log.debug { "options=$options" }
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

        allowed shouldContain "image/jpeg"
        allowed shouldContain "image/png"
        allowed shouldContain "image/webp"
        allowed shouldContain "image/gif"
        allowed shouldContain "image/avif"
        allowed shouldContain "image/heic"
    }

    @Test
    fun `ALLOWED_CONTENT_TYPES does not contain svg`() {
        val allowed = UploadOptions.ALLOWED_CONTENT_TYPES

        allowed shouldNotContain "image/svg+xml"
    }
}
