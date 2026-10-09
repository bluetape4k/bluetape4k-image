package io.bluetape4k.images.batch

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ImageBatchModelsTest: AbstractImageTest() {

    companion object: KLogging()

    // ── ImageBatchFailureStage 검증 ─────────────────────────────────────────

    @Test
    fun `ImageBatchFailureStage has all expected values`() {
        val stages = ImageBatchFailureStage.entries

        stages.any { it == ImageBatchFailureStage.VALIDATION }.shouldBeTrue()
        stages.any { it == ImageBatchFailureStage.LOAD }.shouldBeTrue()
        stages.any { it == ImageBatchFailureStage.TRANSFORM }.shouldBeTrue()
        stages.any { it == ImageBatchFailureStage.WRITE }.shouldBeTrue()
        stages shouldHaveSize 4
    }

    // ── ImageBatchException 검증 ───────────────────────────────────────────

    @Test
    fun `ImageBatchException stores source stage and message`() {
        val path = Path.of("/tmp/photo.jpg")
        val ex = ImageBatchException(
            source = path,
            stage = ImageBatchFailureStage.LOAD,
            message = "could not read file",
        )

        ex.source shouldBeEqualTo path
        ex.stage shouldBeEqualTo ImageBatchFailureStage.LOAD
        ex.output.shouldBeNull()
        ex.message shouldBeEqualTo "could not read file"
        ex.cause.shouldBeNull()
    }

    @Test
    fun `ImageBatchException stores optional output and cause`() {
        val source = Path.of("/input.jpg")
        val output = Path.of("/output.jpg")
        val cause = RuntimeException("disk full")

        val ex = ImageBatchException(
            source = source,
            stage = ImageBatchFailureStage.WRITE,
            output = output,
            message = "write failed",
            cause = cause,
        )

        ex.output shouldBeEqualTo output
        ex.cause shouldBeEqualTo cause
    }

    // ── ImageBatchResult sealed hierarchy 검증 ─────────────────────────────

    @Test
    fun `ImageBatchResult Failure carries source stage output and cause`() {
        val source = Path.of("/in.jpg")
        val output = Path.of("/out.jpg")
        val cause = RuntimeException("oops")

        val failure = ImageBatchResult.Failure(
            source = source,
            stage = ImageBatchFailureStage.TRANSFORM,
            output = output,
            cause = cause,
        )

        failure.source shouldBeEqualTo source
        failure.stage shouldBeEqualTo ImageBatchFailureStage.TRANSFORM
        failure.output shouldBeEqualTo output
        failure.cause shouldBeEqualTo cause
    }

    @Test
    fun `ImageBatchResult Failure output defaults to null`() {
        val failure = ImageBatchResult.Failure(
            source = Path.of("/in.jpg"),
            stage = ImageBatchFailureStage.LOAD,
            cause = RuntimeException("missing"),
        )
        failure.output.shouldBeNull()
    }

    // ── ImageProcessingOptions 검증 ────────────────────────────────────────

    @Test
    fun `ImageProcessingOptions default values are set correctly`() {
        val opts = ImageProcessingOptions()

        log.debug { "Image processing options=$opts" }
        opts.parallelism shouldBeEqualTo defaultImageBatchParallelism()
        opts.maxPixels shouldBeEqualTo DEFAULT_MAX_PIXELS
        opts.maxInFlightPixels shouldBeEqualTo DEFAULT_MAX_IN_FLIGHT_PIXELS
        opts.skipFailures.shouldBeFalse()
    }

    @Test
    fun `ImageProcessingOptions rejects zero parallelism`() {
        assertFailsWith<IllegalArgumentException> {
            ImageProcessingOptions(parallelism = 0)
        }
    }

    @Test
    fun `ImageProcessingOptions rejects negative parallelism`() {
        assertFailsWith<IllegalArgumentException> {
            ImageProcessingOptions(parallelism = -1)
        }
    }

    @Test
    fun `ImageProcessingOptions rejects zero maxPixels`() {
        assertFailsWith<IllegalArgumentException> {
            ImageProcessingOptions(maxPixels = 0L)
        }
    }

    @Test
    fun `ImageProcessingOptions rejects zero maxInFlightPixels`() {
        assertFailsWith<IllegalArgumentException> {
            ImageProcessingOptions(maxInFlightPixels = 0L)
        }
    }

    @Test
    fun `ImageProcessingOptions largeJobs sets enlarged pixel limits`() {
        val opts = ImageProcessingOptions.largeJobs(parallelism = 2)

        opts.maxPixels shouldBeEqualTo LARGE_JOB_MAX_PIXELS
        opts.maxInFlightPixels shouldBeEqualTo LARGE_JOB_MAX_IN_FLIGHT_PIXELS
        opts.maxPixels shouldBeGreaterThan DEFAULT_MAX_PIXELS
        opts.maxInFlightPixels shouldBeGreaterThan DEFAULT_MAX_IN_FLIGHT_PIXELS
        opts.parallelism shouldBeEqualTo 2
    }

    @Test
    fun `ImageProcessingOptions onFailure callback is invoked`() = runTest {
        val recorded = mutableListOf<ImageBatchResult.Failure>()
        val opts = ImageProcessingOptions(
            onFailure = { recorded += it },
        )

        val failure = ImageBatchResult.Failure(
            source = Path.of("/img.jpg"),
            stage = ImageBatchFailureStage.LOAD,
            cause = RuntimeException("boom"),
        )
        opts.onFailure(failure)

        recorded shouldHaveSize 1
        recorded.single().stage shouldBeEqualTo ImageBatchFailureStage.LOAD
    }
}
