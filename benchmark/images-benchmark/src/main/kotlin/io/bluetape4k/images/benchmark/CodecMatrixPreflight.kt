package io.bluetape4k.images.benchmark

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable as KotlinSerializable

internal const val CODEC_MATRIX_SANITIZED_TEXT_LIMIT: Int = 160

@KotlinSerializable
internal enum class CodecMatrixBackend(
    val selector: String,
    val id: CodecMatrixBackendId,
    val expectedJavaMajor: Int,
    val requiresJniBinary: Boolean,
    val requiresNativeAccess: Boolean,
) {
    @SerialName("java21")
    JAVA21("java21", CodecMatrixBackendId.JAVA21, 21, requiresJniBinary = true, requiresNativeAccess = false),

    @SerialName("java25")
    JAVA25("java25", CodecMatrixBackendId.JAVA25, 25, requiresJniBinary = false, requiresNativeAccess = true),
    ;

    companion object {
        fun parse(selector: String): CodecMatrixBackend =
            entries.singleOrNull { it.selector == selector }
                ?: throw IllegalArgumentException("vips.impl must be exactly java21 or java25")
    }
}

@KotlinSerializable
internal enum class CodecMatrixArchitecture {
    @SerialName("x86_64")
    X86_64,

    @SerialName("arm64")
    ARM64,

    @SerialName("unknown")
    UNKNOWN,
    ;

    companion object {
        fun parse(value: String): CodecMatrixArchitecture = when (value.lowercase()) {
            "amd64", "x86_64", "x64" -> X86_64
            "aarch64", "arm64" -> ARM64
            else -> UNKNOWN
        }
    }
}

internal data class CodecMatrixHostFacts(
    val osName: String,
    val kernelVersion: String,
    val architecture: CodecMatrixArchitecture,
    val cpuModel: String,
)

internal data class CodecMatrixJdkFacts(
    val vendor: String,
    val version: String,
    val major: Int,
) {
    init {
        require(major in 1..100) { "JDK major version is outside the accepted range" }
    }
}

internal data class CodecMatrixGitFacts(
    val sha: String,
    val dirty: Boolean,
) {
    init {
        require(GIT_SHA_REGEX.matches(sha)) { "git SHA must be 40 or 64 lowercase hexadecimal characters" }
    }
}

internal data class CodecMatrixPreflightProbes(
    val host: () -> CodecMatrixHostFacts,
    val jdk: () -> CodecMatrixJdkFacts,
    val jniBinaryArchitecture: (CodecMatrixBackend) -> CodecMatrixArchitecture?,
    val git: () -> CodecMatrixGitFacts,
    val diskAvailableBytes: () -> Long,
    val nativeAccessEnabled: () -> Boolean,
    val loaderPathAvailable: () -> Boolean,
)

