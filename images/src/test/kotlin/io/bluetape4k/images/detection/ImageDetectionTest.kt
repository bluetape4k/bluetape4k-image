package io.bluetape4k.images.detection

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.images.ImageDimensions
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

class ImageDetectionTest {

    private val detector = DetectorIdentity(
        name = "unit-detector",
        version = "test",
        backend = "fake",
    )

    @Test
    fun `detectRegions returns empty list from fake detector`() {
        val results = testImage().detectRegions(StaticImageDetector(emptyList()))

        results.shouldBeEmpty()
    }

    @Test
    fun `detectRegions preserves one face region and detector identity`() {
        val result = DetectionResult(
            label = "face",
            category = DetectionCategory.FACE,
            confidence = 0.96,
            detector = detector,
            rawBackendLabel = "frontal_face",
            classIndex = 1,
            region = DetectionRegion(
                geometry = DetectionRectangleRegion(
                    x = 10.0,
                    y = 12.0,
                    width = 30.0,
                    height = 24.0,
                    coordinateSpace = DetectionCoordinateSpace.PIXEL,
                ),
                metadata = mapOf("source" to "fixture"),
            ),
            metadata = mapOf("classification" to "primary-face"),
        )

        val results = testImage().detectRegions(StaticImageDetector(listOf(result)))

        results shouldHaveSize 1
        val actual = results.single()
        actual.label shouldBeEqualTo "face"
        actual.rawBackendLabel shouldBeEqualTo "frontal_face"
        actual.detector shouldBeEqualTo detector
        actual.pixelBoundingBox(ImageDimensions(width = 100, height = 80)) shouldBeEqualTo
            DetectionBoundingBox(x = 10, y = 12, width = 30, height = 24)
    }

    @Test
    fun `detection options filter multiple results by confidence category and label`() {
        val face = DetectionResult(
            label = "face",
            category = DetectionCategory.FACE,
            confidence = 0.91,
            detector = detector,
        )
        val lowConfidenceObject = DetectionResult(
            label = "cup",
            category = DetectionCategory.OBJECT,
            confidence = 0.42,
            detector = detector,
        )
        val matchedObject = DetectionResult(
            label = "mug",
            category = DetectionCategory.OBJECT,
            confidence = 0.88,
            detector = detector,
            rawBackendLabel = "coffee-cup",
        )

        val results = testImage().detectRegions(
            detector = StaticImageDetector(listOf(face, lowConfidenceObject, matchedObject)),
            options = DetectionOptions(
                minimumConfidence = 0.8,
                categories = setOf(DetectionCategory.OBJECT),
                labels = setOf("coffee-cup"),
            ),
        )

        results shouldHaveSize 1
        results.single().label shouldBeEqualTo "mug"
    }

    @Test
    fun `normalized rectangle converts to pixel bounding box`() {
        val geometry = DetectionRectangleRegion(
            x = 0.1,
            y = 0.25,
            width = 0.5,
            height = 0.25,
            coordinateSpace = DetectionCoordinateSpace.NORMALIZED,
        )

        val box = geometry.toPixelBoundingBox(ImageDimensions(width = 200, height = 120))

        box shouldBeEqualTo DetectionBoundingBox(x = 20, y = 30, width = 100, height = 30)
    }

    @Test
    fun `detector result rejects invalid confidence and blank labels`() {
        val confidenceError = assertFailsWith<IllegalArgumentException> {
            DetectionResult(
                label = "face",
                category = DetectionCategory.FACE,
                confidence = 1.01,
                detector = detector,
            )
        }
        confidenceError.message shouldContain "confidence"

        val labelError = assertFailsWith<IllegalArgumentException> {
            DetectionResult(
                label = " ",
                category = DetectionCategory.OBJECT,
                confidence = 0.5,
                detector = detector,
            )
        }
        labelError.message shouldContain "label"
    }

    @Test
    fun `detectRegions rejects selected region outside image bounds`() {
        val result = DetectionResult(
            label = "face",
            category = DetectionCategory.FACE,
            confidence = 0.9,
            detector = detector,
            region = DetectionRegion(
                geometry = DetectionRectangleRegion(
                    x = 90.0,
                    y = 10.0,
                    width = 20.0,
                    height = 20.0,
                    coordinateSpace = DetectionCoordinateSpace.PIXEL,
                ),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            testImage().detectRegions(StaticImageDetector(listOf(result)))
        }

        error.message shouldContain "imageBounds=100x80"
    }

    @Test
    fun `suspendDetectRegions delegates on supplied dispatcher`() = runTest {
        val calls = AtomicInteger()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fakeDetector = ImageDetector { _, _ ->
            calls.incrementAndGet()
            listOf(
                DetectionResult(
                    label = "person",
                    category = DetectionCategory.PERSON,
                    confidence = 0.83,
                    detector = detector,
                ),
            )
        }

        val deferred = async {
            testImage().suspendDetectRegions(detector = fakeDetector, dispatcher = dispatcher)
        }

        calls.get() shouldBeEqualTo 0
        testScheduler.advanceUntilIdle()
        deferred.await() shouldHaveSize 1
        calls.get() shouldBeEqualTo 1
    }

    @Test
    fun `suspendDetectRegions honors cancellation before detector starts`() = runTest {
        val calls = AtomicInteger()
        val cancelledJob = Job().apply { cancel() }
        val fakeDetector = ImageDetector { _, _ ->
            calls.incrementAndGet()
            emptyList()
        }

        assertFailsWith<CancellationException> {
            withContext(cancelledJob) {
                testImage().suspendDetectRegions(detector = fakeDetector)
            }
        }
        calls.get() shouldBeEqualTo 0
    }

    private fun testImage(width: Int = 100, height: Int = 80): ImmutableImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.BLUE
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }
        return ImmutableImage.wrapAwt(image)
    }

    private class StaticImageDetector(
        private val results: List<DetectionResult>,
    ) : ImageDetector {
        override fun detect(image: ImmutableImage, options: DetectionOptions): List<DetectionResult> = results
    }
}
