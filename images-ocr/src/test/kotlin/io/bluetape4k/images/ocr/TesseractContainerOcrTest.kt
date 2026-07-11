package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@EnabledIfSystemProperty(named = "ocr.container.enabled", matches = "true")
class TesseractContainerOcrTest {

    @Test
    fun `launcher keeps one Tesseract container for the module test JVM`() {
        TesseractContainerLauncher.container shouldBeSameInstanceAs TesseractContainerLauncher.container
    }

    @Test
    fun `module JVM Tesseract container exposes required language packs`() {
        val result = TesseractContainerLauncher.container.execInContainer("tesseract", "--list-langs")

        result.exitCode shouldBeEqualTo 0
        result.stdout shouldContain "eng"
        result.stdout shouldContain "kor"
        result.stdout shouldContain "jpn"
    }
}
