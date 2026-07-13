package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CodecMatrixRuntimeTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `backend selector is an exact allowlist`() {
        CodecMatrixBackend.parse("java21").shouldBeEqualTo(CodecMatrixBackend.JAVA21)
        CodecMatrixBackend.parse("java25").shouldBeEqualTo(CodecMatrixBackend.JAVA25)

        listOf("jni", "JAVA21", " java21", "java21 ", "", "java26").forEach { selector ->
            assertFailsWith<IllegalArgumentException> {
                CodecMatrixBackend.parse(selector)
            }
        }
    }

    @Test
    fun `JNI binary headers map to canonical architectures`() {
        val elfX86 = ByteArray(64).apply {
            this[0] = 0x7F
            "ELF".toByteArray().copyInto(this, destinationOffset = 1)
            this[5] = 1
            this[18] = 0x3E
        }
        val machoArm = ByteArray(64).apply {
            byteArrayOf(0xCF.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte())
                .copyInto(this, destinationOffset = 0)
            byteArrayOf(0x0C, 0x00, 0x00, 0x01).copyInto(this, destinationOffset = 4)
        }
        val peX86 = ByteArray(256).apply {
            this[0] = 'M'.code.toByte()
            this[1] = 'Z'.code.toByte()
            this[0x3C] = 0x80.toByte()
            "PE\u0000\u0000".toByteArray().copyInto(this, destinationOffset = 0x80)
            this[0x84] = 0x64
            this[0x85] = 0x86.toByte()
        }

        codecMatrixBinaryArchitecture(elfX86).shouldBeEqualTo(CodecMatrixArchitecture.X86_64)
        codecMatrixBinaryArchitecture(machoArm).shouldBeEqualTo(CodecMatrixArchitecture.ARM64)
        codecMatrixBinaryArchitecture(peX86).shouldBeEqualTo(CodecMatrixArchitecture.X86_64)
    }

    @Test
    fun `arm64 host and x86 JNI binary becomes N A without native initialization`() {
        val result = preflightCodecMatrix(
            runId = CodecMatrixRunId("preflight-arm-0001"),
            backend = CodecMatrixBackend.JAVA21,
            probes = probes(
                hostArchitecture = CodecMatrixArchitecture.ARM64,
                jdkMajor = 21,
                jniArchitecture = CodecMatrixArchitecture.X86_64,
            ),
        )

        result.status.shouldBeEqualTo(CodecMatrixCellStatus.N_A)
        result.reasonCode.shouldBeEqualTo(CodecMatrixReasonCode.HOST_BINARY_INCOMPATIBLE)
        result.facts.jniBinaryArchitecture.shouldBeEqualTo(CodecMatrixArchitecture.X86_64)
    }

    @Test
    fun `wrong JDK and missing FFM native access become structured N A`() {
        preflightCodecMatrix(
            runId = CodecMatrixRunId("preflight-jdk-0001"),
            backend = CodecMatrixBackend.JAVA25,
            probes = probes(jdkMajor = 21),
        ).status.shouldBeEqualTo(CodecMatrixCellStatus.N_A)

        val missingNativeAccess = preflightCodecMatrix(
            runId = CodecMatrixRunId("preflight-ffm-0001"),
            backend = CodecMatrixBackend.JAVA25,
            probes = probes(jdkMajor = 25, nativeAccessEnabled = false),
        )
        missingNativeAccess.status.shouldBeEqualTo(CodecMatrixCellStatus.N_A)
        missingNativeAccess.reasonCode.shouldBeEqualTo(CodecMatrixReasonCode.HOST_BINARY_INCOMPATIBLE)
    }

    @Test
    fun `eligible preflight records allowlisted dirty git and host facts`() {
        val result = preflightCodecMatrix(
            runId = CodecMatrixRunId("preflight-ok-0001"),
            backend = CodecMatrixBackend.JAVA25,
            probes = probes(jdkMajor = 25, gitDirty = true),
        )

        result.status.shouldBeEqualTo(CodecMatrixCellStatus.ELIGIBLE)
        result.reasonCode.shouldBeEqualTo(CodecMatrixReasonCode.NONE)
        result.facts.gitDirty.shouldBeEqualTo(true)
        result.facts.architecture.shouldBeEqualTo(CodecMatrixArchitecture.ARM64)
        result.facts.diskAvailableBytes.shouldBeEqualTo(8L * 1024 * 1024 * 1024)
    }

    @Test
    fun `probe failures become fixed sanitized ERROR evidence`() {
        val result = preflightCodecMatrix(
            runId = CodecMatrixRunId("preflight-error-0001"),
            backend = CodecMatrixBackend.JAVA25,
            probes = probes(jdkMajor = 25).copy(
                git = { error("token=super-secret /Users/alice/work/repo\n*[boom](x)") },
            ),
        )

        result.status.shouldBeEqualTo(CodecMatrixCellStatus.ERROR)
        result.reasonCode.shouldBeEqualTo(CodecMatrixReasonCode.EVIDENCE_INVALID)
        result.reason.shouldBeEqualTo("git probe failed")
        check("super-secret" !in requireNotNull(result.reason))
    }

    @Test
    fun `sanitizer removes paths controls markdown secrets and bounds output`() {
        val raw = "token=super-secret /Users/alice/work/repo C:\\Users\\alice\\secret\n*[boom](x) <tag> " +
                "z".repeat(400)
        val sanitized = sanitizeCodecMatrixText(raw)

        check("super-secret" !in sanitized)
        check("/Users/" !in sanitized)
        check("C:" !in sanitized)
        check('\n' !in sanitized)
        listOf("*", "[", "]", "(", ")", "<", ">").forEach { character ->
            check(character !in sanitized)
        }
        check(sanitized.length <= CODEC_MATRIX_SANITIZED_TEXT_LIMIT)
        sanitized.shouldContain("redacted")
    }

    @Test
    fun `preflight CLI rejects malformed selector run id and extra paths`() {
        parseCodecMatrixPreflightArguments(
            arrayOf("--backend", "java25", "--run-id", "preflight-cli-0001"),
        ).shouldBeEqualTo(
            CodecMatrixPreflightArguments(
                backend = CodecMatrixBackend.JAVA25,
                runId = CodecMatrixRunId("preflight-cli-0001"),
            ),
        )

        listOf(
            arrayOf("--backend", "jni", "--run-id", "preflight-cli-0001"),
            arrayOf("--backend", "java25", "--run-id", "short"),
            arrayOf("--backend", "java25", "--run-id", "preflight-cli-0001", "--output", "/tmp/x"),
        ).forEach { arguments ->
            assertFailsWith<IllegalArgumentException> {
                parseCodecMatrixPreflightArguments(arguments)
            }
        }
    }

    @Test
    fun `preflight JSON is strict and hash verified`() {
        tempDir = tempDir.toRealPath()
        val manifest = preflightCodecMatrix(
            runId = CodecMatrixRunId("preflight-json-0001"),
            backend = CodecMatrixBackend.JAVA25,
            probes = probes(jdkMajor = 25),
        )
        val target = tempDir.resolve("preflight.json")
        val sha256 = CodecMatrixJson.write(target, manifest)

        CodecMatrixJson.readPreflight(target, sha256).shouldBeEqualTo(manifest)
        Files.writeString(target, CodecMatrixJson.encode(manifest).replaceFirst("{", "{\n  \"unknown\": true,"))
        assertFailsWith<IllegalArgumentException> {
            CodecMatrixJson.readPreflight(target, CodecMatrixJson.sha256(Files.readAllBytes(target)))
        }
    }

    private fun probes(
        hostArchitecture: CodecMatrixArchitecture = CodecMatrixArchitecture.ARM64,
        jdkMajor: Int,
        jniArchitecture: CodecMatrixArchitecture? = null,
        nativeAccessEnabled: Boolean = true,
        gitDirty: Boolean = false,
    ): CodecMatrixPreflightProbes = CodecMatrixPreflightProbes(
        host = {
            CodecMatrixHostFacts(
                osName = "macOS",
                kernelVersion = "25.5.0",
                architecture = hostArchitecture,
                cpuModel = "Apple M4",
            )
        },
        jdk = {
            CodecMatrixJdkFacts(
                vendor = "Eclipse Adoptium",
                version = "$jdkMajor.0.2",
                major = jdkMajor,
            )
        },
        jniBinaryArchitecture = { jniArchitecture },
        git = { CodecMatrixGitFacts(sha = "a".repeat(40), dirty = gitDirty) },
        diskAvailableBytes = { 8L * 1024 * 1024 * 1024 },
        nativeAccessEnabled = { nativeAccessEnabled },
        loaderPathAvailable = { true },
    )
}
