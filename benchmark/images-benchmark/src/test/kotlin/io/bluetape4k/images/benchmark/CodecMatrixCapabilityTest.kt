package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CodecMatrixCapabilityTest {

    private lateinit var ops: FakeCodecOps
    private lateinit var evaluator: CodecMatrixCapabilityEvaluator

    @BeforeEach
    fun setUp() {
        ops = FakeCodecOps()
        evaluator = CodecMatrixCapabilityEvaluator(ops)
    }

    @Test
    fun `encode and decode capability gates are independent`() {
        evaluator.evaluate(available(CodecMatrixDirection.ENCODE), fixture(), CodecMatrixCellStatus.ELIGIBLE)
            .status.shouldBeEqualTo(CodecMatrixCellStatus.ELIGIBLE)
        evaluator.evaluate(unavailable(CodecMatrixDirection.DECODE), fixture(), CodecMatrixCellStatus.ELIGIBLE)
            .status.shouldBeEqualTo(CodecMatrixCellStatus.UNSUPPORTED)
    }

    @Test
    fun `decode requires pinned target input`() {
        val result = evaluator.evaluate(
            available(CodecMatrixDirection.DECODE),
            fixture(targetBytes = null),
            CodecMatrixCellStatus.ELIGIBLE,
        )

        result.status.shouldBeEqualTo(CodecMatrixCellStatus.ERROR)
        ops.openCalls.get().shouldBeEqualTo(0)
    }

    @Test
    fun `unavailable and unknown map to terminal unmeasured states`() {
        evaluator.evaluate(unavailable(CodecMatrixDirection.ENCODE), fixture(), CodecMatrixCellStatus.ELIGIBLE)
            .status.shouldBeEqualTo(CodecMatrixCellStatus.UNSUPPORTED)
        evaluator.evaluate(unknown(CodecMatrixDirection.ENCODE), fixture(), CodecMatrixCellStatus.ELIGIBLE)
            .status.shouldBeEqualTo(CodecMatrixCellStatus.SKIPPED)
        ops.openCalls.get().shouldBeEqualTo(0)
    }

    @Test
    fun `known host incompatibility becomes N A without native calls`() {
        val result = evaluator.evaluate(
            available(CodecMatrixDirection.ENCODE),
            fixture(),
            CodecMatrixCellStatus.N_A,
        )

        result.status.shouldBeEqualTo(CodecMatrixCellStatus.N_A)
        ops.openCalls.get().shouldBeEqualTo(0)
    }

    @Test
    fun `available smoke failure remains blocking and sanitized`() {
        ops.failure = IllegalStateException("native details /Users/alice/secret")

        val cell = evaluator.evaluate(
            available(CodecMatrixDirection.ENCODE),
            fixture(),
            CodecMatrixCellStatus.ELIGIBLE,
        )

        cell.status.shouldBeEqualTo(CodecMatrixCellStatus.FAILED_SMOKE)
        check("native details" !in requireNotNull(cell.reason))
        cell.reasonCode.shouldBeEqualTo(CodecMatrixReasonCode.SMOKE_FAILED)
    }

    @Test
    fun `malformed smoke output remains blocking`() {
        ops.output = "not-avif".toByteArray()

        evaluator.evaluate(
            available(CodecMatrixDirection.ENCODE),
            fixture(),
            CodecMatrixCellStatus.ELIGIBLE,
        ).status.shouldBeEqualTo(CodecMatrixCellStatus.FAILED_SMOKE)
    }

    @Test
    fun `operation handles close on success and exception`() {
        evaluator.evaluate(available(CodecMatrixDirection.ENCODE), fixture(), CodecMatrixCellStatus.ELIGIBLE)
        ops.closeCalls.get().shouldBeEqualTo(1)

        ops.failure = IllegalStateException("native failure")
        evaluator.evaluate(available(CodecMatrixDirection.ENCODE), fixture(), CodecMatrixCellStatus.ELIGIBLE)
        ops.closeCalls.get().shouldBeEqualTo(2)
    }

    private fun available(direction: CodecMatrixDirection) = CodecMatrixDirectionalCapability(
        format = CodecMatrixFormat.AVIF,
        direction = direction,
        support = CodecMatrixCapabilitySupport.AVAILABLE,
    )

    private fun unavailable(direction: CodecMatrixDirection) = CodecMatrixDirectionalCapability(
        format = CodecMatrixFormat.AVIF,
        direction = direction,
        support = CodecMatrixCapabilitySupport.UNAVAILABLE,
        reason = "encoder unavailable",
    )

    private fun unknown(direction: CodecMatrixDirection) = CodecMatrixDirectionalCapability(
        format = CodecMatrixFormat.AVIF,
        direction = direction,
        support = CodecMatrixCapabilitySupport.UNKNOWN,
        reason = "provider cannot inspect operation",
    )

    private fun fixture(targetBytes: ByteArray? = validAvif()): CodecMatrixSmokeFixture = CodecMatrixSmokeFixture(
        backend = CodecMatrixBackendId.JAVA25,
        scenario = CodecMatrixScenario.PROFILE,
        dimensions = CodecMatrixDimensions(512, 512),
        jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1),
        jpegSha256 = CodecMatrixSha256("a".repeat(64)),
        targetBytes = targetBytes,
        targetSha256 = targetBytes?.let { CodecMatrixJson.sha256(it) },
    )

    private fun validAvif(): ByteArray = byteArrayOf(
        0, 0, 0, 24,
        'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
        'a'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(),
    )

    private inner class FakeCodecOps : CodecMatrixCodecOps {
        val openCalls = AtomicInteger()
        val closeCalls = AtomicInteger()
        var failure: Exception? = null
        var output: ByteArray = validAvif()

        override fun open(bytes: ByteArray): CodecMatrixCodecHandle {
            openCalls.incrementAndGet()
            return object : CodecMatrixCodecHandle {
                override val width: Int = 512
                override val height: Int = 512

                override fun toBytes(format: CodecMatrixFormat): ByteArray {
                    failure?.let { throw it }
                    return if (format == CodecMatrixFormat.JPEG) {
                        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)
                    } else {
                        output
                    }
                }

                override fun close() {
                    closeCalls.incrementAndGet()
                }
            }
        }
    }
}
