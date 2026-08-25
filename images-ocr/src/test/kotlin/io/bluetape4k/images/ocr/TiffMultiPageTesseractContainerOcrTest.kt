package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.should
import io.bluetape4k.images.coroutines.SuspendTiffMultiPageWriter
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.testcontainers.utility.MountableFile
import kotlinx.coroutines.runBlocking

/** 실제 container Tesseract CLI에 3-page TIFF를 전달하는 orchestration smoke입니다. */
@EnabledIfSystemProperty(named = "ocr.container.enabled", matches = "true")
class TiffMultiPageTesseractContainerOcrTest {

    @Test
    fun `container Tesseract recognizes TIFF pages in order`() {
        val engine = ContainerStructuredOcrEngine()
        val result = TiffMultiPageOcr(engine).recognize(threePageTiff())

        result.pages.map(OcrPage::pageIndex) shouldBeEqualTo listOf(0, 1, 2)
        result.pages.map(OcrPage::text).forEach { it.shouldNotBeEmpty() }
        result.text.split("\n\n").size shouldBeEqualTo 3
    }

    private fun threePageTiff(): ByteArray = runBlocking {
        val output = ByteArrayOutputStream()
        SuspendTiffMultiPageWriter().suspendWrite(
            listOf(textImage("PAGE ONE"), textImage("PAGE TWO"), textImage("PAGE THREE")),
            output,
        )
        output.toByteArray()
    }

    private class ContainerStructuredOcrEngine : StructuredOcrEngine {
        private var pageNumber = 0

        override fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult =
            error("plain OCR is not used by the multipage container smoke")

        override fun recognizeStructured(image: ImmutableImage, options: OcrOptions): OcrStructuredResult {
            val index = pageNumber++
            val hostFile = Files.createTempFile("issue-492-page-$index-", ".png")
            val containerFile = "/tmp/issue-492-page-$index.png"
            try {
                javax.imageio.ImageIO.write(image.awt(), "png", hostFile.toFile())
                TesseractContainerLauncher.container.copyFileToContainer(
                    MountableFile.forHostPath(hostFile),
                    containerFile,
                )
                val output = TesseractContainerLauncher.container.execInContainer(
                    "tesseract",
                    containerFile,
                    "stdout",
                    "-l",
                    "eng",
                    "--psm",
                    "7",
                )
                output.exitCode.should("container Tesseract failed for page $index") { it == 0 }
                val text = output.stdout.trim()
                return OcrStructuredResult(
                    text = text,
                    options = options,
                    pages = listOf(OcrPage(pageIndex = 0, text = text)),
                )
            } finally {
                Files.deleteIfExists(hostFile)
                TesseractContainerLauncher.container.execInContainer("rm", "-f", containerFile)
            }
        }
    }
}
