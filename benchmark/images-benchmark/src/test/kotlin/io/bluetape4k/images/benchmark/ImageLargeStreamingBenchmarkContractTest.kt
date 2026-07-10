package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test

class ImageLargeStreamingBenchmarkContractTest {

    @Test
    fun `large streaming benchmark keeps fair transform and required FFM contract`() {
        val source = Files.readString(sourcePath())

        source.shouldContain("@Warmup(iterations = 1")
        source.shouldContain("vipsSupport = VipsLargePipelineSupport.createRequiredFfm()")
        source.shouldContain("image.scaleTo(config.targetWidth, config.targetHeight)")
        source.shouldNotContain("GrayscaleFilter")
        source.shouldNotContain("GRAYSCALE_FILTER")
        source.shouldNotContain(".filter(")
        source.shouldNotContain("JNI_RUNTIME_CLASS")
        source.shouldNotContain("available: Boolean")
        source.shouldNotContain("bh.consume(null)")
    }

    private fun sourcePath(): Path =
        Path.of("src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt")
}
