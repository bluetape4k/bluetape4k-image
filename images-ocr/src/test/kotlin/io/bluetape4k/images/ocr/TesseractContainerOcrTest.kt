package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.junit5.system.SystemProperty
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@SystemProperty("ocr.container.enabled", value = "true")
class TesseractContainerOcrTest {

    companion object: KLogging()

    @Test
    fun `launcher keeps one Tesseract container for the module test JVM`() {
        val first = TesseractContainerLauncher.container
        val second = TesseractContainerLauncher.container

        first shouldBeSameInstanceAs second
    }

    @Test
    fun `module JVM Tesseract container exposes required language packs`() {
        val result = TesseractContainerLauncher.container.execInContainer("tesseract", "--list-langs")

        log.debug { "result=$result" }
        result.exitCode shouldBeEqualTo 0
        result.stdout shouldContain "eng"
        result.stdout shouldContain "kor"
        result.stdout shouldContain "jpn"
    }
}
