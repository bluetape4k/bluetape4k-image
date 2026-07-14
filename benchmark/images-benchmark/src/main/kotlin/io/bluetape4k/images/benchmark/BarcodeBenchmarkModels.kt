package io.bluetape4k.images.benchmark

import java.io.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable as KotlinSerializable

@KotlinSerializable
internal enum class BarcodeBenchmarkScenario(val value: String) {
    @SerialName("qr")
    QR("qr"),

    @SerialName("code-128")
    CODE_128("code-128"),

    @SerialName("no-result")
    NO_RESULT("no-result"),
}

@KotlinSerializable
internal data class BarcodeBenchmarkFixtureManifest(
    val schemaVersion: Int,
    val hashAlgorithm: String,
    val fixtures: List<BarcodeBenchmarkFixtureEntry>,
): Serializable {

    init {
        require(schemaVersion == 1) {
            "unsupported barcode fixture schemaVersion: $schemaVersion"
        }
        require(hashAlgorithm == "SHA-256") {
            "unsupported barcode fixture hashAlgorithm: $hashAlgorithm"
        }
        require(fixtures.map(BarcodeBenchmarkFixtureEntry::scenario) == BarcodeBenchmarkScenario.entries) {
            "barcode fixture scenarios must be exactly ${BarcodeBenchmarkScenario.entries.map { it.value }}"
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class BarcodeBenchmarkFixtureEntry(
    val scenario: BarcodeBenchmarkScenario,
    val resource: String,
    val width: Int,
    val height: Int,
    val sha256: String,
    val expectedText: String? = null,
    val expectedFormat: String? = null,
    val expectEmpty: Boolean = false,
    val provenance: String,
): Serializable {

    init {
        val pathSegments = resource.split('/')
        require(
            resource.isNotEmpty() &&
                    '\\' !in resource &&
                    pathSegments.all { segment -> segment.isNotEmpty() && segment != "." && segment != ".." },
        ) {
            "barcode fixture resource must be normalized and relative: $resource"
        }
        require(resource.startsWith("bench/barcode/")) {
            "barcode fixture resource must stay under bench/barcode/: $resource"
        }
        require(width > 0 && height > 0) {
            "barcode fixture dimensions must be positive"
        }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) {
            "barcode fixture sha256 must be lowercase hexadecimal"
        }
        require(provenance.isNotBlank()) {
            "barcode fixture provenance must not be blank"
        }
        require(expectEmpty.xor(expectedText != null && expectedFormat != null)) {
            "barcode fixture must define exactly one success or empty expectation"
        }
        when (scenario) {
            BarcodeBenchmarkScenario.QR -> require(expectedFormat == "QR_CODE") {
                "QR fixture expectedFormat must be QR_CODE"
            }

            BarcodeBenchmarkScenario.CODE_128 -> require(expectedFormat == "CODE_128") {
                "Code 128 fixture expectedFormat must be CODE_128"
            }

            BarcodeBenchmarkScenario.NO_RESULT -> require(expectEmpty) {
                "no-result fixture must expect an empty result"
            }
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}
