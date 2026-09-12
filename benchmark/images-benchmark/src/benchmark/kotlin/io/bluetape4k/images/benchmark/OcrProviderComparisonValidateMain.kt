package io.bluetape4k.images.benchmark

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/** Issue #544 provider 비교 receipt와 실행 manifest를 CI에서 재검증합니다. */
object OcrProviderComparisonValidateMain {
    private const val MANIFEST_RESOURCE = "bench/ocr-v2/manifest.json"
    private val commitPattern = Regex("[0-9a-f]{40}")

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4 && args[0] == "--input" && args[2] == "--run-manifest") {
            "Usage: --input <comparison-receipt.json> --run-manifest <run-manifest.json>"
        }
        val input = regularAbsolutePath(args[1], "OCR comparison receipt")
        val runManifestPath = regularAbsolutePath(args[3], "OCR comparison run manifest")
        val receiptBytes = Files.readAllBytes(input)
        val runManifestBytes = Files.readAllBytes(runManifestPath)
        require(hasSingleTrailingLf(receiptBytes)) {
            "OCR comparison receipt must end with exactly one LF: $input"
        }
        require(hasSingleTrailingLf(runManifestBytes)) {
            "OCR comparison run manifest must end with exactly one LF: $runManifestPath"
        }

        val manifestBytes = requireNotNull(resource(MANIFEST_RESOURCE)) {
            "OCR corpus v2 manifest is missing: $MANIFEST_RESOURCE"
        }
        val manifest = OcrBenchmarkCorpusV2.decodeManifest(manifestBytes)
        val manifestSha256 = sha256Hex(manifestBytes)
        val receipt = OcrProviderComparisonReceipt.decode(receiptBytes)
        OcrProviderComparisonReceiptValidator.validate(receipt, manifest)
        require(receipt.manifestSha256 == manifestSha256) {
            "OCR comparison receipt manifest SHA-256 differs from the checked-in manifest"
        }

        val runManifest = Json.parseToJsonElement(runManifestBytes.decodeToString()).jsonObject
        require(runManifest["schemaVersion"]?.jsonPrimitive?.content == "1") {
            "OCR comparison run manifest schema version differs"
        }
        require(runManifest["kind"]?.jsonPrimitive?.content == "ocr-provider-comparison") {
            "OCR comparison run manifest kind differs"
        }
        require(runManifest["issue"]?.jsonPrimitive?.content == "544") {
            "OCR comparison run manifest issue differs"
        }
        require(runManifest["sourceCommit"]?.jsonPrimitive?.content?.matches(commitPattern) == true) {
            "OCR comparison run manifest source commit is invalid"
        }
        require(runManifest["status"]?.jsonPrimitive?.content == "PASS") {
            "OCR comparison run manifest status must be PASS"
        }
        require(runManifest["manifestSha256"]?.jsonPrimitive?.content == manifestSha256) {
            "OCR comparison run manifest manifest SHA-256 differs"
        }
        require(runManifest["platform"]?.jsonObject?.get("host")?.jsonPrimitive?.content == "linux/amd64") {
            "OCR comparison run manifest host platform must be linux/amd64"
        }
        val receiptPath = runManifest["receipt"]?.jsonObject?.get("path")?.jsonPrimitive?.content
            ?: error("OCR comparison run manifest receipt path is missing")
        val receiptSha256 = runManifest["receipt"]?.jsonObject?.get("sha256")?.jsonPrimitive?.content
            ?: error("OCR comparison run manifest receipt SHA-256 is missing")
        require(runManifestPath.parent.resolve(receiptPath).normalize() == input) {
            "OCR comparison run manifest receipt path differs"
        }
        require(sha256Hex(receiptBytes) == receiptSha256) {
            "OCR comparison run manifest receipt SHA-256 differs"
        }
        require(runManifest["service"]?.jsonObject?.get("cleanupVerified")?.jsonPrimitive?.content == "true") {
            "OCR comparison service cleanup must be verified"
        }
        println(
            "Validated OCR provider comparison receipt: ${receipt.providers.size} providers, " +
                "${receipt.comparison?.comparedFixtureCount ?: 0} fixtures",
        )
    }

    private fun regularAbsolutePath(value: String, label: String): Path {
        val path = Path.of(value).toAbsolutePath().normalize()
        require(path.isAbsolute && Files.isRegularFile(path) && !Files.isSymbolicLink(path)) {
            "$label is missing: $path"
        }
        return path
    }

    private fun resource(path: String): ByteArray? =
        OcrProviderComparisonValidateMain::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }

    private fun hasSingleTrailingLf(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && bytes.last() == '\n'.code.toByte() &&
            (bytes.size == 1 || bytes[bytes.lastIndex - 1] !in setOf(
                '\n'.code.toByte(),
                '\r'.code.toByte(),
                ' '.code.toByte(),
                '\t'.code.toByte(),
            ))
}
