package io.bluetape4k.images.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * FFM 파생 이미지 benchmark의 cold/warm 및 resource envelope를 고정하는 receipt입니다.
 *
 * native RSS/peak allocation을 측정할 수 없는 CI에서는 해당 metric을 null로 두고
 * [VipsTransformResourceEnvelope.status]를 `N/A`로 기록합니다. 이는 측정 성공으로
 * 간주되지 않으며, 후속 macOS native run에서 같은 manifest를 재사용해야 합니다.
 */
@Serializable
data class VipsTransformResourceEnvelope(
    val status: String,
    val nativeRssBeforeBytes: Long? = null,
    val nativeRssPeakBytes: Long? = null,
    val nativeAllocationBytesPerOp: Long? = null,
) {
    init {
        require(status == "MEASURED" || status == "N/A") {
            "Vips transform resource status must be MEASURED or N/A"
        }
        if (status == "MEASURED") {
            require(nativeRssBeforeBytes != null && nativeRssBeforeBytes > 0) {
                "Measured native RSS before value is missing"
            }
            require(nativeRssPeakBytes != null && nativeRssPeakBytes >= nativeRssBeforeBytes) {
                "Measured native RSS peak value is invalid"
            }
            require(nativeAllocationBytesPerOp != null && nativeAllocationBytesPerOp > 0) {
                "Measured native allocation value is missing"
            }
        } else {
            require(nativeRssBeforeBytes == null && nativeRssPeakBytes == null && nativeAllocationBytesPerOp == null) {
                "N/A native resource metrics must be null"
            }
        }
    }
}

@Serializable
data class VipsTransformReceiptRow(
    val backend: String,
    val scenario: String,
    val imageSize: String,
    val chainLength: Int,
    val fanOut: Int,
    val operationMix: List<String>,
    val status: String,
    val reason: String,
    val coldLatencyNanos: Long? = null,
    val warmLatencyNanos: Long? = null,
    val throughputOpsPerSecond: Double? = null,
    val outputBytes: Long? = null,
    val outputSha256: String,
    val outputCorrectness: String,
    val lifecycle: String,
    val closedResources: Boolean,
    val resource: VipsTransformResourceEnvelope,
) {
    init {
        require(backend in setOf("scrimage", "java21", "java25")) { "Unknown Vips transform backend: $backend" }
        require(scenario in setOf("chain", "fan-out")) { "Unknown Vips transform scenario: $scenario" }
        require(imageSize.matches(Regex("[0-9]+x[0-9]+"))) { "Invalid Vips transform image size: $imageSize" }
        require(chainLength > 0 && fanOut > 0) { "Vips transform shape must be positive" }
        require(operationMix.isNotEmpty() && operationMix.all(String::isNotBlank)) {
            "Vips transform operation mix is incomplete"
        }
        require(status == "MEASURED" || status == "N/A") {
            "Vips transform row status must be MEASURED or N/A"
        }
        require(reason.isNotBlank()) { "Vips transform row reason is required" }
        require(outputSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Vips transform output SHA-256 is invalid"
        }
        require(outputCorrectness == "PASS" || outputCorrectness == "N/A") {
            "Vips transform output correctness must be PASS or N/A"
        }
        require(lifecycle == "each-derived-image-use") { "Vips transform lifecycle policy differs" }
        require(closedResources) { "Vips transform receipt must close every derived resource" }
        if (status == "MEASURED") {
            require(coldLatencyNanos != null && coldLatencyNanos > 0) {
                "Measured cold latency is missing"
            }
            require(warmLatencyNanos != null && warmLatencyNanos > 0) {
                "Measured warm latency is missing"
            }
            require(throughputOpsPerSecond != null && throughputOpsPerSecond.isFinite() && throughputOpsPerSecond > 0.0) {
                "Measured throughput is missing"
            }
            require(outputBytes != null && outputBytes > 0) { "Measured output size is missing" }
            require(outputCorrectness == "PASS") { "Measured output correctness must pass" }
        } else {
            require(coldLatencyNanos == null && warmLatencyNanos == null && throughputOpsPerSecond == null) {
                "N/A latency metrics must be null"
            }
            require(outputBytes == null && outputCorrectness == "N/A") {
                "N/A output metrics must be null/N/A"
            }
        }
    }

    val identity: String
        get() = "$backend|$scenario|$imageSize"
}

