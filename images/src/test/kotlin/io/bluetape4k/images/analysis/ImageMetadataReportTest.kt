package io.bluetape4k.images.analysis

import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.icc.IccDirectory
import com.drew.metadata.iptc.IptcDirectory
import com.drew.metadata.jpeg.JpegDirectory
import com.drew.metadata.xmp.XmpDirectory
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO

class ImageMetadataReportTest {

    @Test
    fun `readImageMetadataReport returns empty report for malformed bytes`() {
        val report = readImageMetadataReport(ByteArray(64) { 0x7F.toByte() })

        report shouldBeEqualTo ImageMetadataReport.EMPTY
    }

    @Test
    fun `readImageMetadataReport enforces byte array size guard`() {
        val oversized = ByteArray(ImageMetadataReadOptions.DEFAULT_MAX_BYTES + 1)

        assertFailsWith<IllegalArgumentException> {
            readImageMetadataReport(oversized)
        }
    }

    @Test
    fun `readImageMetadataReport extracts dimensions from image bytes without metadata`() {
        val report = readImageMetadataReport(noMetadataJpeg(width = 12, height = 8))

        report.dimensions shouldBeEqualTo ImageMetadataDimensions(width = 12, height = 8)
        report.hasAnyMetadata.shouldBeTrue()
        report.hasSensitiveMetadata.shouldBeFalse()
        report.containsXmp.shouldBeFalse()
        report.containsIptc.shouldBeFalse()
        report.containsIccProfile.shouldBeFalse()
    }

    @Test
    fun `InputStream readImageMetadataReport does not close caller owned stream`() {
        val stream = CloseTrackingInputStream(noMetadataJpeg())

        val report = stream.readImageMetadataReport()

        report.dimensions shouldBeEqualTo ImageMetadataDimensions(width = 10, height = 10)
        stream.closed.shouldBeFalse()
    }

    @Test
    fun `Path readImageMetadataReport extracts dimensions with size guard`() {
        val path = Files.createTempFile("metadata-report-", ".jpg")
        try {
            Files.write(path, noMetadataJpeg(width = 14, height = 9))

            val report = path.readImageMetadataReport()

            report.dimensions shouldBeEqualTo ImageMetadataDimensions(width = 14, height = 9)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `metadata report maps XMP IPTC ICC orientation and dimensions`() {
        val report = syntheticMetadata().toImageMetadataReport()

        report.dimensions shouldBeEqualTo ImageMetadataDimensions(width = 640, height = 480)
        report.orientation shouldBeEqualTo 6
        report.containsXmp.shouldBeTrue()
        report.containsIptc.shouldBeTrue()
        report.containsIccProfile.shouldBeTrue()
        report.iccProfile?.byteCount shouldBeEqualTo 3144
        report.iccProfile?.colorSpace shouldBeEqualTo "RGB"
        report.exif.cameraMake shouldBeEqualTo "Canon"
    }

    @Test
    fun `metadata report keeps diagnostic tags bounded only when requested`() {
        val report = syntheticMetadata().toImageMetadataReport(
            ImageMetadataReadOptions(includeDiagnosticTags = true, maxDiagnosticValueLength = 12),
        )

        report.diagnostics.shouldNotBeEmpty()
        report.diagnostics
            .flatMap { it.tags.values }
            .all { it.length <= 12 }
            .shouldBeTrue()

        val safeReport = syntheticMetadata().toImageMetadataReport()
        safeReport.diagnostics.size shouldBeEqualTo 0
    }

    @Test
    fun `withoutSensitiveMetadata strips GPS values while preserving safe report fields`() {
        val report = ImageMetadataReport(
            exif = ExifData(
                gpsLatitude = 37.5665,
                gpsLongitude = 126.9780,
                gpsAltitude = 38.0,
                cameraMake = "Canon",
            ),
            dimensions = ImageMetadataDimensions(width = 640, height = 480),
            orientation = 6,
            containsXmp = true,
        )

        val safeReport = report.withoutSensitiveMetadata()

        safeReport.exif.hasGps.shouldBeFalse()
        safeReport.exif.gpsAltitude.shouldBeNull()
        safeReport.exif.cameraMake shouldBeEqualTo "Canon"
        safeReport.dimensions shouldBeEqualTo ImageMetadataDimensions(width = 640, height = 480)
        safeReport.orientation shouldBeEqualTo 6
        safeReport.containsXmp.shouldBeTrue()
    }

    @Test
    fun `backend header fields are bounded and privacy filtered`() {
        val publicReport = ImageMetadataReport.EMPTY.withBackendHeaderFields(
            sourceBackend = "vips",
            headerFields = mapOf("interpretation" to "scRGB HDR", "gainmap" to "present"),
        )

        publicReport.hdrHints.hasHdrHint.shouldBeTrue()
        publicReport.hdrHints.hasGainMapHint.shouldBeTrue()
        publicReport.diagnostics.size shouldBeEqualTo 0

        val report = ImageMetadataReport.EMPTY.withBackendHeaderFields(
            sourceBackend = "vips",
            headerFields = mapOf(
                "interpretation" to "scRGB HDR display profile",
                "gainmap" to "embedded gain map payload hint",
                "source-path" to "/tmp/private/photo.jpg",
                "native-pointer" to "0xDEADBEEF",
                "gps-latitude" to "37.5665",
                "raw-bytes" to "not exposed",
            ),
            options = ImageMetadataReadOptions(includeDiagnosticTags = true, maxDiagnosticValueLength = 10),
        )

        val tags = report.diagnostics.single().tags

        report.hdrHints.hasHdrHint.shouldBeTrue()
        report.hdrHints.hasGainMapHint.shouldBeTrue()
        tags.containsKey("interpretation").shouldBeTrue()
        tags.containsKey("gainmap").shouldBeTrue()
        tags.containsKey("source-path").shouldBeFalse()
        tags.containsKey("native-pointer").shouldBeFalse()
        tags.containsKey("gps-latitude").shouldBeFalse()
        tags.containsKey("raw-bytes").shouldBeFalse()
        tags.values.all { it.length <= 10 }.shouldBeTrue()
    }

    private fun noMetadataJpeg(width: Int = 10, height: Int = 10): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(100, 150, 200)
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "jpg", output)
            output.toByteArray()
        }
    }

    private fun syntheticMetadata(): Metadata =
        Metadata().apply {
            addDirectory(
                JpegDirectory().apply {
                    setInt(JpegDirectory.TAG_IMAGE_WIDTH, 640)
                    setInt(JpegDirectory.TAG_IMAGE_HEIGHT, 480)
                },
            )
            addDirectory(
                ExifIFD0Directory().apply {
                    setInt(ExifIFD0Directory.TAG_ORIENTATION, 6)
                    setString(ExifIFD0Directory.TAG_MAKE, "Canon")
                },
            )
            addDirectory(
                XmpDirectory().apply {
                    setInt(XmpDirectory.TAG_XMP_VALUE_COUNT, 2)
                },
            )
            addDirectory(
                IptcDirectory().apply {
                    setString(IptcDirectory.TAG_OBJECT_NAME, "press-safe-title")
                },
            )
            addDirectory(
                IccDirectory().apply {
                    setInt(IccDirectory.TAG_PROFILE_BYTE_COUNT, 3144)
                    setString(IccDirectory.TAG_COLOR_SPACE, "RGB ")
                    setString(IccDirectory.TAG_PROFILE_VERSION, "4.3.0")
                },
            )
        }

    private class CloseTrackingInputStream(bytes: ByteArray): ByteArrayInputStream(bytes) {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
