package io.bluetape4k.images.benchmark

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/** Committed OCR protocol receipt와 run manifest를 CI에서 재검증합니다. */
object OcrBenchmarkProtocolValidateMain {
    private const val MANIFEST_RESOURCE = "bench/ocr-v2/manifest.json"

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4 && args[0] == "--input" && args[2] == "--run-manifest") {
            "Usage: --input <protocol-json> --run-manifest <run-manifest-json>"
        }
        val input = Path.of(args[1]).toAbsolutePath().normalize()
        val runManifestPath = Path.of(args[3]).toAbsolutePath().normalize()
        require(input.isAbsolute && runManifestPath.isAbsolute) {
            "OCR protocol receipt paths must be absolute"
        }
        val protocolBytes = Files.readAllBytes(input)
        require(hasSingleTrailingLf(protocolBytes)) {
            "OCR protocol receipt must end with exactly one LF: $input"
        }
        val runManifestBytes = Files.readAllBytes(runManifestPath)
        require(hasSingleTrailingLf(runManifestBytes)) {
            "OCR protocol run manifest must end with exactly one LF: $runManifestPath"
        }
        val manifestBytes = requireNotNull(resource(MANIFEST_RESOURCE)) {
            "OCR corpus v2 manifest is missing: $MANIFEST_RESOURCE"
        }
        val manifest = OcrBenchmarkCorpusV2.decodeManifest(manifestBytes)
        val manifestSha256 = sha256Hex(manifestBytes)
        val receipt = OcrBenchmarkProtocolReceipt.decode(protocolBytes)
        OcrBenchmarkProtocolReceiptValidator.validate(receipt, manifest)
        require(receipt.manifestSha256 == manifestSha256) {
            "OCR protocol receipt manifest SHA-256 differs from the checked-in manifest"
        }

        val runManifest = kotlinx.serialization.json.Json.parseToJsonElement(runManifestBytes.decodeToString()).jsonObject
        require(runManifest["schemaVersion"]?.jsonPrimitive?.content == "1") {
            "OCR protocol run manifest schema version differs"
        }
        require(runManifest["issue"]?.jsonPrimitive?.content == "565") {
            "OCR protocol run manifest issue differs"
        }
        require(runManifest["runId"]?.jsonPrimitive?.content == receipt.runId) {
            "OCR protocol run manifest run ID differs"
        }
        require(runManifest["status"]?.jsonPrimitive?.content == "MEASURED") {
            "OCR protocol run manifest status must be MEASURED"
        }
        require(runManifest["coverage"]?.jsonPrimitive?.content == "full-corpus") {
            "OCR protocol run manifest coverage must be full-corpus"
        }
        require(runManifest["fixtureManifest"]?.jsonObject?.get("sha256")?.jsonPrimitive?.content == manifestSha256) {
            "OCR protocol run manifest fixture SHA-256 differs"
        }
        val receiptPath = runManifest["protocolReceipt"]?.jsonObject?.get("path")?.jsonPrimitive?.content
            ?: error("OCR protocol run manifest receipt path is missing")
        val receiptSha256 = runManifest["protocolReceipt"]?.jsonObject?.get("sha256")?.jsonPrimitive?.content
            ?: error("OCR protocol run manifest receipt SHA-256 is missing")
        require(runManifestPath.parent.resolve(receiptPath).normalize() == input) {
            "OCR protocol run manifest receipt path differs"
        }
        require(sha256Hex(protocolBytes) == receiptSha256) {
            "OCR protocol run manifest receipt SHA-256 differs"
        }
        println(
            "Validated OCR protocol receipt: ${receipt.rows.size} fixtures, " +
                "CER=${receipt.metrics.summary.cer}, WER=${receipt.metrics.summary.wer}",
        )
    }

    private fun resource(path: String): ByteArray? =
        OcrBenchmarkProtocolValidateMain::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }

    private fun hasSingleTrailingLf(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && bytes.last() == '\n'.code.toByte() &&
            (bytes.size == 1 || bytes[bytes.lastIndex - 1] !in setOf(
                '\n'.code.toByte(),
                '\r'.code.toByte(),
                ' '.code.toByte(),
                '\t'.code.toByte(),
            ))
}
