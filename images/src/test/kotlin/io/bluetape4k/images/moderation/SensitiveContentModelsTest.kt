package io.bluetape4k.images.moderation

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.ImageDimensions
import org.junit.jupiter.api.Test

class SensitiveContentModelsTest {

    @Test
    fun `detection preserves stable category and raw backend label`() {
        val detection = SensitiveContentDetection(
            label = "explicit-nudity",
            category = SensitiveContentCategory.EXPLICIT_NUDITY,
            severity = SensitiveContentSeverity.HIGH,
            confidence = 0.92,
            sourceBackend = "unit-detector",
            rawBackendLabel = "nsfw_explicit",
            policyReason = "adult-content",
            region = SensitiveRegion(
                geometry = SensitiveRegionGeometry.Rectangle(
                    x = 10.0,
                    y = 20.0,
                    width = 80.0,
                    height = 60.0,
                    coordinateSpace = SensitiveCoordinateSpace.PIXEL,
                ),
            ),
        )

        detection.label shouldBeEqualTo "explicit-nudity"
        detection.rawBackendLabel shouldBeEqualTo "nsfw_explicit"
        detection.category shouldBeEqualTo SensitiveContentCategory.EXPLICIT_NUDITY
        val region = detection.region.shouldNotBeNull()
        region.geometry.requireWithin(ImageDimensions(width = 200, height = 120)) shouldBeEqualTo region.geometry
    }

    @Test
    fun `detection rejects confidence outside zero to one range`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SensitiveContentDetection(
                label = "violence",
                category = SensitiveContentCategory.VIOLENCE,
                severity = SensitiveContentSeverity.MEDIUM,
                confidence = 1.01,
                sourceBackend = "unit-detector",
                rawBackendLabel = "weapon_score",
            )
        }

        error.message shouldContain "confidence"
    }

    @Test
    fun `detection rejects blank source backend and raw label`() {
        assertFailsWith<IllegalArgumentException> {
            SensitiveContentDetection(
                label = "violence",
                category = SensitiveContentCategory.VIOLENCE,
                severity = SensitiveContentSeverity.MEDIUM,
                confidence = 0.5,
                sourceBackend = " ",
                rawBackendLabel = "weapon_score",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            SensitiveContentDetection(
                label = "violence",
                category = SensitiveContentCategory.VIOLENCE,
                severity = SensitiveContentSeverity.MEDIUM,
                confidence = 0.5,
                sourceBackend = "unit-detector",
                rawBackendLabel = " ",
            )
        }
    }

    @Test
    fun `normalized rectangle rejects coordinates outside unit bounds`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SensitiveRegionGeometry.Rectangle(
                x = 0.8,
                y = 0.1,
                width = 0.3,
                height = 0.2,
                coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
            )
        }

        error.message shouldContain "normalized"
    }

    @Test
    fun `pixel rectangle validates against image dimensions`() {
        val rectangle = SensitiveRegionGeometry.Rectangle(
            x = 100.0,
            y = 20.0,
            width = 80.0,
            height = 60.0,
            coordinateSpace = SensitiveCoordinateSpace.PIXEL,
        )

        val error = assertFailsWith<IllegalArgumentException> {
            rectangle.requireWithin(ImageDimensions(width = 160, height = 120))
        }

        error.message shouldContain "imageBounds=160x120"
    }

    @Test
    fun `polygon must be closed`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SensitiveRegionGeometry.Polygon(
                points = listOf(
                    SensitivePoint(0.1, 0.1),
                    SensitivePoint(0.8, 0.1),
                    SensitivePoint(0.8, 0.8),
                    SensitivePoint(0.2, 0.7),
                ),
                coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
            )
        }

        error.message shouldContain "closed"
    }

    @Test
    fun `polyline must remain open and contain at least two points`() {
        val closedError = assertFailsWith<IllegalArgumentException> {
            SensitiveRegionGeometry.Polyline(
                points = listOf(
                    SensitivePoint(0.1, 0.1),
                    SensitivePoint(0.8, 0.8),
                    SensitivePoint(0.1, 0.1),
                ),
                coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
            )
        }
        closedError.message shouldContain "open"

        val countError = assertFailsWith<IllegalArgumentException> {
            SensitiveRegionGeometry.Polyline(
                points = listOf(SensitivePoint(0.1, 0.1)),
                coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
            )
        }
        countError.message shouldContain "at least two"
    }

    @Test
    fun `raster mask stores optional reference and metadata`() {
        val mask = SensitiveRasterMask(
            width = 64,
            height = 32,
            reference = "s3://redacted/mask.png",
            mediaType = "image/png",
        )

        val geometry = SensitiveRegionGeometry.RasterMask(mask = mask)

        geometry.mask.width shouldBeEqualTo 64
        geometry.mask.reference shouldBeEqualTo "s3://redacted/mask.png"
    }

    @Test
    fun `region metadata rejects blank keys`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SensitiveRegion(
                geometry = SensitiveRegionGeometry.Rectangle(
                    x = 0.1,
                    y = 0.1,
                    width = 0.3,
                    height = 0.2,
                    coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
                ),
                metadata = mapOf(" " to "value"),
            )
        }

        error.message shouldContain "metadata"
    }
}