@Serializable
data class VipsTransformReceiptProtocol(
    val coldRuns: Int,
    val warmupRuns: Int,
    val warmRuns: Int,
    val imageSizes: List<String>,
    val nativeResourcePolicy: String,
) {
    init {
        require(coldRuns == 1) { "Vips transform coldRuns must be 1" }
        require(warmupRuns > 0 && warmRuns > 0) { "Vips transform warm runs must be positive" }
        require(imageSizes.toSet().size == imageSizes.size && imageSizes.isNotEmpty()) {
            "Vips transform image sizes must be unique"
        }
        require(nativeResourcePolicy == "N/A_ALLOWED") {
            "Vips transform native resource policy differs"
        }
    }
}

@Serializable
data class VipsTransformReceipt(
    val schemaVersion: Int,
    val issue: Int,
    val runId: String,
    val sourceCommit: String,
    val host: Map<String, String>,
    val protocol: VipsTransformReceiptProtocol,
    val rows: List<VipsTransformReceiptRow>,
) {
    companion object {
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
            isLenient = false
        }

        fun encode(receipt: VipsTransformReceipt): ByteArray =
            (json.encodeToString(receipt) + "\n").encodeToByteArray()

        fun decode(bytes: ByteArray): VipsTransformReceipt =
            json.decodeFromString(bytes.decodeToString())
    }
}

object VipsTransformReceiptValidator {
    fun validate(receipt: VipsTransformReceipt) {
        require(receipt.schemaVersion == 1) { "Vips transform receipt schema version differs" }
        require(receipt.issue == 582) { "Vips transform receipt issue differs" }
        require(receipt.runId.matches(Regex("[a-z0-9][a-z0-9._-]{7,79}"))) {
            "Vips transform receipt run ID is invalid"
        }
        require(receipt.sourceCommit.matches(Regex("[0-9a-f]{40}"))) {
            "Vips transform receipt source commit is invalid"
        }
        require(setOf("os", "arch", "jvm").all { !receipt.host[it].isNullOrBlank() }) {
            "Vips transform receipt host envelope is incomplete"
        }
        require(receipt.rows.isNotEmpty()) { "Vips transform receipt has no rows" }
        val identities = receipt.rows.map(VipsTransformReceiptRow::identity)
        require(identities.toSet().size == identities.size) {
            "Vips transform receipt contains duplicate scenario rows"
        }
        val expectedBackends = setOf("scrimage", "java21", "java25")
        val expectedScenarios = setOf("chain", "fan-out")
        val expectedIdentities = expectedBackends.flatMap { backend ->
            expectedScenarios.flatMap { scenario ->
                receipt.protocol.imageSizes.map { imageSize -> "$backend|$scenario|$imageSize" }
            }
        }.toSet()
        require(identities.toSet() == expectedIdentities) {
            "Vips transform receipt scenario coverage differs"
        }
        receipt.rows.forEach { row ->
            require(row.status == "MEASURED" || row.status == "N/A")
            if (row.status == "N/A") {
                require(row.reason.contains("N/A", ignoreCase = true)) {
                    "N/A Vips transform row must state why it was not measured: ${row.identity}"
                }
            }
        }
    }

    fun validateJson(bytes: ByteArray) {
        require(hasSingleTrailingLf(bytes)) { "Vips transform receipt must end with exactly one LF" }
        validate(VipsTransformReceipt.decode(bytes))
    }

    private fun hasSingleTrailingLf(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && bytes.last() == '\n'.code.toByte() &&
            (bytes.size == 1 || bytes[bytes.lastIndex - 1] !in setOf(
                '\n'.code.toByte(),
                '\r'.code.toByte(),
                ' '.code.toByte(),
                '\t'.code.toByte(),
            ))
}

fun vipsTransformNotMeasuredSha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest("NOT_MEASURED".encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
