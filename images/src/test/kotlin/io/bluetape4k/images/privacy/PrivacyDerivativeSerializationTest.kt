package io.bluetape4k.images.privacy

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.concurrent.await
import io.bluetape4k.concurrent.get
import io.bluetape4k.io.lookup
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.support.toUtf8Bytes
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.Serializable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

class PrivacyDerivativeSerializationTest {

    companion object: KLogging()

    @Test
    fun `payload uses versioned envelope and round trips through Jackson 3`() {
        val payload = payload()

        val json = PrivacyDerivativeJackson.encodePayload(payload)

        log.debug { "json=$json" }
        json shouldContain "\"schemaVersion\":1"
        json shouldContain "\"kind\":\"payload\""
        json shouldNotContain "ImmutableImage"
        json shouldNotContain "java.nio.file"

        PrivacyDerivativeJackson.decodePayload(json) shouldBeEqualTo payload
    }

    @Test
    fun `typed codec rejects unknown schema and kind mismatch`() {
        val payload = payload()

        val unsupported = PrivacyDerivativeJackson
            .encodePayload(payload)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":99")

        val unsupportedError = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(unsupported)
        }
        unsupportedError.reason shouldBeEqualTo PrivacyDerivativeCodecReason.UNSUPPORTED_SCHEMA_VERSION

        val mismatch = PrivacyDerivativeJackson
            .encodePayload(payload)
            .replace("\"kind\":\"payload\"", "\"kind\":\"report\"")

        val mismatchError = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(mismatch)
        }
        mismatchError.reason shouldBeEqualTo PrivacyDerivativeCodecReason.TYPE_MISMATCH
    }

    @Suppress("DEPRECATION")
    @Test
    fun `payload copies bytes and collections across Java serialization`() {
        val bytes = byteArrayOf(1, 2, 3)
        val payload = payload(bytes)
        bytes[0] = 9

        val serialized = BinarySerializers.Jdk.serialize(payload)
        val roundTrip = BinarySerializers.Jdk.deserialize<PrivacyDerivativePayload>(serialized).shouldNotBeNull()

        roundTrip.bytes.contentEquals(byteArrayOf(1, 2, 3)) shouldBeEqualTo true
        roundTrip shouldBeEqualTo payload
        roundTrip.report.appliedActions shouldHaveSize 1

        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            roundTrip.report
                .appliedActions.shouldBeInstanceOf<MutableList<PrivacyWireDerivativeActionId>>()
                .add(PrivacyWireDerivativeActionId.REDACT)
        }
    }

    @Test
    fun `snapshot classes expose an explicit serialVersionUID`() {
        listOf(
            PrivacyThumbnailSizeSnapshot::class,
            PrivacyRedactionSnapshot::class,
            PrivacyDerivativeOptionsSnapshot::class,
            PrivacyImageDimensionsSnapshot::class,
            PrivacyAppliedRedactionSnapshot::class,
            PrivacyMetadataVerificationSnapshot::class,
            PrivacyDerivativeFailureSnapshot::class,
            PrivacyDerivativeReportSnapshot::class,
            PrivacyDerivativePayload::class,
            PrivacyDerivativeBatchSnapshot::class,
        ).forEach { clazz ->
            Serializable::class.java.isAssignableFrom(clazz.java).shouldBeTrue()
            clazz.lookup().serialVersionUID shouldBeEqualTo 1L
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
        log.debug { "expanded=$expanded" }

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

        assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(unknown)
        }.reason shouldBeEqualTo PrivacyDerivativeCodecReason.UNKNOWN_FIELD

        assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(ByteArrayInputStream(unknown.toUtf8Bytes()))
        }.reason shouldBeEqualTo PrivacyDerivativeCodecReason.UNKNOWN_FIELD

        val unknownEnum = json.replace("\"ENCODED\"", "\"FUTURE_ACTION\"")
        assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload(unknownEnum)
        }.reason shouldBeEqualTo PrivacyDerivativeCodecReason.INVALID_VALUE

        val malformed = assertFailsWith<PrivacyDerivativeCodecException> {
            PrivacyDerivativeJackson.decodePayload("{")
        }
        malformed.reason shouldBeEqualTo PrivacyDerivativeCodecReason.MALFORMED_JSON
        malformed.cause.shouldBeNull()
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

        output.closed.shouldBeFalse()
        output.flushCount shouldBeEqualTo 0

        val trailing = ByteArrayInputStream(output.toByteArray() + "{}".toUtf8Bytes())

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
                ByteArrayInputStream(json.toUtf8Bytes()),
                PrivacyDerivativeJsonLimits(maxJsonBytes = json.toUtf8Bytes().size - 1),
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

        val expectedPayload = payload()
        try {
            val futures = List(16) {
                pool.submit {
                    ready.countDown()
                    start.await(5.seconds)

                    repeat(100) {
                        PrivacyDerivativeJackson.decodePayload(
                            PrivacyDerivativeJackson.encodePayload(expectedPayload),
                        ) shouldBeEqualTo expectedPayload
                    }
                }
            }
            ready.await(5.seconds).shouldBeTrue()
            start.countDown()
            futures.forEach { it.get(30.seconds) }
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

    private class RecordingOutputStream: OutputStream() {
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

    private class FailingInputStream: ByteArrayInputStream(byteArrayOf('{'.code.toByte())) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("fixture")

        override fun read(): Int = throw IOException("fixture")
    }

    private class FailingOutputStream: OutputStream() {
        override fun write(b: Int) = throw IOException("fixture")
    }
}
