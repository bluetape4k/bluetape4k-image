package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeOptions
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.immutableImageOf
import java.security.MessageDigest
import kotlinx.serialization.json.Json

internal object BarcodeBenchmarkFixtures {
    private const val MANIFEST_RESOURCE = "bench/barcode/manifest.json"
    private const val MAX_MANIFEST_BYTES = 65_536
    private const val MAX_FIXTURE_BYTES = 1_048_576
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
    }

    internal fun loadManifest(): BarcodeBenchmarkFixtureManifest =
        decodeManifest(requireResource(MANIFEST_RESOURCE, MAX_MANIFEST_BYTES))

    internal fun decodeManifest(bytes: ByteArray): BarcodeBenchmarkFixtureManifest {
        require(bytes.size in 1..MAX_MANIFEST_BYTES) {
            "barcode fixture manifest byte size is out of bounds"
        }
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }

    internal fun load(scenario: BarcodeBenchmarkScenario): BarcodeBenchmarkFixture =
        load(loadManifest(), scenario, ::classpathResource)

    internal fun loadForTest(
        manifestBytes: ByteArray,
        scenario: BarcodeBenchmarkScenario,
        resources: Map<String, ByteArray>,
    ): BarcodeBenchmarkFixture =
        load(decodeManifest(manifestBytes), scenario, resources::get)

    private fun load(
        manifest: BarcodeBenchmarkFixtureManifest,
        scenario: BarcodeBenchmarkScenario,
        resourceReader: (String) -> ByteArray?,
    ): BarcodeBenchmarkFixture {
        val entry = manifest.fixtures.single { it.scenario == scenario }
        val bytes = requireNotNull(resourceReader(entry.resource)) {
            "barcode fixture resource is missing: ${entry.resource}"
        }
        require(bytes.size in 1..MAX_FIXTURE_BYTES) {
            "barcode fixture byte size is out of bounds: ${entry.resource}"
        }
        require(sha256(bytes) == entry.sha256) {
            "barcode fixture SHA-256 differs: ${entry.resource}"
        }
        val image = immutableImageOf(bytes)
        require(image.width == entry.width && image.height == entry.height) {
            "barcode fixture dimensions differ: ${entry.resource}"
        }
        return BarcodeBenchmarkFixture(entry, image)
    }

    private fun requireResource(resource: String, maxBytes: Int): ByteArray =
        requireNotNull(classpathResource(resource, maxBytes)) {
            "barcode fixture resource is missing: $resource"
        }

    private fun classpathResource(resource: String): ByteArray? =
        classpathResource(resource, MAX_FIXTURE_BYTES)

    private fun classpathResource(resource: String, maxBytes: Int): ByteArray? =
        BarcodeBenchmarkFixtures::class.java.classLoader.getResourceAsStream(resource)?.use { input ->
            input.readNBytes(maxBytes + 1)
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}

internal class BarcodeBenchmarkFixture(
    internal val entry: BarcodeBenchmarkFixtureEntry,
    internal val image: ImmutableImage,
) {
    internal fun options(): BarcodeOptions =
        entry.expectedFormat
            ?.let { format -> BarcodeOptions(formats = setOf(BarcodeFormat.valueOf(format))) }
            ?: BarcodeOptions()

    internal fun verify(results: List<BarcodeResult>) {
        if (entry.expectEmpty) {
            require(results.isEmpty()) {
                "barcode fixture must produce no result: ${entry.scenario.value}"
            }
            return
        }

        val result = results.single()
        require(result.text == entry.expectedText) {
            "barcode fixture payload differs: ${entry.scenario.value}"
        }
        require(result.format == BarcodeFormat.valueOf(requireNotNull(entry.expectedFormat))) {
            "barcode fixture format differs: ${entry.scenario.value}"
        }
    }
}
