package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.immutableImageOf
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable as KotlinxSerializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun requireCorpusRelativePath(path: String, field: String) {
    val segments = path.split('/')
    require(
        path.isNotBlank() && !path.startsWith('/') && '\\' !in path &&
            segments.firstOrNull()?.contains(':') != true &&
            segments.all { segment -> segment.isNotEmpty() && segment != "." && segment != ".." },
    ) { "$field must be normalized and relative: $path" }
}

private fun isCorpusSha256(value: String): Boolean = value.matches(Regex("[0-9a-f]{64}"))

/**
 * Manifest v2 loader for the OCR corpus contract.
 *
 * Image, text, geometry, generator, and negative input receipts are verified
 * before a fixture is handed to a benchmark. The loader is intentionally
 * internal: it prepares a provider-neutral benchmark boundary without
 * changing the production OCR API or adding a provider dependency.
 */
internal object OcrBenchmarkCorpusV2 {
    private const val MANIFEST_RESOURCE = "bench/ocr-v2/manifest.json"
    private const val MAX_MANIFEST_BYTES = 256_000
    private const val MAX_RESOURCE_BYTES = 5 * 1024 * 1024
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
    }

    fun loadManifest(): OcrBenchmarkCorpusManifest =
        decodeManifest(requireResource(MANIFEST_RESOURCE, MAX_MANIFEST_BYTES))

    fun loadFixture(fixtureId: String): OcrBenchmarkCorpusFixture =
        loadFixture(loadManifest(), fixtureId, ::classpathResource)

    fun loadNegative(fixtureId: String): OcrBenchmarkNegativeFixtureReceipt {
        val manifest = loadManifest()
        val entry = manifest.negatives.single { it.fixtureId == fixtureId }
        verifyBytes(entry.path, entry.bytes, entry.sha256, ::classpathResource)
        return entry
    }

    internal fun decodeManifest(bytes: ByteArray): OcrBenchmarkCorpusManifest {
        require(bytes.size in 1..MAX_MANIFEST_BYTES) {
            "OCR corpus v2 manifest byte size is out of bounds"
        }
        return json.decodeFromString(bytes.decodeUtf8())
    }

    internal fun loadFixtureForTest(
        manifestBytes: ByteArray,
        fixtureId: String,
        resources: Map<String, ByteArray>,
    ): OcrBenchmarkCorpusFixture =
        loadFixture(decodeManifest(manifestBytes), fixtureId, resources::get)

    internal fun loadNegativeForTest(
        manifestBytes: ByteArray,
        fixtureId: String,
        resources: Map<String, ByteArray>,
    ): OcrBenchmarkNegativeFixtureReceipt {
        val manifest = decodeManifest(manifestBytes)
        val entry = manifest.negatives.single { it.fixtureId == fixtureId }
        verifyBytes(entry.path, entry.bytes, entry.sha256, resources::get)
        return entry
    }

    private fun loadFixture(
        manifest: OcrBenchmarkCorpusManifest,
        fixtureId: String,
        resourceReader: (String) -> ByteArray?,
    ): OcrBenchmarkCorpusFixture {
        val entry = manifest.fixtures.single { it.fixtureId == fixtureId }
        verifyBytes(
            manifest.generator.config.path,
            manifest.generator.config.bytes,
            manifest.generator.config.sha256,
            resourceReader,
        )
        validateLicenses(entry.licenses)
        validateFontReceipt(entry.provenance.font)

        val imageBytes = verifyBytes(
            entry.resource.path,
            entry.resource.bytes,
            entry.resource.sha256,
            resourceReader,
        )
        val image = immutableImageOf(imageBytes)
        require(image.width == entry.resource.width && image.height == entry.resource.height) {
            "OCR corpus v2 image dimensions differ: ${entry.fixtureId}"
        }

        val textBytes = verifyBytes(
            entry.groundTruth.text.path,
            entry.groundTruth.text.bytes,
            entry.groundTruth.text.sha256,
            resourceReader,
        )
        val normalizedText = decodeGroundTruthText(textBytes, entry.groundTruth.text)

        val schemaBytes = verifyBytes(
            entry.groundTruth.boxes.schemaResource.path,
            entry.groundTruth.boxes.schemaResource.bytes,
            entry.groundTruth.boxes.schemaResource.sha256,
            resourceReader,
        )
        validateBoxSchema(schemaBytes)

        val boxesBytes = verifyBytes(
            entry.groundTruth.boxes.path,
            entry.groundTruth.boxes.bytes,
            entry.groundTruth.boxes.sha256,
            resourceReader,
        )
        val boxes = decodeBoxes(boxesBytes)
        validateBoxes(boxes, entry.resource.width, entry.resource.height)
        validateExpectedOutcome(entry.expectedOutcome, normalizedText, boxes)

        return OcrBenchmarkCorpusFixture(entry, image, normalizedText, boxes.entries)
    }

    private fun decodeGroundTruthText(
        bytes: ByteArray,
        receipt: OcrBenchmarkTextReceipt,
    ): String {
        require(receipt.encoding == OcrBenchmarkTextEncoding.UTF_8) {
            "OCR ground truth encoding must be UTF-8"
        }
        require(receipt.normalization == OcrBenchmarkTextNormalization.NFC_LF) {
            "OCR ground truth normalization must be NFC+LF"
        }
        val decoded = bytes.decodeUtf8()
        require('\r' !in decoded) { "OCR ground truth must use LF line endings" }
        val normalized = Normalizer.normalize(decoded, Normalizer.Form.NFC)
        return when (receipt.whitespacePolicy) {
            OcrBenchmarkWhitespacePolicy.PRESERVE -> normalized
            OcrBenchmarkWhitespacePolicy.COLLAPSE -> normalized.lines().joinToString("\n") { line ->
                line.trim().replace(Regex("[ \\t]+"), " ")
            }
        }
    }

    private fun decodeBoxes(bytes: ByteArray): OcrBenchmarkBoxesDocument =
        json.decodeFromString(bytes.decodeUtf8())

    private fun validateBoxSchema(bytes: ByteArray) {
        val root = json.parseToJsonElement(bytes.decodeUtf8()).jsonObject
        require(root["type"]?.jsonPrimitive?.content == "object") {
            "OCR geometry schema type must be object"
        }
        require(root["additionalProperties"]?.jsonPrimitive?.content == "false") {
            "OCR geometry schema must reject unknown fields"
        }
        val required = root["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
        require(required == setOf("schema", "coordinateSpace", "entries")) {
            "OCR geometry schema required fields differ"
        }
        val properties = root["properties"]?.jsonObject ?: error("OCR geometry schema properties are missing")
        require(properties["schema"]?.jsonObject?.get("const")?.jsonPrimitive?.content == "ocr-boxes-v1") {
            "OCR geometry schema name differs"
        }
        require(properties["coordinateSpace"]?.jsonObject?.get("const")?.jsonPrimitive?.content == "pixel") {
            "OCR geometry coordinate space differs"
        }
    }

    private fun validateBoxes(
        boxes: OcrBenchmarkBoxesDocument,
        imageWidth: Int,
        imageHeight: Int,
    ) {
        require(boxes.schema == "ocr-boxes-v1") { "OCR geometry schema name differs" }
        require(boxes.coordinateSpace == "pixel") { "OCR geometry coordinate space must be pixel" }
        require(boxes.entries.map(OcrBenchmarkCorpusBox::boxId).distinct().size == boxes.entries.size) {
            "OCR geometry boxId values must be unique"
        }
        require(boxes.entries.map(OcrBenchmarkCorpusBox::order).distinct().size == boxes.entries.size) {
            "OCR geometry order values must be unique"
        }
        boxes.entries.forEach { box ->
            require(box.boxId.isNotBlank() && box.text.isNotBlank()) {
                "OCR geometry boxId and text must not be blank"
            }
            require(box.pageIndex == 0) { "OCR geometry pageIndex must be zero for single-page fixtures" }
            require(box.x >= 0 && box.y >= 0 && box.order >= 0) {
                "OCR geometry coordinates and order must be non-negative"
            }
            require(box.width > 0 && box.height > 0) {
                "OCR geometry dimensions must be positive"
            }
            require(box.width <= imageWidth - box.x && box.height <= imageHeight - box.y) {
                "OCR geometry box must stay inside the image bounds"
            }
        }
    }

    private fun validateExpectedOutcome(
        outcome: OcrBenchmarkExpectedOutcome,
        normalizedText: String,
        boxes: OcrBenchmarkBoxesDocument,
    ) {
        when (outcome) {
            OcrBenchmarkExpectedOutcome.TEXT -> require(normalizedText.isNotBlank()) {
                "TEXT OCR fixture must have non-blank ground truth"
            }

            OcrBenchmarkExpectedOutcome.EMPTY -> {
                require(normalizedText.isBlank() && boxes.entries.isEmpty()) {
                    "EMPTY OCR fixture must have blank text and no geometry"
                }
            }

            OcrBenchmarkExpectedOutcome.ERROR -> Unit
        }
    }

    private fun validateLicenses(licenses: List<OcrBenchmarkLicenseReceipt>) {
        require(licenses.isNotEmpty()) { "OCR corpus fixture licenses must not be empty" }
        licenses.forEach { license ->
            require(license.component.isNotBlank() && license.spdx.isNotBlank()) {
                "OCR corpus license component and SPDX expression must not be blank"
            }
            require(license.sourceUrl.startsWith("https://")) {
                "OCR corpus license sourceUrl must use HTTPS"
            }
            requireCorpusRelativePath(license.noticePath, "license noticePath")
        }
    }

    private fun validateFontReceipt(font: OcrBenchmarkFontReceipt) {
        require(font.name.isNotBlank() && font.sourceUrl.startsWith("https://")) {
            "OCR corpus font receipt is incomplete"
        }
        require(font.bytes > 0 && isCorpusSha256(font.sha256) && font.spdx.isNotBlank()) {
            "OCR corpus font receipt must pin bytes, SHA-256, and SPDX"
        }
        requireCorpusRelativePath(font.noticePath, "font noticePath")
    }

    private fun verifyBytes(
        path: String,
        expectedBytes: Long,
        expectedSha256: String,
        resourceReader: (String) -> ByteArray?,
    ): ByteArray {
        requireCorpusRelativePath(path, "resource path")
        require(expectedBytes in 0..MAX_RESOURCE_BYTES.toLong()) {
            "OCR corpus resource byte size is out of bounds: $path"
        }
        require(isCorpusSha256(expectedSha256)) { "OCR corpus resource SHA-256 is invalid: $path" }
        val bytes = requireNotNull(resourceReader(path)) { "OCR corpus resource is missing: $path" }
        require(bytes.size.toLong() == expectedBytes) { "OCR corpus resource byte size differs: $path" }
        require(sha256(bytes) == expectedSha256) { "OCR corpus resource SHA-256 differs: $path" }
        return bytes
    }

    private fun requireResource(resource: String, maxBytes: Int): ByteArray =
        requireNotNull(classpathResource(resource, maxBytes)) {
            "OCR corpus resource is missing: $resource"
        }

    private fun classpathResource(resource: String): ByteArray? =
        classpathResource(resource, MAX_RESOURCE_BYTES)

    private fun classpathResource(resource: String, maxBytes: Int): ByteArray? =
        OcrBenchmarkCorpusV2::class.java.classLoader.getResourceAsStream(resource)?.use { input ->
            input.readNBytes(maxBytes + 1)
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun ByteArray.decodeUtf8(): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(this)).toString()
    }
}

