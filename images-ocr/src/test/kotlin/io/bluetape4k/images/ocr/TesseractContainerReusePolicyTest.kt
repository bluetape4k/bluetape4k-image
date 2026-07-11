package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test

class TesseractContainerReusePolicyTest {

    @Test
    fun `container reuse is disabled unless explicitly requested`() {
        tesseractContainerReuseEnabled(reuseRequested = null, ci = null).shouldBeFalse()
        tesseractContainerReuseEnabled(reuseRequested = "false", ci = null).shouldBeFalse()
    }

    @Test
    fun `developer can explicitly request local container reuse`() {
        tesseractContainerReuseEnabled(reuseRequested = "true", ci = null).shouldBeTrue()
    }

    @Test
    fun `CI cannot enable reusable containers`() {
        tesseractContainerReuseEnabled(reuseRequested = "true", ci = "true").shouldBeFalse()
    }
}