@KotlinSerializable
internal data class CodecMatrixPreflightFacts(
    val osName: String? = null,
    val kernelVersion: String? = null,
    val architecture: CodecMatrixArchitecture = CodecMatrixArchitecture.UNKNOWN,
    val cpuModel: String? = null,
    val jdkVendor: String? = null,
    val jdkVersion: String? = null,
    val jdkMajor: Int? = null,
    val jniBinaryArchitecture: CodecMatrixArchitecture? = null,
    val nativeAccessEnabled: Boolean? = null,
    val loaderPathAvailable: Boolean? = null,
    val diskAvailableBytes: Long? = null,
    val gitSha: String? = null,
    val gitDirty: Boolean? = null,
): Serializable {
    init {
        listOfNotNull(osName, kernelVersion, cpuModel, jdkVendor, jdkVersion).forEach { value ->
            value.requireNotBlank("preflight fact")
            require(value.length <= CODEC_MATRIX_SANITIZED_TEXT_LIMIT) { "preflight fact exceeds the text limit" }
        }
        jdkMajor?.let { require(it in 1..100) { "JDK major version is outside the accepted range" } }
        diskAvailableBytes?.let { require(it >= 0L) { "diskAvailableBytes must be non-negative" } }
        gitSha?.let { require(GIT_SHA_REGEX.matches(it)) { "invalid preflight git SHA" } }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixPreflightManifest(
    val schemaVersion: Int = CODEC_MATRIX_SCHEMA_VERSION,
    val runId: CodecMatrixRunId,
    val requestedBackend: CodecMatrixBackendId,
    val requestedSelector: String,
    val status: CodecMatrixCellStatus,
    val reasonCode: CodecMatrixReasonCode,
    val reason: String? = null,
    val facts: CodecMatrixPreflightFacts,
): Serializable {
    init {
        require(schemaVersion == CODEC_MATRIX_SCHEMA_VERSION) { "unsupported schemaVersion: $schemaVersion" }
        require(requestedSelector == "java21" || requestedSelector == "java25") { "invalid requested selector" }
        require(status in PREFLIGHT_STATUSES) { "invalid preflight status: $status" }
        if (status == CodecMatrixCellStatus.ELIGIBLE) {
            require(reasonCode == CodecMatrixReasonCode.NONE) { "eligible preflight must not carry a reason code" }
            require(reason == null) { "eligible preflight must not carry a reason" }
        } else {
            require(reasonCode != CodecMatrixReasonCode.NONE) { "$status preflight requires a reason code" }
            requireNotNull(reason).requireNotBlank("preflight reason")
            require(reason.length <= CODEC_MATRIX_SANITIZED_TEXT_LIMIT) { "preflight reason exceeds the text limit" }
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

internal fun preflightCodecMatrix(
    runId: CodecMatrixRunId,
    backend: CodecMatrixBackend,
    probes: CodecMatrixPreflightProbes,
): CodecMatrixPreflightManifest {
    var facts = CodecMatrixPreflightFacts()
    return try {
        val host = probe("host", probes.host)
        facts = facts.copy(
            osName = sanitizeCodecMatrixText(host.osName),
            kernelVersion = sanitizeCodecMatrixText(host.kernelVersion),
            architecture = host.architecture,
            cpuModel = sanitizeCodecMatrixText(host.cpuModel),
        )
        val jdk = probe("JDK", probes.jdk)
        facts = facts.copy(
            jdkVendor = sanitizeCodecMatrixText(jdk.vendor),
            jdkVersion = sanitizeCodecMatrixText(jdk.version),
            jdkMajor = jdk.major,
        )
        val jniArchitecture = probe("JNI binary") { probes.jniBinaryArchitecture(backend) }
        val nativeAccessEnabled = probe("native access", probes.nativeAccessEnabled)
        val loaderPathAvailable = probe("loader path", probes.loaderPathAvailable)
        val diskAvailableBytes = probe("disk", probes.diskAvailableBytes)
        require(diskAvailableBytes >= 0L) { "disk probe returned a negative value" }
        val git = probe("git", probes.git)
        facts = facts.copy(
            jniBinaryArchitecture = jniArchitecture,
            nativeAccessEnabled = nativeAccessEnabled,
            loaderPathAvailable = loaderPathAvailable,
            diskAvailableBytes = diskAvailableBytes,
            gitSha = git.sha,
            gitDirty = git.dirty,
        )
        evaluateCompatibility(runId, backend, facts)
    } catch (failure: CodecMatrixProbeFailure) {
        preflightResult(
            runId = runId,
            backend = backend,
            status = CodecMatrixCellStatus.ERROR,
            reasonCode = CodecMatrixReasonCode.EVIDENCE_INVALID,
            reason = "${failure.probeName} probe failed",
            facts = facts,
        )
    } catch (_: Exception) {
        preflightResult(
            runId = runId,
            backend = backend,
            status = CodecMatrixCellStatus.ERROR,
            reasonCode = CodecMatrixReasonCode.EVIDENCE_INVALID,
            reason = "preflight validation failed",
            facts = facts,
        )
    }
}

private fun evaluateCompatibility(
    runId: CodecMatrixRunId,
    backend: CodecMatrixBackend,
    facts: CodecMatrixPreflightFacts,
): CodecMatrixPreflightManifest {
    if (facts.jdkMajor != backend.expectedJavaMajor) {
        return incompatible(runId, backend, "selected backend requires JDK ${backend.expectedJavaMajor}", facts)
    }
    if (backend.requiresJniBinary) {
        val binaryArchitecture = facts.jniBinaryArchitecture
            ?: return preflightResult(
                runId,
                backend,
                CodecMatrixCellStatus.N_A,
                CodecMatrixReasonCode.CAPABILITY_UNKNOWN,
                "JNI binary architecture is unavailable",
                facts,
            )
        if (facts.architecture != CodecMatrixArchitecture.UNKNOWN && binaryArchitecture != facts.architecture) {
            return incompatible(runId, backend, "host and JNI binary architectures differ", facts)
        }
    }
    if (backend.requiresNativeAccess && facts.nativeAccessEnabled != true) {
        return incompatible(runId, backend, "FFM native access is not enabled", facts)
    }
    if (facts.loaderPathAvailable != true) {
        return preflightResult(
            runId,
            backend,
            CodecMatrixCellStatus.N_A,
            CodecMatrixReasonCode.CAPABILITY_UNAVAILABLE,
            "libvips loader path is unavailable",
            facts,
        )
    }
    return preflightResult(
        runId,
        backend,
        CodecMatrixCellStatus.ELIGIBLE,
        CodecMatrixReasonCode.NONE,
        reason = null,
        facts,
    )
}

private fun incompatible(
    runId: CodecMatrixRunId,
    backend: CodecMatrixBackend,
    reason: String,
    facts: CodecMatrixPreflightFacts,
): CodecMatrixPreflightManifest = preflightResult(
    runId,
    backend,
    CodecMatrixCellStatus.N_A,
    CodecMatrixReasonCode.HOST_BINARY_INCOMPATIBLE,
    reason,
    facts,
)

private fun preflightResult(
    runId: CodecMatrixRunId,
    backend: CodecMatrixBackend,
    status: CodecMatrixCellStatus,
    reasonCode: CodecMatrixReasonCode,
    reason: String?,
    facts: CodecMatrixPreflightFacts,
): CodecMatrixPreflightManifest = CodecMatrixPreflightManifest(
    runId = runId,
    requestedBackend = backend.id,
    requestedSelector = backend.selector,
    status = status,
    reasonCode = reasonCode,
    reason = reason?.let(::sanitizeCodecMatrixText),
    facts = facts,
)

internal fun sanitizeCodecMatrixText(raw: String): String {
    val sanitized = raw
        .replace(SECRET_VALUE_REGEX, "redacted")
        .replace(WINDOWS_ABSOLUTE_PATH_REGEX, " path ")
        .replace(ABSOLUTE_PATH_REGEX, " path ")
        .replace(CONTROL_CHARACTER_REGEX, " ")
        .replace(MARKDOWN_METACHARACTER_REGEX, "")
        .replace(UNSAFE_CHARACTER_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()
    return (sanitized.ifBlank { "unavailable" }).take(CODEC_MATRIX_SANITIZED_TEXT_LIMIT).trimEnd()
}

private inline fun <T> probe(name: String, block: () -> T): T = try {
    block()
} catch (e: Exception) {
    throw CodecMatrixProbeFailure(name, e)
}

private class CodecMatrixProbeFailure(
    val probeName: String,
    cause: Exception,
): RuntimeException(cause)

private val PREFLIGHT_STATUSES = setOf(
    CodecMatrixCellStatus.ELIGIBLE,
    CodecMatrixCellStatus.N_A,
    CodecMatrixCellStatus.ERROR,
)
private val GIT_SHA_REGEX = Regex("(?:[0-9a-f]{40}|[0-9a-f]{64})")
private val SECRET_VALUE_REGEX = Regex("(?i)(?:password|passwd|token|secret|api[_-]?key)\\s*[:=]\\s*\\S+")
private val WINDOWS_ABSOLUTE_PATH_REGEX = Regex("(?i)(?:[A-Z]:\\\\(?:Users|Temp|Windows)\\\\)\\S+")
private val ABSOLUTE_PATH_REGEX = Regex("(?<![A-Za-z0-9])/(?:Users|home|tmp|private|var|opt)/\\S+")
private val CONTROL_CHARACTER_REGEX = Regex("[\\p{Cc}\\p{Cf}]")
private val MARKDOWN_METACHARACTER_REGEX = Regex("[\\[\\]()*<>`#|!]")
private val UNSAFE_CHARACTER_REGEX = Regex("[^\\p{L}\\p{N} .,:;_=/+\\-]")
private val WHITESPACE_REGEX = Regex("\\s+")
