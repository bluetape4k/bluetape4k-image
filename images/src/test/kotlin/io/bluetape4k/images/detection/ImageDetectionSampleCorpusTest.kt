package io.bluetape4k.images.detection

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.analysis.blurScore
import io.bluetape4k.images.analysis.dominantColors
import io.bluetape4k.images.analysis.readExif
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.probeImageDimensions
import io.bluetape4k.utils.Resourcex
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageDetectionSampleCorpusTest {

    private val detector = DetectorIdentity(
        name = "sample-manifest",
        version = "0.4.0",
        backend = "curated-fixture",
    )
    private val objectMapper = ObjectMapper()

    @Test
    fun `sample manifest matches committed resources`() {
        val samples = loadSamples()

        samples shouldHaveSize 4
        samples.forEach { sample ->
            val bytes = Resourcex.getBytes(sample.resourcePath)
            sha256(bytes) shouldBeEqualTo sample.sha256
            probeImageDimensions(bytes) shouldBeEqualTo sample.expectedDimensions

            val image = immutableImageOf(bytes)
            image.width shouldBeEqualTo sample.expectedDimensions.width
            image.height shouldBeEqualTo sample.expectedDimensions.height
            image.dominantColors(count = 3).shouldNotBeEmpty()
            image.blurScore().score shouldBeGreaterOrEqualTo 0.0
            sample.expectedTags.shouldNotBeEmpty()
            sample.sourcePage.shouldContain("https://commons.wikimedia.org/wiki/File:")
            sample.license.shouldContain("Public domain")
        }
    }

    @Test
    fun `sample annotations exercise detector boundary categories`() {
        val samples = loadSamples()
        val categories = samples
            .flatMap { it.expectedDetections }
            .map { it.category }
            .toSet()

        categories shouldBeEqualTo setOf(
            DetectionCategory.FACE,
            DetectionCategory.PERSON,
            DetectionCategory.OBJECT,
            DetectionCategory.TEXT,
            DetectionCategory.LANDMARK,
        )

        samples.forEach { sample ->
            val image = immutableImageOf(Resourcex.getBytes(sample.resourcePath))
            val results = image.detectRegions(SampleManifestDetector(sample.toDetectionResults()))

            results shouldHaveSize sample.expectedDetections.size
            results.forEach { result ->
                result.detector shouldBeEqualTo detector
                result.pixelBoundingBox(sample.expectedDimensions).shouldNotBeNull()
            }
        }
    }

    @Test
    fun `sample report shows currently extractable signals`() {
        val rows = loadSamples().map { sample ->
            val bytes = Resourcex.getBytes(sample.resourcePath)
            val image = immutableImageOf(bytes)
            val colors = image.dominantColors(count = 3).joinToString(",") { it.hex }
            val blur = image.blurScore()
            val exif = readExif(bytes)

            SampleReportRow(
                id = sample.id,
                dimensions = sample.expectedDimensions,
                tags = sample.expectedTags,
                categories = sample.expectedDetections.map { it.category }.distinct(),
                colors = colors,
                blurScore = blur.score,
                hasExif = exif != io.bluetape4k.images.analysis.ExifData.EMPTY,
            )
        }

        rows.flatMap { it.categories }.toSet() shouldBeEqualTo setOf(
            DetectionCategory.FACE,
            DetectionCategory.PERSON,
            DetectionCategory.OBJECT,
            DetectionCategory.TEXT,
            DetectionCategory.LANDMARK,
        )
        rows.count { it.hasExif } shouldBeGreaterThan 0

        writeReport(rows)
    }

    @Test
    fun `README preview assets show sample detections`() {
        val samples = loadSamples()
        val previewDir = repositoryRoot().resolve("docs/images/detection-samples")

        samples.forEach { sample ->
            val preview = previewDir.resolve("${sample.id}-detections.png")
            val dimensions = requireNotNull(probeImageDimensions(Files.readAllBytes(preview))) {
                "Preview image cannot be decoded: $preview"
            }

            dimensions.width shouldBeGreaterThan 0
            dimensions.height shouldBeGreaterThan 0
        }

        val contactSheet = Files.readAllBytes(previewDir.resolve("sample-detection-results.png"))
        probeImageDimensions(contactSheet) shouldBeEqualTo ImageDimensions(width = 912, height = 1040)
    }

    private fun SampleEntry.toDetectionResults(): List<DetectionResult> =
        expectedDetections.map { expected ->
            DetectionResult(
                label = expected.label,
                category = expected.category,
                confidence = expected.confidence,
                detector = detector,
                region = DetectionRegion(
                    geometry = DetectionRectangleRegion(
                        x = expected.region.x,
                        y = expected.region.y,
                        width = expected.region.width,
                        height = expected.region.height,
                        coordinateSpace = DetectionCoordinateSpace.NORMALIZED,
                    ),
                    metadata = mapOf("sampleId" to id),
                ),
                metadata = mapOf(
                    "sourcePage" to sourcePage,
                    "license" to license,
                ),
            )
        }

    private fun loadSamples(): List<SampleEntry> {
        val manifest = Resourcex.getBytes(MANIFEST_PATH).toString(StandardCharsets.UTF_8)
        return objectMapper.readTree(manifest).map { it.toSampleEntry() }
    }

    private fun JsonNode.toSampleEntry(): SampleEntry {
        return SampleEntry(
            id = string("id"),
            resourcePath = string("resourcePath"),
            sourcePage = string("sourcePage"),
            license = string("license"),
            attribution = string("attribution"),
            sha256 = string("sha256"),
            expectedDimensions = obj("expectedDimensions").toDimensions(),
            expectedTags = array("expectedTags").map { it.asText() },
            expectedDetections = array("expectedDetections").map { it.toExpectedDetection() },
        )
    }

    private fun JsonNode.toExpectedDetection(): ExpectedDetection {
        return ExpectedDetection(
            label = string("label"),
            category = DetectionCategory.valueOf(string("category")),
            confidence = double("confidence"),
            region = obj("region").toRegion(),
        )
    }

    private fun JsonNode.toDimensions(): ImageDimensions =
        ImageDimensions(
            width = int("width"),
            height = int("height"),
        )

    private fun JsonNode.toRegion(): ExpectedRegion =
        ExpectedRegion(
            x = double("x"),
            y = double("y"),
            width = double("width"),
            height = double("height"),
        )

    private fun JsonNode.string(key: String): String =
        required(key).asText()

    private fun JsonNode.int(key: String): Int =
        required(key).asInt()

    private fun JsonNode.double(key: String): Double =
        required(key).asDouble()

    private fun JsonNode.obj(key: String): JsonNode =
        required(key)

    private fun JsonNode.array(key: String): List<JsonNode> =
        required(key).toList()

    private fun JsonNode.required(key: String): JsonNode =
        requireNotNull(get(key)) { "Missing manifest key: $key" }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun writeReport(rows: List<SampleReportRow>) {
        val reportPath = Paths.get(
            System.getProperty("user.dir"),
            "build",
            "reports",
            "detection-samples.md",
        )
        Files.createDirectories(reportPath.parent)
        Files.writeString(reportPath, rows.toMarkdown())
    }

    private fun repositoryRoot(): java.nio.file.Path {
        val userDir = Paths.get(System.getProperty("user.dir"))
        if (Files.exists(userDir.resolve("docs"))) {
            return userDir
        }

        val parent = userDir.parent
        return if (parent != null && Files.exists(parent.resolve("docs"))) {
            parent
        } else {
            userDir
        }
    }

    private fun List<SampleReportRow>.toMarkdown(): String {
        val rows = this
        return buildString {
            appendLine("# Detection Sample Corpus")
            appendLine()
            appendLine("This report is generated from license-cleared test resources.")
            appendLine("It reflects core `images` signals plus manifest-backed detector-boundary annotations.")
            appendLine()
            appendLine("| Sample | Dimensions | Tags | Categories | Dominant colors | Blur score | EXIF |")
            appendLine("|---|---:|---|---|---|---:|---|")
            rows.forEach { row ->
                append("| ")
                append(row.id)
                append(" | ")
                append("${row.dimensions.width}x${row.dimensions.height}")
                append(" | ")
                append(row.tags.joinToString(", "))
                append(" | ")
                append(row.categories.joinToString(", ") { it.name })
                append(" | ")
                append(row.colors)
                append(" | ")
                append("%.2f".format(row.blurScore))
                append(" | ")
                append(if (row.hasExif) "yes" else "no")
                appendLine(" |")
            }
        }
    }

    private class SampleManifestDetector(
        private val results: List<DetectionResult>,
    ) : ImageDetector {
        override fun detect(image: com.sksamuel.scrimage.ImmutableImage, options: DetectionOptions): List<DetectionResult> =
            results.filter(options::accepts)
    }

    private data class SampleEntry(
        val id: String,
        val resourcePath: String,
        val sourcePage: String,
        val license: String,
        val attribution: String,
        val sha256: String,
        val expectedDimensions: ImageDimensions,
        val expectedTags: List<String>,
        val expectedDetections: List<ExpectedDetection>,
    ) : Serializable {
        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    private data class ExpectedDetection(
        val label: String,
        val category: DetectionCategory,
        val confidence: Double,
        val region: ExpectedRegion,
    ) : Serializable {
        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    private data class ExpectedRegion(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
    ) : Serializable {
        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    private data class SampleReportRow(
        val id: String,
        val dimensions: ImageDimensions,
        val tags: List<String>,
        val categories: List<DetectionCategory>,
        val colors: String,
        val blurScore: Double,
        val hasExif: Boolean,
    ) : Serializable {
        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    private companion object {
        private const val MANIFEST_PATH = "detection/samples/metadata.json"
    }
}
