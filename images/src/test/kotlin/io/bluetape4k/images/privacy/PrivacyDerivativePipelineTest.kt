package io.bluetape4k.images.privacy

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.analysis.ExifData
import io.bluetape4k.images.batch.ImageProcessingOptions
import io.bluetape4k.images.moderation.SensitiveCoordinateSpace
import io.bluetape4k.images.moderation.SensitiveRegion
import io.bluetape4k.images.moderation.SensitiveRegionGeometry
import io.bluetape4k.images.thumbnail.ThumbnailSize
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrivacyDerivativePipelineTest {

    @Test
    fun `suspendPrivacyDerivative strips location metadata and reports derivative actions`() =
        runTest(timeout = 30.seconds) {
            val result = testImage().suspendPrivacyDerivative(
                sourceExif = ExifData(
                    gpsLatitude = 37.5,
                    gpsLongitude = 127.0,
                    gpsAltitude = 50.0,
                    cameraMake = "UnitCam",
                ),
            )

            result.bytes.size shouldBeGreaterThan 0
            result.report.sourceDimensions shouldBeEqualTo PrivacyImageDimensions(width = 120, height = 80)
            result.report.outputDimensions shouldBeEqualTo PrivacyImageDimensions(width = 120, height = 80)
            result.report.strippedMetadataCategories shouldContain PrivacyMetadataCategory.GPS
            result.report.strippedMetadataCategories shouldContain PrivacyMetadataCategory.EXIF
            result.report.appliedActions shouldContain PrivacyDerivativeAction.GPS_REMOVED
            result.report.appliedActions shouldContain PrivacyDerivativeAction.METADATA_STRIPPED
            result.report.appliedActions shouldContain PrivacyDerivativeAction.ENCODED
            result.report.failures shouldHaveSize 0
        }

    @Test
    fun `suspendPrivacyDerivative normalizes exif orientation`() =
        runTest(timeout = 30.seconds) {
            val result = testImage(width = 120, height = 80).suspendPrivacyDerivative(
                sourceExif = ExifData(orientation = 6),
            )

            result.image.width shouldBeEqualTo 80
            result.image.height shouldBeEqualTo 120
            result.report.sourceDimensions shouldBeEqualTo PrivacyImageDimensions(width = 120, height = 80)
            result.report.outputDimensions shouldBeEqualTo PrivacyImageDimensions(width = 80, height = 120)
            result.report.appliedActions shouldContain PrivacyDerivativeAction.ORIENTATION_NORMALIZED
        }

    @Test
    fun `suspendPrivacyDerivative rejects images over max pixel budget`() =
        runTest(timeout = 30.seconds) {
            val error = assertFailsWith<IllegalArgumentException> {
                testImage(width = 64, height = 64).suspendPrivacyDerivative(
                    options = PrivacyDerivativeOptions(maxPixels = 1024),
                )
            }

            error.message.shouldNotBeNull()
            error.message shouldContain "maxInputPixels"
        }

    @Test
    fun `suspendPrivacyDerivative creates thumbnail sized derivative`() =
        runTest(timeout = 30.seconds) {
            val result = testImage(width = 160, height = 90).suspendPrivacyDerivative(
                options = PrivacyDerivativeOptions(
                    thumbnailSize = ThumbnailSize(width = 40, height = 30, suffix = "public"),
                ),
            )

            result.image.width shouldBeEqualTo 40
            result.image.height shouldBeEqualTo 30
            result.report.outputDimensions shouldBeEqualTo PrivacyImageDimensions(width = 40, height = 30)
            result.report.appliedActions shouldContain PrivacyDerivativeAction.RESIZED
        }

    @Test
    fun `suspendPrivacyDerivative applies rectangle redaction`() =
        runTest(timeout = 30.seconds) {
            val redaction = PrivacyRedaction(
                region = SensitiveRegion(
                    geometry = SensitiveRegionGeometry.Rectangle(
                        x = 0.25,
                        y = 0.25,
                        width = 0.50,
                        height = 0.50,
                        coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
                    ),
                    id = "face-1",
                ),
                maskColorArgb = Color.BLACK.rgb,
            )

            val result = testImage(width = 100, height = 100, color = Color.WHITE).suspendPrivacyDerivative(
                options = PrivacyDerivativeOptions(redactions = listOf(redaction)),
            )

            Color(result.image.awt().getRGB(50, 50)) shouldBeEqualTo Color.BLACK
            Color(result.image.awt().getRGB(5, 5)) shouldBeEqualTo Color.WHITE
            result.report.redactions shouldHaveSize 1
            result.report.appliedActions shouldContain PrivacyDerivativeAction.REDACTED
        }

    @Test
    fun `processPrivacyDerivatives emits failures when skipFailures is true`() =
        runTest(timeout = 30.seconds) {
            val source = Files.createTempFile("privacy-derivative", ".txt")
            Files.writeString(source, "not an image")

            val failures = mutableListOf<PrivacyDerivativeBatchResult.Failure>()
            val results = flowOf(source)
                .processPrivacyDerivatives(
                    processingOptions = ImageProcessingOptions(
                        parallelism = 1,
                        skipFailures = true,
                        onFailure = {},
                    ),
                    onFailure = { failures += it },
                )
                .toList()

            results.single() shouldBeInstanceOf PrivacyDerivativeBatchResult.Failure::class
            failures.single().stage shouldBeEqualTo PrivacyDerivativeFailureStage.LOAD
        }

    @Test
    fun `processPrivacyDerivatives writes re-encoded derivatives without unsafe metadata defaults`() =
        runTest(timeout = 30.seconds) {
            val source = Files.createTempFile("privacy-derivative", ".png")
            ImageIO.write(testBufferedImage(width = 80, height = 60, color = Color.CYAN), "png", source.toFile())

            val results = flowOf(source)
                .processPrivacyDerivatives(
                    privacyOptions = PrivacyDerivativeOptions(
                        thumbnailSize = ThumbnailSize(width = 20, height = 20, suffix = "safe"),
                    ),
                    processingOptions = ImageProcessingOptions(parallelism = 1),
                )
                .toList()

            val success = results.single() as PrivacyDerivativeBatchResult.Success
            success.source shouldBeEqualTo source
            success.result.bytes.size shouldBeGreaterThan 0
            success.result.report.outputDimensions shouldBeEqualTo PrivacyImageDimensions(width = 20, height = 20)
        }

    private fun testImage(
        width: Int = 120,
        height: Int = 80,
        color: Color = Color(120, 180, 220),
    ): ImmutableImage =
        ImmutableImage.fromAwt(testBufferedImage(width, height, color))

    private fun testBufferedImage(
        width: Int,
        height: Int,
        color: Color,
    ): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = color
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }
        return image
    }
}
