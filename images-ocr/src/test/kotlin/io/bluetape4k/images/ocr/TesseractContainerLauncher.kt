package io.bluetape4k.images.ocr

import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile

private const val REUSE_PROPERTY = "ocr.container.reuse"

internal fun tesseractContainerReuseEnabled(
    reuseRequested: String? = System.getProperty(REUSE_PROPERTY),
    environment: Map<String, String> = System.getenv(),
): Boolean = reuseRequested.equals("true", ignoreCase = true) &&
    "CI" !in environment &&
    "GITHUB_ACTIONS" !in environment

internal object TesseractContainerLauncher {

    val container: GenericContainer<Nothing> by lazy {
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

        GenericContainer<Nothing>(image)
            .withReuse(tesseractContainerReuseEnabled())
            .apply { start() }
    }
}
