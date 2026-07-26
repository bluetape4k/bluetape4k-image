package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.immutableImageOf
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object OcrBenchmarkFixtures {
    private const val MANIFEST_RESOURCE = "bench/ocr/manifest.json"
    private const val MAX_MANIFEST_BYTES = 65_536
    private const val MAX_FIXTURE_BYTES = 2_500_000
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
    }

    fun loadManifest(): OcrBenchmarkFixtureManifest =
        decodeManifest(requireResource(MANIFEST_RESOURCE, MAX_MANIFEST_BYTES))

    fun load(scenario: OcrBenchmarkScenario): OcrBenchmarkFixture =
        load(loadManifest(), scenario, ::classpathResource)

    internal fun decodeManifest(bytes: ByteArray): OcrBenchmarkFixtureManifest {
        require(bytes.size in 1..MAX_MANIFEST_BYTES) {
            "OCR fixture manifest byte size is out of bounds"
        }
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }

    internal fun loadForTest(
        manifestBytes: ByteArray,
        scenario: OcrBenchmarkScenario,
        resources: Map<String, ByteArray>,
    ): OcrBenchmarkFixture =
        load(decodeManifest(manifestBytes), scenario, resources::get)

    private fun load(
        manifest: OcrBenchmarkFixtureManifest,
        scenario: OcrBenchmarkScenario,
        resourceReader: (String) -> ByteArray?,
    ): OcrBenchmarkFixture {
        val entry = manifest.fixtures.single { it.scenario == scenario }
        val bytes = requireNotNull(resourceReader(entry.resource)) {
            "OCR fixture resource is missing: ${entry.resource}"
        }
        require(bytes.size in 1..MAX_FIXTURE_BYTES) {
            "OCR fixture byte size is out of bounds: ${entry.resource}"
        }
        require(sha256(bytes) == entry.sha256) {
            "OCR fixture SHA-256 differs: ${entry.resource}"
        }
        val image = immutableImageOf(bytes)
        require(image.width == entry.width && image.height == entry.height) {
            "OCR fixture dimensions differ: ${entry.resource}"
        }
        return OcrBenchmarkFixture(entry, image)
    }

    private fun requireResource(resource: String, maxBytes: Int): ByteArray =
        requireNotNull(classpathResource(resource, maxBytes)) {
            "OCR fixture resource is missing: $resource"
        }

    private fun classpathResource(resource: String): ByteArray? =
        classpathResource(resource, MAX_FIXTURE_BYTES)

    private fun classpathResource(resource: String, maxBytes: Int): ByteArray? =
        OcrBenchmarkFixtures::class.java.classLoader.getResourceAsStream(resource)?.use { input ->
            input.readNBytes(maxBytes + 1)
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}

internal class OcrBenchmarkFixture(
    val entry: OcrBenchmarkFixtureEntry,
    val image: ImmutableImage,
) {
    fun verify(text: String) {
        val normalized = text.uppercase()
        entry.expectedTokens.forEach { token ->
            require(normalized.contains(token.uppercase())) {
                "OCR fixture token is missing for ${entry.scenario.value}: $token"
            }
        }
    }
}

internal object OcrBenchmarkEnvironment {
    private val tessdataCandidates = listOf(
        "/opt/homebrew/share/tessdata",
        "/usr/local/share/tessdata",
        "/usr/share/tesseract-ocr/5/tessdata",
        "/usr/share/tesseract-ocr/4.00/tessdata",
        "/usr/share/tessdata",
    )

    fun requireTessdataPath(): String =
        System.getenv("TESSDATA_PREFIX")
            ?.takeIf(String::isNotBlank)
            ?.takeIf { Files.isDirectory(Path.of(it)) }
            ?: tessdataCandidates.firstOrNull { candidate -> Files.isDirectory(Path.of(candidate)) }
            ?: error(
                "Tesseract tessdata is unavailable. Set TESSDATA_PREFIX or install traineddata under a supported path.",
            )

    fun requireLanguages(required: List<String>) {
        val output = try {
            val process = ProcessBuilder("tesseract", "--list-langs")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().readText()
            require(process.waitFor() == 0) { "tesseract --list-langs failed: $text" }
            text
        } catch (cause: Exception) {
            throw IllegalStateException(
                "Tesseract host prerequisite is unavailable. Install tesseract and required traineddata packages.",
                cause,
            )
        }
        val available = output.lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
        val missing = required.filterNot(available::contains)
        require(missing.isEmpty()) {
            "Tesseract traineddata is missing for ${missing.joinToString()}. Install it before running the OCR benchmark."
        }
    }
}

@Serializable
internal enum class OcrBenchmarkScenario(val value: String) {
    @SerialName("clean-text")
    CLEAN_TEXT("clean-text"),

    @SerialName("noisy-scan")
    NOISY_SCAN("noisy-scan"),

    @SerialName("rotated-document")
    ROTATED_DOCUMENT("rotated-document"),

    @SerialName("multilingual-text")
    MULTILINGUAL_TEXT("multilingual-text"),
}

@Serializable
internal data class OcrBenchmarkFixtureManifest(
    val schemaVersion: Int,
    val hashAlgorithm: String,
    val fixtures: List<OcrBenchmarkFixtureEntry>,
) {
    init {
        require(schemaVersion == 1) { "unsupported OCR fixture schemaVersion: $schemaVersion" }
        require(hashAlgorithm == "SHA-256") { "unsupported OCR fixture hashAlgorithm: $hashAlgorithm" }
        require(fixtures.map(OcrBenchmarkFixtureEntry::scenario) == OcrBenchmarkScenario.entries) {
            "OCR fixture scenarios must be exactly ${OcrBenchmarkScenario.entries.map { it.value }}"
        }
    }
}

@Serializable
internal data class OcrBenchmarkFixtureEntry(
    val scenario: OcrBenchmarkScenario,
    val resource: String,
    val width: Int,
    val height: Int,
    val sha256: String,
    val languages: List<String>,
    val expectedTokens: List<String>,
    val provenance: String,
) {
    init {
        val pathSegments = resource.split('/')
        require(
            resource.isNotEmpty() && '\\' !in resource &&
                    pathSegments.all { segment -> segment.isNotEmpty() && segment != "." && segment != ".." },
        ) { "OCR fixture resource must be normalized and relative: $resource" }
        require(resource.startsWith("bench/ocr/")) { "OCR fixture resource must stay under bench/ocr/: $resource" }
        require(width > 0 && height > 0) { "OCR fixture dimensions must be positive" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "OCR fixture sha256 must be lowercase hexadecimal" }
        require(languages.isNotEmpty() && languages.all(String::isNotBlank)) { "OCR fixture languages must not be blank" }
        require(expectedTokens.isNotEmpty() && expectedTokens.all(String::isNotBlank)) {
            "OCR fixture expectedTokens must not be blank"
        }
        require(provenance.isNotBlank()) { "OCR fixture provenance must not be blank" }
        if (scenario == OcrBenchmarkScenario.MULTILINGUAL_TEXT) {
            require(languages == listOf("eng", "kor", "jpn")) {
                "multilingual OCR fixture must require eng, kor, and jpn"
            }
        } else {
            require(languages == listOf("eng")) { "English OCR fixtures must require eng" }
        }
    }
}
