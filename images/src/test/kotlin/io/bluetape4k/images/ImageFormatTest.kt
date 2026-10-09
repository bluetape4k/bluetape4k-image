package io.bluetape4k.images

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

class ImageFormatTest {

    companion object: KLogging()

    @Test
    fun `parse should ignore case and trim`() {
        ImageFormat.parse(" jpg ") shouldBeEqualTo ImageFormat.JPG
        ImageFormat.parse("Png") shouldBeEqualTo ImageFormat.PNG
        ImageFormat.parse("jpeg") shouldBeEqualTo ImageFormat.JPG
    }

    @Test
    fun `parse should return null for blank or unknown`() {
        ImageFormat.parse("").shouldBeNull()
        ImageFormat.parse("  ").shouldBeNull()
        ImageFormat.parse("\t").shouldBeNull()
        ImageFormat.parse("unknown").shouldBeNull()
    }
}
