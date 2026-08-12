package io.bluetape4k.images.privacy

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.assertions.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class PrivacyDerivativeSerializationTest {

    @Test
    fun `payload uses versioned envelope and round trips through Jackson 3`() {
        val payload = payload()

        val json = PrivacyDerivativeJackson.encodePayload(payload)
        json shouldContain "\"schemaVersion\":1"
        json shouldContain "\"kind\":\"payload\""
        json shouldNotContain "ImmutableImage"
        json shouldNotContain "java.nio.file"

        PrivacyDerivativeJackson.decodePayload(json) shouldBeEqualTo payload
    }

    @Test
    fun `typed codec rejects unknown schema and kind mismatch`() {
        val payload = payload()

        val unsupported = PrivacyDerivativeJackson.encodePayload(payload)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        val unsupportedError = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(unsupported)
        }
        unsupportedError.reason shouldBeEqualTo PrivacyDerivativeCodecReason.UNSUPPORTED_SCHEMA_VERSION

        val mismatch = PrivacyDerivativeJackson.encodePayload(payload)
            .replace("\"kind\":\"payload\"", "\"kind\":\"report\"")
        val mismatchError = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(mismatch)
        }
        mismatchError.reason shouldBeEqualTo PrivacyDerivativeCodecReason.TYPE_MISMATCH
    }

    @Test
    fun `payload copies bytes and collections across Java serialization`() {
        val bytes = byteArrayOf(1, 2, 3)
        val payload = payload(bytes)
        bytes[0] = 9

        val serialized = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(payload) }
        }.toByteArray()
        val roundTrip = ObjectInputStream(ByteArrayInputStream(serialized)).use {
            it.readObject() as PrivacyDerivativePayload
        }

        roundTrip.bytes.contentEquals(byteArrayOf(1, 2, 3)) shouldBeEqualTo true
        roundTrip shouldBeEqualTo payload
        roundTrip.report.appliedActions shouldHaveSize 1
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (roundTrip.report.appliedActions as MutableList<PrivacyWireDerivativeActionId>)
                .add(PrivacyWireDerivativeActionId.REDACT)
        }
    }

    @Test
    fun `snapshot classes expose an explicit serialVersionUID`() {
        listOf(
            PrivacyThumbnailSizeSnapshot::class.java,
            PrivacyRedactionSnapshot::class.java,
            PrivacyDerivativeOptionsSnapshot::class.java,
            PrivacyImageDimensionsSnapshot::class.java,
            PrivacyAppliedRedactionSnapshot::class.java,
            PrivacyMetadataVerificationSnapshot::class.java,
            PrivacyDerivativeFailureSnapshot::class.java,
            PrivacyDerivativeReportSnapshot::class.java,
            PrivacyDerivativePayload::class.java,
            PrivacyDerivativeBatchSnapshot::class.java,
        ).forEach { type ->
            Serializable::class.java.isAssignableFrom(type) shouldBeEqualTo true
            type.getDeclaredField("serialVersionUID").getLong(null) shouldBeEqualTo 1L
        }
    }

    @Test
    fun `options snapshot rejects custom writer and restores built in rectangle policy`() {
        val options = PrivacyDerivativeOptions(
            outputFormat = PrivacyDerivativeFormat.Jpeg,
            redactions = emptyList(),
        )

        val snapshot = options.toSnapshot()
        snapshot.toOptions().outputFormat shouldBeEqualTo PrivacyDerivativeFormat.Jpeg
        snapshot.toOptions().redactions shouldHaveSize 0

        PrivacyDerivativeJackson.decodeOptions(PrivacyDerivativeJackson.encodeOptions(snapshot))
            .toOptions().outputFormat shouldBeEqualTo PrivacyDerivativeFormat.Jpeg
        PrivacyDerivativeJackson.decodeOptions(PrivacyDerivativeJackson.encodeOptionsBytes(snapshot))
            .toOptions().outputFormat shouldBeEqualTo PrivacyDerivativeFormat.Jpeg
    }

    @Test
    fun `report and batch snapshots use typed envelopes`() {
        val report = payload().report
        PrivacyDerivativeJackson.decodeReport(PrivacyDerivativeJackson.encodeReport(report)) shouldBeEqualTo report

        val batch = PrivacyDerivativeBatchSnapshot("fixture.png", payload(), null)
        val batchJson = PrivacyDerivativeJackson.encodeBatch(batch)
        PrivacyDerivativeJackson.decodeBatch(batchJson) shouldBeEqualTo batch
        PrivacyDerivativeJackson.decodeBatch(PrivacyDerivativeJackson.encodeBatchBytes(batch)) shouldBeEqualTo batch
        assertFailsWith<IllegalArgumentException> {
            PrivacyDerivativeBatchSnapshot("fixture.png", null, null)
        }
    }

    @Test
    fun `caller limits apply to nested report collections`() {
        val report = payload().report
        val redaction = PrivacyAppliedRedactionSnapshot("region", 0.0, 0.0, 1.0, 1.0)
        val expanded = PrivacyDerivativeReportSnapshot(
            sourceId = report.sourceId,
            sourceDimensions = report.sourceDimensions,
            outputDimensions = report.outputDimensions,
            strippedMetadataCategories = report.strippedMetadataCategories,
            appliedActions = report.appliedActions,
            redactions = listOf(redaction, redaction),
            failures = report.failures,
            elapsedMillis = report.elapsedMillis,
            metadataVerification = report.metadataVerification,
        )
        assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodeReport(
                PrivacyDerivativeJackson.encodeReport(expanded),
                PrivacyDerivativeJsonLimits(maxRedactions = 1),
            )
        }.reason shouldBeEqualTo PrivacyDerivativeCodecReason.LIMIT_EXCEEDED
    }

    @Test
    fun `codec rejects unknown fields, malformed JSON, and oversized payload`() {
        val json = PrivacyDerivativeJackson.encodePayload(payload())
        val unknown = json.replace("\"kind\":\"payload\"", "\"kind\":\"payload\",\"extra\":true")
        assertFailsWith<PrivacyDerivativeCodecException> { PrivacyDerivativeJackson.decodePayload(unknown) }
            .reason shouldBeEqualTo PrivacyDerivativeCodecReason.UNKNOWN_FIELD
        assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(ByteArrayInputStream(unknown.toByteArray(StandardCharsets.UTF_8)))
        }.reason shouldBeEqualTo PrivacyDerivativeCodecReason.UNKNOWN_FIELD

        val unknownEnum = json.replace("\"ENCODED\"", "\"FUTURE_ACTION\"")
        assertFailsWith<PrivacyDerivativeCodecException> { PrivacyDerivativeJackson.decodePayload(unknownEnum) }
            .reason shouldBeEqualTo PrivacyDerivativeCodecReason.INVALID_VALUE

        val malformed = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload("{")
        }
        malformed.reason shouldBeEqualTo PrivacyDerivativeCodecReason.MALFORMED_JSON
        malformed.cause shouldBeEqualTo null
        malformed.message shouldNotContain "PrivacyDerivative"

        val limits = PrivacyDerivativeJsonLimits(maxPayloadBytes = 2)
        assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(json, limits)
        }.reason shouldBeEqualTo PrivacyDerivativeCodecReason.LIMIT_EXCEEDED
    }

    @Test
    fun `streaming codec does not close caller streams and rejects trailing data`() {
        val payload = payload()
        val output = RecordingOutputStream()

        PrivacyDerivativeJackson.encodePayloadTo(payload, output)
        output.closed shouldBeEqualTo false
        output.flushCount shouldBeEqualTo 0

        val trailing = ByteArrayInputStream(
            output.toByteArray() + "{}".toByteArray(StandardCharsets.UTF_8),
        )
        val error = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(trailing)
        }
        error.reason shouldBeEqualTo PrivacyDerivativeCodecReason.TRAILING_DATA
    }

    @Test
    fun `streaming codec enforces document limit before materializing a byte array`() {
        val json = PrivacyDerivativeJackson.encodePayload(payload())
        val error = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(
                ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)),
                PrivacyDerivativeJsonLimits(maxJsonBytes = json.toByteArray(StandardCharsets.UTF_8).size - 1),
            )
        }
        error.reason shouldBeEqualTo PrivacyDerivativeCodecReason.LIMIT_EXCEEDED
    }

    @Test
    fun `streaming codec maps caller IO failures to stable reason codes`() {
        val inputError = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(FailingInputStream())
        }
        inputError.reason shouldBeEqualTo PrivacyDerivativeCodecReason.IO_FAILURE

        val outputError = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.encodePayloadTo(payload(), FailingOutputStream())
        }
        outputError.reason shouldBeEqualTo PrivacyDerivativeCodecReason.IO_FAILURE
    }

    @Test
    fun `shared codec is safe for concurrent typed round trips`() {
        val pool = Executors.newFixedThreadPool(16)
        val ready = CountDownLatch(16)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until 16).map {
                pool.submit {
                    ready.countDown()
                    start.await(5, TimeUnit.SECONDS)
                    repeat(100) {
                        PrivacyDerivativeJackson.decodePayload(
                            PrivacyDerivativeJackson.encodePayload(payload()),
                        ) shouldBeEqualTo payload()
                    }
                }
            }
            ready.await(5, TimeUnit.SECONDS) shouldBeEqualTo true
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `source id never accepts absolute path or control characters`() {
        val result = assertFailsWith<IllegalArgumentException> {
            PrivacyDerivativeBatchSnapshot(
                sourceId = "/private/source.png",
                payload = payload(),
                failure = null,
            )
        }
        result.message.shouldNotBeNull()
    }

    private fun payload(bytes: ByteArray = byteArrayOf(1, 2, 3)): PrivacyDerivativePayload =
        PrivacyDerivativePayload(
            encodedBytes = bytes,
            report = PrivacyDerivativeReportSnapshot(
                sourceId = "fixture.png",
                sourceDimensions = PrivacyImageDimensionsSnapshot(10, 10),
                outputDimensions = PrivacyImageDimensionsSnapshot(10, 10),
                strippedMetadataCategories = emptySet(),
                appliedActions = listOf(PrivacyWireDerivativeActionId.ENCODED),
                redactions = emptyList(),
                failures = emptyList(),
                elapsedMillis = 1,
                metadataVerification = PrivacyMetadataVerificationSnapshot(
                    requested = emptySet(),
                    sourcePresent = emptySet(),
                    remaining = emptySet(),
                    verified = true,
                ),
            ),
        )

    private class RecordingOutputStream : OutputStream() {
        private val delegate = ByteArrayOutputStream()
        var closed: Boolean = false
            private set
        var flushCount: Int = 0
            private set

        override fun write(b: Int) = delegate.write(b)

        override fun write(bytes: ByteArray, offset: Int, length: Int) = delegate.write(bytes, offset, length)

        override fun flush() {
            flushCount++
        }

        override fun close() {
            closed = true
        }

        fun toByteArray(): ByteArray = delegate.toByteArray()
    }

    private class FailingInputStream : ByteArrayInputStream(byteArrayOf('{'.code.toByte())) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("fixture")

        override fun read(): Int = throw IOException("fixture")
    }

    private class FailingOutputStream : OutputStream() {
        override fun write(b: Int) = throw IOException("fixture")
    }
}