/** 검증된 v2 OCR fixture와 정규화된 정답·geometry를 benchmark에 전달한다. */
internal class OcrBenchmarkCorpusFixture(
    val entry: OcrBenchmarkCorpusFixtureEntry,
    val image: ImmutableImage,
    val normalizedText: String,
    val boxes: List<OcrBenchmarkCorpusBox>,
)

@KotlinxSerializable
internal enum class OcrBenchmarkCorpusScenario(val value: String) {
    @SerialName("clean") CLEAN("clean"),
    @SerialName("low-resolution") LOW_RESOLUTION("low-resolution"),
    @SerialName("noisy") NOISY("noisy"),
    @SerialName("rotated") ROTATED("rotated"),
    @SerialName("table") TABLE("table"),
    @SerialName("multi-column") MULTI_COLUMN("multi-column"),
    @SerialName("multilingual") MULTILINGUAL("multilingual"),
    @SerialName("valid-blank") VALID_BLANK("valid-blank"),
    @SerialName("malformed") MALFORMED("malformed"),
}

@KotlinxSerializable
internal enum class OcrBenchmarkCorpusSourceType(val value: String) {
    @SerialName("synthetic") SYNTHETIC("synthetic"),
    @SerialName("public") PUBLIC("public"),
}

@KotlinxSerializable
internal enum class OcrBenchmarkExpectedOutcome {
    TEXT,
    EMPTY,
    ERROR,
}

