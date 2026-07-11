package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test

class TesseractContainerReusePolicyTest {

    @Test
    fun `container reuse is disabled unless explicitly requested`() {
        tesseractContainerReuseEnabled(reuseRequested = null, environment = emptyMap()).shouldBeFalse()
        tesseractContainerReuseEnabled(reuseRequested = "false", environment = emptyMap()).shouldBeFalse()
    }

    @Test
    fun `developer can explicitly request local container reuse`() {
        tesseractContainerReuseEnabled(
            reuseRequested = "true",
            environment = emptyMap(),
        ).shouldBeTrue()
    }

    @Test
    fun `CI markers deny reusable containers regardless of marker value`() {
        listOf("true", "1", "false", "").forEach { marker ->
            tesseractContainerReuseEnabled(
                reuseRequested = "true",
                environment = mapOf("CI" to marker),
            ).shouldBeFalse()

            tesseractContainerReuseEnabled(
                reuseRequested = "true",
                environment = mapOf("GITHUB_ACTIONS" to marker),
            ).shouldBeFalse()
        }
    }

    @Test
    fun `either CI marker denies reuse when both markers are present`() {
        tesseractContainerReuseEnabled(
            reuseRequested = "true",
            environment = mapOf("CI" to "1", "GITHUB_ACTIONS" to "true"),
        ).shouldBeFalse()
    }
}
