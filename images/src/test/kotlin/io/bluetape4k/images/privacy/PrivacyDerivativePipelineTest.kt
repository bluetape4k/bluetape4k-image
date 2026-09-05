package io.bluetape4k.images.privacy

import com.sksamuel.scrimage.AwtImage
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.metadata.ImageMetadata
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.analysis.ExifData
import io.bluetape4k.images.analysis.ImageMetadataReadOptions
import io.bluetape4k.images.analysis.ImageMetadataReadResult
import io.bluetape4k.images.analysis.readImageMetadataReportStrict
import io.bluetape4k.images.batch.ImageProcessingOptions
import io.bluetape4k.images.coroutines.SuspendImageWriter
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.moderation.SensitiveCoordinateSpace
import io.bluetape4k.images.moderation.SensitiveRegion
import io.bluetape4k.images.moderation.SensitiveRegionGeometry
import io.bluetape4k.images.thumbnail.ThumbnailCrop
import io.bluetape4k.images.thumbnail.ThumbnailSize
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.OutputStream
import java.io.Serializable
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
            result.report.metadataVerification.requested shouldContain PrivacyMetadataCategory.XMP
            result.report.metadataVerification.sourcePresent shouldContain PrivacyMetadataCategory.GPS
            result.report.metadataVerification.sourcePresent shouldContain PrivacyMetadataCategory.EXIF
            result.report.metadataVerification.remaining shouldHaveSize 0
            result.report.metadataVerification.verified shouldBeEqualTo true
        }

    @Test
    fun `snapshot restored options can rerun the privacy pipeline`() =
        runTest(timeout = 30.seconds) {
            val restoredOptions = PrivacyDerivativeJackson.decodeOptions(
                PrivacyDerivativeJackson.encodeOptions(PrivacyDerivativeOptions().toSnapshot()),
            ).toOptions()

            val result = testImage().suspendPrivacyDerivative(restoredOptions)

            result.bytes.size shouldBeGreaterThan 0
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
    fun `suspendPrivacyDerivative maps redactions through every exif orientation and resize`() =
        runTest(timeout = 30.seconds) {
            val sourceWidth = 10
            val sourceHeight = 6
            val redaction = pixelRedaction(
                x = 1.0,
                y = 1.0,
                width = 3.0,
                height = 2.0,
                id = "orientation",
                color = Color.RED,
            )

            for (orientation in 2..8) {
                val result = testImage(
                    width = sourceWidth,
                    height = sourceHeight,
                    color = Color.WHITE,
                ).suspendPrivacyDerivative(
                    options = PrivacyDerivativeOptions(
                        thumbnailSize = ThumbnailSize(width = 30, height = 30, suffix = "orientation-$orientation"),
                        outputFormat = PrivacyDerivativeFormat.Png,
                        redactions = listOf(redaction),
                    ),
                    sourceExif = ExifData(orientation = orientation),
                )

                val expected = expectedOrientationRedaction(orientation)
                result.report.redactions.single() shouldBeEqualTo expected
                paintedBounds(result.image, Color.RED) shouldBeEqualTo PaintedBounds(
                    x = expected.x,
                    y = expected.y,
                    width = expected.width,
                    height = expected.height,
                )
            }
        }

    @Test
    fun `suspendPrivacyDerivative clips and drops redactions around smart crop origin`() =
        runTest(timeout = 30.seconds) {
            val clipped = pixelRedaction(
                x = 30.0,
                y = 15.0,
                width = 20.0,
                height = 15.0,
                id = "clipped",
                color = Color.RED,
            )
            val inside = pixelRedaction(
                x = 100.0,
                y = 15.0,
                width = 20.0,
                height = 15.0,
                id = "inside",
                color = Color.BLUE,
            )
            val outside = pixelRedaction(
                x = 0.0,
                y = 15.0,
                width = 20.0,
                height = 15.0,
                id = "outside",
                color = Color.GREEN,
            )

            val result = smartCropSource().suspendPrivacyDerivative(
                options = PrivacyDerivativeOptions(
                    thumbnailSize = ThumbnailSize(width = 40, height = 30, suffix = "smart"),
                    thumbnailCrop = ThumbnailCrop.Smart(),
                    outputFormat = PrivacyDerivativeFormat.Png,
                    redactions = listOf(clipped, inside, outside),
                ),
            )

            result.report.redactions shouldHaveSize 2
            result.report.redactions.single { it.regionId == "clipped" } shouldBeEqualTo AppliedPrivacyRedaction(
                regionId = "clipped",
                mode = PrivacyRedactionMode.SOLID_MASK,
                x = 0,
                y = 5,
                width = 4,
                height = 5,
            )
            result.report.redactions.single { it.regionId == "inside" } shouldBeEqualTo AppliedPrivacyRedaction(
                regionId = "inside",
                mode = PrivacyRedactionMode.SOLID_MASK,
                x = 20,
                y = 5,
                width = 7,
                height = 5,
            )
            paintedBounds(result.image, Color.RED) shouldBeEqualTo PaintedBounds(x = 0, y = 5, width = 4, height = 5)
            paintedBounds(result.image, Color.BLUE) shouldBeEqualTo PaintedBounds(x = 20, y = 5, width = 7, height = 5)
            countPixels(result.image, Color.GREEN) shouldBeEqualTo 0
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
    fun `suspendPrivacyDerivative fails closed when encoded output cannot be verified`() =
        runTest(timeout = 30.seconds) {
            val error = assertFailsWith<PrivacyDerivativeVerificationException> {
                testImage().suspendPrivacyDerivative(
                    options = PrivacyDerivativeOptions(
                        outputFormat = PrivacyDerivativeFormat(
                            writer = MalformedImageWriter,
                            extension = "bin",
                        ),
                    ),
                )
            }

            error.message.shouldNotBeNull()
            error.message shouldContain "metadata verification"
            error.remainingCategories shouldHaveSize 0
        }

    @Test
    fun `suspendPrivacyDerivative still verifies output when no metadata removal is requested`() =
        runTest(timeout = 30.seconds) {
            val error = assertFailsWith<PrivacyDerivativeVerificationException> {
                testImage().suspendPrivacyDerivative(
                    options = PrivacyDerivativeOptions(
                        stripMetadata = false,
                        removeGps = false,
                        normalizeOrientation = false,
                        outputFormat = PrivacyDerivativeFormat(MalformedImageWriter, "bin"),
                    ),
                )
            }

            error.message.shouldNotBeNull()
            error.message shouldContain "metadata verification"
        }

    @Test
    fun `processPrivacyDerivatives reports output inspection failures at verify stage`() =
        runTest(timeout = 30.seconds) {
            val source = Files.createTempFile("privacy-derivative", ".png")
            ImageIO.write(testBufferedImage(width = 24, height = 18, color = Color.ORANGE), "png", source.toFile())

            val result = flowOf(source)
                .processPrivacyDerivatives(
                    privacyOptions = PrivacyDerivativeOptions(
                        outputFormat = PrivacyDerivativeFormat(MalformedImageWriter, "bin"),
                    ),
                    processingOptions = ImageProcessingOptions(
                        parallelism = 1,
                        skipFailures = true,
                        onFailure = {},
                    ),
                )
                .toList()
                .single()

            result shouldBeInstanceOf PrivacyDerivativeBatchResult.Failure::class
            (result as PrivacyDerivativeBatchResult.Failure).stage shouldBeEqualTo PrivacyDerivativeFailureStage.VERIFY
        }

    @Test
    fun `suspendPrivacyDerivative verifies metadata-bearing JPEG fixture for JPEG and PNG outputs`() =
        runTest(timeout = 30.seconds) {
            val sourceBytes = requireNotNull(
                javaClass.getResourceAsStream("/images/filters/debop.jpg"),
            ).use { it.readBytes() }
            val sourceReport = readImageMetadataReportStrict(
                sourceBytes,
                ImageMetadataReadOptions(stripSensitiveMetadata = false),
            ).shouldBeInstanceOf<ImageMetadataReadResult.Success>().report

            sourceReport.containsXmp shouldBeEqualTo true
            sourceReport.containsIptc shouldBeEqualTo true
            sourceReport.containsIccProfile shouldBeEqualTo true
            sourceReport.exif.hasGps shouldBeEqualTo true
            sourceReport.exif.cameraMake.shouldNotBeNull()

            listOf(PrivacyDerivativeFormat.Jpeg, PrivacyDerivativeFormat.Png).forEach { format ->
                val result = immutableImageOf(sourceBytes).suspendPrivacyDerivative(
                    options = PrivacyDerivativeOptions(outputFormat = format),
                    sourceExif = sourceReport.exif,
                    sourceMetadata = sourceReport,
                )

                result.report.metadataVerification.sourcePresent shouldContain PrivacyMetadataCategory.GPS
                result.report.metadataVerification.sourcePresent shouldContain PrivacyMetadataCategory.EXIF
                result.report.metadataVerification.sourcePresent shouldContain PrivacyMetadataCategory.XMP
                result.report.metadataVerification.sourcePresent shouldContain PrivacyMetadataCategory.IPTC
                result.report.metadataVerification.sourcePresent shouldContain PrivacyMetadataCategory.ICC
                result.report.metadataVerification.remaining shouldHaveSize 0
                result.report.metadataVerification.verified shouldBeEqualTo true
            }
        }

    @Test
    fun `suspendPrivacyDerivative rejects requested metadata that the writer preserves`() =
        runTest(timeout = 30.seconds) {
            val sourceBytes = requireNotNull(
                javaClass.getResourceAsStream("/images/filters/debop.jpg"),
            ).use { it.readBytes() }
            val sourceReport = readImageMetadataReportStrict(
                sourceBytes,
                ImageMetadataReadOptions(stripSensitiveMetadata = false),
            ).shouldBeInstanceOf<ImageMetadataReadResult.Success>().report

            val error = assertFailsWith<PrivacyDerivativeVerificationException> {
                immutableImageOf(sourceBytes).suspendPrivacyDerivative(
                    options = PrivacyDerivativeOptions(
                        outputFormat = PrivacyDerivativeFormat(
                            writer = PreservingImageWriter(sourceBytes),
                            extension = "jpg",
                        ),
                    ),
                    sourceExif = sourceReport.exif,
                    sourceMetadata = sourceReport,
                )
            }

            error.remainingCategories shouldContain PrivacyMetadataCategory.GPS
            error.remainingCategories shouldContain PrivacyMetadataCategory.EXIF
            error.remainingCategories shouldContain PrivacyMetadataCategory.XMP
            error.remainingCategories shouldContain PrivacyMetadataCategory.IPTC
            error.remainingCategories shouldContain PrivacyMetadataCategory.ICC
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

    private fun pixelRedaction(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        id: String,
        color: Color,
    ): PrivacyRedaction = PrivacyRedaction(
        region = SensitiveRegion(
            geometry = SensitiveRegionGeometry.Rectangle(
                x = x,
                y = y,
                width = width,
                height = height,
                coordinateSpace = SensitiveCoordinateSpace.PIXEL,
            ),
            id = id,
        ),
        maskColorArgb = color.rgb,
    )

    private fun smartCropSource(): ImmutableImage {
        val image = BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color.BLACK
            graphics.fillRect(157, 0, 1, image.height)
        } finally {
            graphics.dispose()
        }
        return ImmutableImage.fromAwt(image)
    }

    private fun expectedOrientationRedaction(orientation: Int): AppliedPrivacyRedaction {
        val transformed = when (orientation) {
            2 -> PaintedBounds(x = 6, y = 1, width = 3, height = 2)
            3 -> PaintedBounds(x = 6, y = 3, width = 3, height = 2)
            4 -> PaintedBounds(x = 1, y = 3, width = 3, height = 2)
            5 -> PaintedBounds(x = 1, y = 1, width = 2, height = 3)
            6 -> PaintedBounds(x = 3, y = 1, width = 2, height = 3)
            7 -> PaintedBounds(x = 3, y = 6, width = 2, height = 3)
            8 -> PaintedBounds(x = 1, y = 6, width = 2, height = 3)
            else -> error("Unsupported test orientation: $orientation")
        }
        val orientedWidth = if (orientation >= 5) 6 else 10
        val orientedHeight = if (orientation >= 5) 10 else 6
        return AppliedPrivacyRedaction(
            regionId = "orientation",
            mode = PrivacyRedactionMode.SOLID_MASK,
            x = transformed.x * 30 / orientedWidth,
            y = transformed.y * 30 / orientedHeight,
            width = transformed.width * 30 / orientedWidth,
            height = transformed.height * 30 / orientedHeight,
        )
    }

    private fun paintedBounds(image: ImmutableImage, color: Color): PaintedBounds {
        val pixels = (0 until image.height).flatMap { y ->
            (0 until image.width).mapNotNull { x ->
                if (image.awt().getRGB(x, y) == color.rgb) x to y else null
            }
        }
        require(pixels.isNotEmpty()) { "Expected ${color.rgb} pixels in ${image.width}x${image.height} image" }
        val xs = pixels.map { it.first }
        val ys = pixels.map { it.second }
        val minX = xs.min()
        val minY = ys.min()
        return PaintedBounds(
            x = minX,
            y = minY,
            width = xs.max() - minX + 1,
            height = ys.max() - minY + 1,
        )
    }

    private fun countPixels(image: ImmutableImage, color: Color): Int =
        (0 until image.height).sumOf { y ->
            (0 until image.width).count { x -> image.awt().getRGB(x, y) == color.rgb }
        }

    private data class PaintedBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) : Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    private object MalformedImageWriter : SuspendImageWriter {
        override fun write(image: AwtImage, metadata: ImageMetadata, out: OutputStream) {
            out.write(byteArrayOf(0x00, 0x01, 0x02))
        }
    }

    private class PreservingImageWriter(
        private val bytes: ByteArray,
    ) : SuspendImageWriter {
        override fun write(image: AwtImage, metadata: ImageMetadata, out: OutputStream) {
            out.write(bytes)
        }
    }
}