@KotlinxSerializable
internal enum class OcrBenchmarkNegativeReason {
    DECODE_FAILED,
    INPUT_BYTES_EXCEEDED,
    PIXELS_EXCEEDED,
    SIDE_EXCEEDED,
    TRANSPORT_BYTES_EXCEEDED,
    LIMIT_PROFILE_MISMATCH,
}

@KotlinxSerializable
internal enum class OcrBenchmarkTextEncoding {
    @SerialName("UTF-8") UTF_8,
}

@KotlinxSerializable
internal enum class OcrBenchmarkTextNormalization {
    @SerialName("NFC+LF") NFC_LF,
}

@KotlinxSerializable
internal enum class OcrBenchmarkWhitespacePolicy {
    PRESERVE,
    COLLAPSE,
}

@KotlinxSerializable
internal data class OcrBenchmarkCorpusManifest(
    val schemaVersion: Int,
    val hashAlgorithm: String,
    val generator: OcrBenchmarkGeneratorReceipt,
    val fixtures: List<OcrBenchmarkCorpusFixtureEntry>,
    val negatives: List<OcrBenchmarkNegativeFixtureReceipt> = emptyList(),
) : Serializable {
    init {
        require(schemaVersion == 2) { "unsupported OCR corpus schemaVersion: $schemaVersion" }
        require(hashAlgorithm == "SHA-256") { "unsupported OCR corpus hashAlgorithm: $hashAlgorithm" }
        require(fixtures.isNotEmpty()) { "OCR corpus fixtures must not be empty" }
        val fixtureIds = fixtures.map(OcrBenchmarkCorpusFixtureEntry::fixtureId)
        require(fixtureIds.distinct().size == fixtureIds.size) { "OCR corpus fixtureId values must be unique" }
        val negativeIds = negatives.map(OcrBenchmarkNegativeFixtureReceipt::fixtureId)
        require(negativeIds.distinct().size == negativeIds.size) {
            "OCR corpus negative fixtureId values must be unique"
        }
        require(fixtureIds.intersect(negativeIds.toSet()).isEmpty()) {
            "OCR corpus fixture and negative fixtureId values must not overlap"
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkGeneratorReceipt(
    val name: String,
    val version: String,
    val command: String,
    val seed: Long,
    val config: OcrBenchmarkResourceReceipt,
) : Serializable {
    init {
        require(name.isNotBlank() && version.isNotBlank() && command.isNotBlank()) {
            "OCR corpus generator receipt is incomplete"
        }
        require(seed >= 0) { "OCR corpus generator seed must be non-negative" }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkCorpusFixtureEntry(
    val fixtureId: String,
    val scenario: OcrBenchmarkCorpusScenario,
    val sourceType: OcrBenchmarkCorpusSourceType,
    val resource: OcrBenchmarkImageReceipt,
    val languages: List<String>,
    val transformations: List<String>,
    val groundTruth: OcrBenchmarkGroundTruthReceipt,
    val licenses: List<OcrBenchmarkLicenseReceipt>,
    val provenance: OcrBenchmarkProvenanceReceipt,
    val expectedOutcome: OcrBenchmarkExpectedOutcome,
) : Serializable {
    init {
        require(fixtureId.matches(Regex("[a-z0-9][a-z0-9-]{2,80}"))) {
            "OCR corpus fixtureId is invalid: $fixtureId"
        }
        require(scenario != OcrBenchmarkCorpusScenario.MALFORMED) {
            "malformed OCR inputs must be declared in the negative manifest"
        }
        require(languages.isNotEmpty() && languages.all(String::isNotBlank)) {
            "OCR corpus languages must not be empty"
        }
        require(transformations.isNotEmpty() && transformations.all(String::isNotBlank)) {
            "OCR corpus transformations must not be empty"
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkImageReceipt(
    val path: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val sha256: String,
) : Serializable {
    init {
        requireCorpusRelativePath(path, "image path")
        require(bytes > 0 && isCorpusSha256(sha256)) { "OCR corpus image receipt is incomplete" }
        require(width > 0 && height > 0) { "OCR corpus image dimensions must be positive" }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkResourceReceipt(
    val path: String,
    val bytes: Long,
    val sha256: String,
    val encoding: OcrBenchmarkTextEncoding,
    val normalization: OcrBenchmarkTextNormalization,
    val spdx: String,
    val noticePath: String,
) : Serializable {
    init {
        requireCorpusRelativePath(path, "resource path")
        require(bytes >= 0 && isCorpusSha256(sha256) && spdx.isNotBlank()) {
            "OCR corpus resource receipt is incomplete"
        }
        requireCorpusRelativePath(noticePath, "resource noticePath")
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkGroundTruthReceipt(
    val text: OcrBenchmarkTextReceipt,
    val boxes: OcrBenchmarkBoxesReceipt,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkTextReceipt(
    val path: String,
    val bytes: Long,
    val sha256: String,
    val encoding: OcrBenchmarkTextEncoding,
    val normalization: OcrBenchmarkTextNormalization,
    val whitespacePolicy: OcrBenchmarkWhitespacePolicy,
) : Serializable {
    init {
        requireCorpusRelativePath(path, "ground truth text path")
        require(bytes >= 0 && isCorpusSha256(sha256)) {
            "OCR ground truth text receipt is incomplete"
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkBoxesReceipt(
    val path: String,
    val bytes: Long,
    val sha256: String,
    val schema: String,
    val schemaResource: OcrBenchmarkSchemaReceipt,
    val coordinateSpace: String,
    val order: String,
) : Serializable {
    init {
        requireCorpusRelativePath(path, "ground truth boxes path")
        require(bytes >= 0 && isCorpusSha256(sha256)) {
            "OCR ground truth boxes receipt is incomplete"
        }
        require(schema == "ocr-boxes-v1") { "OCR geometry schema name differs" }
        require(coordinateSpace == "pixel") { "OCR geometry coordinate space differs" }
        require(order == "reading-order") { "OCR geometry order must be reading-order" }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkSchemaReceipt(
    val path: String,
    val bytes: Long,
    val sha256: String,
) : Serializable {
    init {
        requireCorpusRelativePath(path, "geometry schema path")
        require(bytes > 0 && isCorpusSha256(sha256)) {
            "OCR geometry schema receipt is incomplete"
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkLicenseReceipt(
    val component: String,
    val spdx: String,
    val sourceUrl: String,
    val noticePath: String,
) : Serializable {
    init {
        require(component.isNotBlank() && spdx.isNotBlank() && sourceUrl.startsWith("https://")) {
            "OCR corpus license receipt is incomplete"
        }
        requireCorpusRelativePath(noticePath, "license noticePath")
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkProvenanceReceipt(
    val font: OcrBenchmarkFontReceipt,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkFontReceipt(
    val name: String,
    val sourceUrl: String,
    val bytes: Long,
    val sha256: String,
    val spdx: String,
    val noticePath: String,
) : Serializable {
    init {
        require(name.isNotBlank() && sourceUrl.startsWith("https://")) {
            "OCR corpus font receipt is incomplete"
        }
        require(bytes > 0 && isCorpusSha256(sha256) && spdx.isNotBlank()) {
            "OCR corpus font receipt must pin bytes, SHA-256, and SPDX"
        }
        requireCorpusRelativePath(noticePath, "font noticePath")
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkNegativeFixtureReceipt(
    val fixtureId: String,
    val path: String,
    val bytes: Long,
    val sha256: String,
    val expectedReason: OcrBenchmarkNegativeReason,
    val sourceType: OcrBenchmarkCorpusSourceType,
) : Serializable {
    init {
        require(fixtureId.matches(Regex("[a-z0-9][a-z0-9-]{2,80}"))) {
            "OCR corpus negative fixtureId is invalid: $fixtureId"
        }
        requireCorpusRelativePath(path, "negative fixture path")
        require(bytes >= 0 && isCorpusSha256(sha256)) {
            "OCR corpus negative fixture receipt is incomplete"
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkBoxesDocument(
    val schema: String,
    val coordinateSpace: String,
    val entries: List<OcrBenchmarkCorpusBox>,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkCorpusBox(
    val boxId: String,
    val pageIndex: Int,
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val order: Int,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
