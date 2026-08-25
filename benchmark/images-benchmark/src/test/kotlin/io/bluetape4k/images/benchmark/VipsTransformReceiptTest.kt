package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import org.junit.jupiter.api.Test

class VipsTransformReceiptTest {

    @Test
    fun `contract receipt covers backend scenario and image size matrix`() {
        val receipt = contractReceipt()

        VipsTransformReceiptValidator.validate(receipt)
        receipt.rows.size shouldBeEqualTo 12
        receipt.rows.map(VipsTransformReceiptRow::identity).distinct().size shouldBeEqualTo 12
        receipt.rows.forEach { row ->
            row.outputSha256.shouldNotBeEmpty()
            row.resource.status shouldBeEqualTo "N/A"
        }
    }

    @Test
    fun `validator rejects duplicate or incomplete scenario rows`() {
        val receipt = contractReceipt()
        val duplicate = receipt.copy(rows = receipt.rows.dropLast(1) + receipt.rows.first())

        val failure = assertFailsWith<IllegalArgumentException> {
            VipsTransformReceiptValidator.validate(duplicate)
        }

        failure.message.shouldContain("scenario rows")
    }

    @Test
    fun `json receipt keeps exactly one trailing newline`() {
        val encoded = VipsTransformReceipt.encode(contractReceipt())

        VipsTransformReceiptValidator.validateJson(encoded)
        assertFailsWith<IllegalArgumentException> {
            VipsTransformReceiptValidator.validateJson(encoded + '\n'.code.toByte())
        }
    }

    private fun contractReceipt(): VipsTransformReceipt {
        val rows = listOf("scrimage", "java21", "java25").flatMap { backend ->
            listOf("chain", "fan-out").flatMap { scenario ->
                listOf("1280x720", "640x480").map { imageSize ->
                    VipsTransformReceiptRow(
                        backend = backend,
                        scenario = scenario,
                        imageSize = imageSize,
                        chainLength = if (scenario == "chain") 3 else 1,
                        fanOut = if (scenario == "fan-out") 4 else 1,
                        operationMix = if (scenario == "chain") {
                            listOf("resize", "thumbnail", "resize")
                        } else {
                            listOf("thumbnail")
                        },
                        status = "N/A",
                        reason = "N/A: native RSS and backend receipt were not measured in hosted CI",
                        outputSha256 = vipsTransformNotMeasuredSha256(),
                        outputCorrectness = "N/A",
                        lifecycle = "each-derived-image-use",
                        closedResources = true,
                        resource = VipsTransformResourceEnvelope(status = "N/A"),
                    )
                }
            }
        }
        return VipsTransformReceipt(
            schemaVersion = 1,
            issue = 582,
            runId = "issue-582-contract-20260825",
            sourceCommit = "0".repeat(40),
            host = mapOf("os" to "N/A", "arch" to "N/A", "jvm" to "Java 25"),
            protocol = VipsTransformReceiptProtocol(
                coldRuns = 1,
                warmupRuns = 2,
                warmRuns = 3,
                imageSizes = listOf("1280x720", "640x480"),
                nativeResourcePolicy = "N/A_ALLOWED",
            ),
            rows = rows,
        )
    }
}
