package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile

@EnabledIfSystemProperty(named = "ocr.container.enabled", matches = "true")
class TesseractContainerOcrTest {

    @Test
    fun `test-owned Tesseract container exposes required language packs`() {
        val image = ImageFromDockerfile("bluetape4k-image-ocr-test:latest", false)
            .withDockerfileFromBuilder { builder ->
                builder
                    .from("ubuntu:24.04")
                    .run(
                        "apt-get update && " +
                            "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends " +
                            "tesseract-ocr tesseract-ocr-eng tesseract-ocr-kor tesseract-ocr-jpn fonts-noto-cjk && " +
                            "rm -rf /var/lib/apt/lists/*",
                    )
                    .cmd("sleep", "60")
                    .build()
            }

        GenericContainer<Nothing>(image).use { container ->
            container.start()

            val result = container.execInContainer("tesseract", "--list-langs")

            result.exitCode shouldBeEqualTo 0
            result.stdout shouldContain "eng"
            result.stdout shouldContain "kor"
            result.stdout shouldContain "jpn"
        }
    }
}
