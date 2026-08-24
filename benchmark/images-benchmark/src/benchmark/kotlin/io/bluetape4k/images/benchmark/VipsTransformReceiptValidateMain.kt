package io.bluetape4k.images.benchmark

import java.nio.file.Files
import java.nio.file.Path

/** CI에서 Issue #582 transform receipt의 schema·coverage·N/A 경계를 검증합니다. */
object VipsTransformReceiptValidateMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2 && args[0] == "--input") {
            "Usage: --input <transform-receipt.json>"
        }
        val input = Path.of(args[1]).toAbsolutePath().normalize()
        require(input.isAbsolute && Files.isRegularFile(input)) {
            "Vips transform receipt is missing: $input"
        }
        VipsTransformReceiptValidator.validateJson(Files.readAllBytes(input))
        println("Validated Vips transform receipt: $input")
    }
}
