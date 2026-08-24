package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class OcrBenchmarkCorpusV2Test {
    @Test
    fun `v2 manifest verifies image ground truth geometry and receipts`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val fixture = OcrBenchmarkCorpusV2.loadFixture("clean-text-v2-001")

        manifest.generator.replayStatus.shouldBeEqualTo(OcrBenchmarkGeneratorReplayStatus.PENDING)
        fixture.entry.scenario.shouldBeEqualTo(OcrBenchmarkCorpusScenario.CLEAN)
        fixture.entry.expectedOutcome.shouldBeEqualTo(OcrBenchmarkExpectedOutcome.TEXT)
        fixture.entry.provenance.font.name
            .shouldBeEqualTo("Arial Unicode MS")
        fixture.image.width.shouldBeEqualTo(1600)
        fixture.image.height.shouldBeEqualTo(1000)
        fixture.normalizedText.shouldContain("BLUETAPE OCR BENCHMARK")
        fixture.boxes.size.shouldBeEqualTo(6)
        fixture.boxes
            .map(OcrBenchmarkCorpusBox::order)
            .distinct()
            .size
            .shouldBeEqualTo(6)
    }

    @Test
    fun `negative manifest verifies malformed input receipt without treating it as a text fixture`() {
        val negative = OcrBenchmarkCorpusV2.loadNegative("malformed-v2-001")

        negative.expectedReason.shouldBeEqualTo(OcrBenchmarkNegativeReason.DECODE_FAILED)
        negative.sourceType.shouldBeEqualTo(OcrBenchmarkCorpusSourceType.SYNTHETIC)
    }

    @Test
    fun `negative decode receipt rejects a valid image payload`() {
        val manifest = canonicalManifest()
        val validImage = resource("bench/ocr/clean-text.png")
        val invalidNegative =
            manifest
                .replace(
                    "89333cb8edf527776b033f457dcb8f66bfc49047803334364bfb1ab9acb284ab",
                    sha256(validImage)
                ).replace("\"bytes\": 13", "\"bytes\": 155280")
        assertFailsWith<IllegalStateException> {
            OcrBenchmarkCorpusV2.loadNegativeForTest(
                invalidNegative.toByteArray(),
                "malformed-v2-001",
                canonicalResources() + ("bench/ocr-v2/malformed-001.bin" to validImage)
            )
        }
    }

    @Test
    fun `v2 loader rejects traversal and wrong image hash`() {
        val manifest = canonicalManifest()
        val resources = canonicalResources()
        val traversal =
            manifest.replace(
                "bench/ocr/clean-text.png",
                "../secret.png"
            )

        val traversalError =
            assertFailsWith<IllegalArgumentException> {
                OcrBenchmarkCorpusV2.loadFixtureForTest(
                    traversal.toByteArray(),
                    "clean-text-v2-001",
                    resources + ("../secret.png" to resources.getValue("bench/ocr/clean-text.png"))
                )
            }
        traversalError.message.orEmpty().shouldContain("normalized and relative")

        val wrongHash =
            manifest.replace(
                "f036a0ec994554fa6c214fe883603bea79c399c934b4674d84f77737ea0322b8",
                "0".repeat(64)
            )
        assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkCorpusV2.loadFixtureForTest(
                wrongHash.toByteArray(),
                "clean-text-v2-001",
                resources
            )
        }
    }

    @Test
    fun `v2 loader rejects unknown outcome and negative traversal`() {
        val manifest = canonicalManifest()
        val unknownOutcome = manifest.replace("\"TEXT\"", "\"UNKNOWN\"")
        assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkCorpusV2.decodeManifest(unknownOutcome.toByteArray())
        }

        val malformedAsFixture = manifest.replace("\"scenario\": \"clean\"", "\"scenario\": \"malformed\"")
        assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkCorpusV2.decodeManifest(malformedAsFixture.toByteArray())
        }

        val negativeTraversal =
            manifest.replace(
                "bench/ocr-v2/malformed-001.bin",
                "bench/ocr-v2/../malformed.bin"
            )
        assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkCorpusV2.loadNegativeForTest(
                negativeTraversal.toByteArray(),
                "malformed-v2-001",
                canonicalResources()
            )
        }
    }

    @Test
    fun `v2 loader rejects duplicate geometry order`() {
        val manifest = canonicalManifest()
        val resources = canonicalResources()
        val boxesPath = "bench/ocr-v2/clean-text-001.boxes.json"
        val originalHash = "4faafcbbfc2ada2dfe73c7957dbbcec165d53a307fb64c8757ed4b112f827931"
        val invalidBoxes =
            resources
                .getValue(boxesPath)
                .decodeToString()
                .replace("\"order\":5", "\"order\":0")
                .toByteArray()
        val invalidManifest = manifest.replace(originalHash, sha256(invalidBoxes))

        assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkCorpusV2.loadFixtureForTest(
                invalidManifest.toByteArray(),
                "clean-text-v2-001",
                resources + (boxesPath to invalidBoxes)
            )
        }
    }

    @Test
    fun `v2 loader rejects a noncontiguous geometry order`() {
        val manifest = canonicalManifest()
        val resources = canonicalResources()
        val boxesPath = "bench/ocr-v2/clean-text-001.boxes.json"
        val originalHash = "4faafcbbfc2ada2dfe73c7957dbbcec165d53a307fb64c8757ed4b112f827931"
        val invalidBoxes =
            resources
                .getValue(boxesPath)
                .decodeToString()
                .replace("\"order\":5", "\"order\":6")
                .toByteArray()
        val invalidManifest = manifest.replace(originalHash, sha256(invalidBoxes))

        assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkCorpusV2.loadFixtureForTest(
                invalidManifest.toByteArray(),
                "clean-text-v2-001",
                resources + (boxesPath to invalidBoxes)
            )
        }
    }

    private fun canonicalManifest(): String = resource("bench/ocr-v2/manifest.json").decodeToString()

    private fun canonicalResources(): Map<String, ByteArray> =
        listOf(
            "bench/ocr/clean-text.png",
            "bench/ocr-v2/generator.toml",
            "bench/ocr-v2/clean-text-001.txt",
            "bench/ocr-v2/clean-text-001.boxes.json",
            "bench/ocr-v2/ocr-boxes-v1.schema.json",
            "bench/ocr-v2/malformed-001.bin"
        ).associateWith(::resource)

    private fun resource(path: String): ByteArray =
        requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "test resource is missing: $path"
        }.use { input -> input.readBytes() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
