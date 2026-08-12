package io.bluetape4k.images.privacy

import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.StreamWriteFeature
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.exc.StreamConstraintsException
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * privacy snapshot 전용 Jackson 3 codec입니다.
 *
 * JSON top-level은 항상 `schemaVersion`, `kind`, `value` envelope를 사용합니다. 외부
 * mapper 주입이나 polymorphic class-name resolution은 제공하지 않으며, caller stream은
 * 이 객체가 닫지 않습니다.
 */
object PrivacyDerivativeJackson {
    private const val SCHEMA_VERSION = 1
    private const val KIND_OPTIONS = "options"
    private const val KIND_REPORT = "report"
    private const val KIND_PAYLOAD = "payload"
    private const val KIND_BATCH = "batch"

    private val mapper: JsonMapper by lazy {
        val streamReadConstraints = StreamReadConstraints.builder()
            .maxNestingDepth(PrivacyDerivativeJsonLimits.DEFAULT_MAX_DEPTH)
            .maxDocumentLength(PrivacyDerivativeJsonLimits.DEFAULT_MAX_JSON_BYTES.toLong())
            .maxTokenCount(PrivacyDerivativeJsonLimits.DEFAULT_MAX_JSON_BYTES.toLong())
            .maxStringLength(PrivacyDerivativeJsonLimits.DEFAULT_MAX_JSON_BYTES)
            .maxNameLength(PrivacyDerivativeJsonLimits.DEFAULT_MAX_CODE_LENGTH)
            .build()
        val jsonFactory = JsonFactory.builder()
            .streamReadConstraints(streamReadConstraints)
            .build()
        JsonMapper.builder(jsonFactory)
            .addModule(kotlinModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build()
    }

    /** options snapshot을 canonical envelope JSON으로 인코딩합니다. */
    fun encodeOptions(value: PrivacyDerivativeOptionsSnapshot): String = encode(KIND_OPTIONS, value)

    /** options snapshot JSON을 복원합니다. */
    fun decodeOptions(json: String, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativeOptionsSnapshot =
        decode(json.toByteArray(StandardCharsets.UTF_8), KIND_OPTIONS, PrivacyDerivativeOptionsSnapshot::class.java, limits)

    /** report snapshot을 canonical envelope JSON으로 인코딩합니다. */
    fun encodeReport(value: PrivacyDerivativeReportSnapshot): String = encode(KIND_REPORT, value)

    /** report snapshot JSON을 복원합니다. */
    fun decodeReport(json: String, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativeReportSnapshot =
        decode(json.toByteArray(StandardCharsets.UTF_8), KIND_REPORT, PrivacyDerivativeReportSnapshot::class.java, limits)

    /** payload snapshot을 canonical envelope JSON으로 인코딩합니다. */
    fun encodePayload(value: PrivacyDerivativePayload): String = encode(KIND_PAYLOAD, value)

    /** payload snapshot JSON을 복원합니다. */
    fun decodePayload(json: String, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativePayload =
        decode(json.toByteArray(StandardCharsets.UTF_8), KIND_PAYLOAD, PrivacyDerivativePayload::class.java, limits)

    /** payload snapshot을 UTF-8 byte로 인코딩합니다. */
    fun encodePayloadBytes(value: PrivacyDerivativePayload): ByteArray =
        encodePayload(value).toByteArray(StandardCharsets.UTF_8)

    /** UTF-8 payload snapshot을 복원합니다. */
    fun decodePayload(bytes: ByteArray, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativePayload =
        decode(bytes, KIND_PAYLOAD, PrivacyDerivativePayload::class.java, limits)

    /** payload snapshot을 caller stream에 한 번 쓰며 stream을 flush/close하지 않습니다. */
    fun encodePayloadTo(value: PrivacyDerivativePayload, output: OutputStream) {
        encodeTo(KIND_PAYLOAD, value, output)
    }

    /** caller stream에서 payload 하나를 읽고 trailing non-whitespace를 거부합니다. */
    fun decodePayload(
        input: InputStream,
        limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits(),
    ): PrivacyDerivativePayload = decode(
        input,
        KIND_PAYLOAD,
        PrivacyDerivativePayload::class.java,
        limits,
    )

    /** batch snapshot을 canonical envelope JSON으로 인코딩합니다. */
    fun encodeBatch(value: PrivacyDerivativeBatchSnapshot): String = encode(KIND_BATCH, value)

    /** options snapshot을 UTF-8 byte로 인코딩합니다. */
    fun encodeOptionsBytes(value: PrivacyDerivativeOptionsSnapshot): ByteArray =
        encodeOptions(value).toByteArray(StandardCharsets.UTF_8)

    /** report snapshot을 UTF-8 byte로 인코딩합니다. */
    fun encodeReportBytes(value: PrivacyDerivativeReportSnapshot): ByteArray =
        encodeReport(value).toByteArray(StandardCharsets.UTF_8)

    /** batch snapshot을 UTF-8 byte로 인코딩합니다. */
    fun encodeBatchBytes(value: PrivacyDerivativeBatchSnapshot): ByteArray =
        encodeBatch(value).toByteArray(StandardCharsets.UTF_8)

    /** options snapshot을 caller stream에 쓰며 stream ownership을 변경하지 않습니다. */
    fun encodeOptionsTo(value: PrivacyDerivativeOptionsSnapshot, output: OutputStream) =
        encodeTo(KIND_OPTIONS, value, output)

    /** report snapshot을 caller stream에 쓰며 stream ownership을 변경하지 않습니다. */
    fun encodeReportTo(value: PrivacyDerivativeReportSnapshot, output: OutputStream) =
        encodeTo(KIND_REPORT, value, output)

    /** batch snapshot을 caller stream에 쓰며 stream ownership을 변경하지 않습니다. */
    fun encodeBatchTo(value: PrivacyDerivativeBatchSnapshot, output: OutputStream) =
        encodeTo(KIND_BATCH, value, output)

    /** options snapshot을 caller stream에서 읽습니다. */
    fun decodeOptions(input: InputStream, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativeOptionsSnapshot =
        decode(input, KIND_OPTIONS, PrivacyDerivativeOptionsSnapshot::class.java, limits)

    /** report snapshot을 caller stream에서 읽습니다. */
    fun decodeReport(input: InputStream, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativeReportSnapshot =
        decode(input, KIND_REPORT, PrivacyDerivativeReportSnapshot::class.java, limits)

    /** batch snapshot을 caller stream에서 읽습니다. */
    fun decodeBatch(input: InputStream, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativeBatchSnapshot =
        decode(input, KIND_BATCH, PrivacyDerivativeBatchSnapshot::class.java, limits)

    fun decodeOptions(bytes: ByteArray, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativeOptionsSnapshot =
        decode(bytes, KIND_OPTIONS, PrivacyDerivativeOptionsSnapshot::class.java, limits)

    fun decodeReport(bytes: ByteArray, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativeReportSnapshot =
        decode(bytes, KIND_REPORT, PrivacyDerivativeReportSnapshot::class.java, limits)

    fun decodeBatch(bytes: ByteArray, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativeBatchSnapshot =
        decode(bytes, KIND_BATCH, PrivacyDerivativeBatchSnapshot::class.java, limits)

    private fun encodeTo(kind: String, value: Any, output: OutputStream) {
        val generator = try {
            mapper.createGenerator(output)
        } catch (_: JacksonException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.IO_FAILURE)
        }
        try {
            generator.configure(StreamWriteFeature.FLUSH_PASSED_TO_STREAM, false)
            generator.writeStartObject()
            generator.writeNumberProperty("schemaVersion", SCHEMA_VERSION)
            generator.writeStringProperty("kind", kind)
            generator.writeName("value")
            generator.writePOJO(value)
            generator.writeEndObject()
            // Flush the generator's internal buffer, but never call OutputStream.flush().
            generator.flush()
        } catch (_: IOException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.IO_FAILURE)
        } catch (e: JacksonException) {
            throw PrivacyDerivativeCodecException(
                e.findCodecReason() ?: PrivacyDerivativeCodecReason.INVALID_VALUE,
            )
        }
        // Do not flush or close: the caller owns output and controls terminal I/O.
    }

    /** batch snapshot JSON을 복원합니다. */
    fun decodeBatch(json: String, limits: PrivacyDerivativeJsonLimits = PrivacyDerivativeJsonLimits()): PrivacyDerivativeBatchSnapshot =
        decode(json.toByteArray(StandardCharsets.UTF_8), KIND_BATCH, PrivacyDerivativeBatchSnapshot::class.java, limits)

    private fun encode(kind: String, value: Any): String =
        try {
            mapper.writeValueAsString(JsonEnvelope(SCHEMA_VERSION, kind, value))
        } catch (_: JacksonException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.INVALID_VALUE)
        }

    private fun <T : Any> decode(
        bytes: ByteArray,
        expectedKind: String,
        targetType: Class<T>,
        limits: PrivacyDerivativeJsonLimits,
    ): T {
        if (bytes.size > limits.maxJsonBytes) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
        }
        return decodeRoot(parseSingleDocument(bytes), expectedKind, targetType, limits)
    }

    private fun <T : Any> decodeRoot(
        root: JsonNode,
        expectedKind: String,
        targetType: Class<T>,
        limits: PrivacyDerivativeJsonLimits,
    ): T {
        preflight(root, limits)
        requireEnvelope(root, expectedKind)
        val value = root.get("value")
            ?: throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.NULL_VALUE)
        if (value.isNull) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.NULL_VALUE)
        }
        try {
            val normalizedValue = value.deepCopy().also(::normalizePayloadFields)
            val result = mapper.treeToValue(normalizedValue, targetType)
            validateDecoded(result, limits)
            return result
        } catch (e: PrivacyDerivativeCodecException) {
            throw e
        } catch (_: StreamConstraintsException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
        } catch (e: JacksonException) {
            val reason = if (e.isUnknownFieldFailure()) {
                PrivacyDerivativeCodecReason.UNKNOWN_FIELD
            } else {
                PrivacyDerivativeCodecReason.INVALID_VALUE
            }
            throw PrivacyDerivativeCodecException(reason)
        } catch (_: IllegalArgumentException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.INVALID_VALUE)
        }
    }

    private fun <T : Any> decode(
        input: InputStream,
        expectedKind: String,
        targetType: Class<T>,
        limits: PrivacyDerivativeJsonLimits,
    ): T {
        val boundedInput = BoundedInputStream(input, limits.maxJsonBytes)
        val parser = try {
            mapper.createParser(boundedInput)
        } catch (e: JacksonException) {
            throw e.toCodecException(PrivacyDerivativeCodecReason.MALFORMED_JSON)
        }
        return try {
            decodeEnvelope(parser, expectedKind, targetType, limits)
        } catch (e: PrivacyDerivativeCodecException) {
            throw e
        } catch (_: StreamConstraintsException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
        } catch (e: JacksonException) {
            throw if (boundedInput.overflowed) {
                PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
            } else {
                e.toCodecException(PrivacyDerivativeCodecReason.MALFORMED_JSON)
            }
        } catch (_: IOException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.IO_FAILURE)
        } finally {
            // BoundedInputStream.close() is intentionally a no-op: the caller owns input.
            parser.close()
        }
    }

    /**
     * Reads the envelope incrementally. The value is decoded directly into its typed
     * snapshot instead of first materializing an unbounded JsonNode tree.
     *
     * The canonical property order is required so schema/kind are validated before
     * allocating the typed value. This is a deliberate wire-contract guard for bounded
     * external input; encoders in this object always emit this order.
     */
    private fun <T : Any> decodeEnvelope(
        parser: JsonParser,
        expectedKind: String,
        targetType: Class<T>,
        limits: PrivacyDerivativeJsonLimits,
    ): T {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TYPE_MISMATCH)
        }
        requireField(parser, "schemaVersion")
        if (parser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.UNSUPPORTED_SCHEMA_VERSION)
        }
        val schema = parser.intValue
        if (schema != SCHEMA_VERSION) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.UNSUPPORTED_SCHEMA_VERSION)
        }
        requireField(parser, "kind")
        if (parser.nextToken() != JsonToken.VALUE_STRING) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TYPE_MISMATCH)
        }
        val kind = parser.text
            ?: throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TYPE_MISMATCH)
        if (kind != expectedKind) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TYPE_MISMATCH)
        }
        requireField(parser, "value")
        if (parser.nextToken() == null) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.NULL_VALUE)
        }
        val value = try {
            mapper.readValue(parser, targetType)
        } catch (e: JacksonException) {
            val nestedReason = e.findCodecReason()
            if (nestedReason != null) {
                throw PrivacyDerivativeCodecException(nestedReason)
            }
            if (e.message?.contains("trailing", ignoreCase = true) == true) {
                throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TRAILING_DATA)
            }
            if (e.isUnknownFieldFailure()) {
                throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.UNKNOWN_FIELD)
            }
            throw e
        }
        if (parser.nextToken() != JsonToken.END_OBJECT) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.UNKNOWN_FIELD)
        }
        if (parser.nextToken() != null) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TRAILING_DATA)
        }
        try {
            validateDecoded(value, limits)
        } catch (e: PrivacyDerivativeCodecException) {
            throw e
        } catch (_: RuntimeException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.INVALID_VALUE)
        }
        return value
    }

    private fun requireField(parser: JsonParser, expected: String) {
        if (parser.nextToken() != JsonToken.PROPERTY_NAME || parser.currentName() != expected) {
            throw PrivacyDerivativeCodecException(
                if (parser.currentToken() == JsonToken.PROPERTY_NAME) PrivacyDerivativeCodecReason.UNKNOWN_FIELD
                else PrivacyDerivativeCodecReason.TYPE_MISMATCH,
            )
        }
    }

    private fun normalizePayloadFields(node: JsonNode) {
        if (!node.isObject) return
        val objectNode = node.asObject()
        if (objectNode.has("bytes") && objectNode.has("encodedBytes")) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.UNKNOWN_FIELD)
        }
        objectNode.get("bytes")?.let { bytesNode ->
            objectNode.remove("bytes")
            objectNode.set("encodedBytes", bytesNode)
        }
        objectNode.values().forEach(::normalizePayloadFields)
    }

    private fun parseSingleDocument(bytes: ByteArray): JsonNode {
        val parser = try {
            mapper.createParser(bytes)
        } catch (e: JacksonException) {
            throw e.toCodecException(PrivacyDerivativeCodecReason.MALFORMED_JSON)
        }
        try {
            if (hasTrailingData(bytes)) {
                throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TRAILING_DATA)
            }
            val root = mapper.readTree(parser)
                ?: throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.NULL_VALUE)
            if (parser.nextToken() != null) {
                throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TRAILING_DATA)
            }
            return root
        } catch (e: PrivacyDerivativeCodecException) {
            throw e
        } catch (_: StreamConstraintsException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
        } catch (e: JacksonException) {
            throw e.toCodecException(PrivacyDerivativeCodecReason.MALFORMED_JSON)
        } catch (_: IOException) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.IO_FAILURE)
        } finally {
            parser.close()
        }
    }


    private fun hasTrailingData(bytes: ByteArray): Boolean {
        var index = 0
        while (index < bytes.size && bytes[index].toInt().toChar().isWhitespace()) index++
        if (index == bytes.size) return false
        val first = bytes[index].toInt().toChar()
        if (first != '{' && first != '[') {
            return false
        }
        var depth = 0
        var inString = false
        var escaped = false
        var closedAt = -1
        while (index < bytes.size) {
            val ch = bytes[index].toInt().toChar()
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth == 0) {
                            closedAt = index
                            break
                        }
                    }
                }
            }
            index++
        }
        if (closedAt < 0) return false
        index = closedAt + 1
        while (index < bytes.size && bytes[index].toInt().toChar().isWhitespace()) index++
        return index < bytes.size
    }

    private fun requireEnvelope(root: JsonNode, expectedKind: String) {
        if (!root.isObject) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TYPE_MISMATCH)
        }
        val schema = root.get("schemaVersion")
            ?: throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.UNSUPPORTED_SCHEMA_VERSION)
        if (schema.isNull) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.NULL_VALUE)
        }
        if (!schema.isIntegralNumber || schema.asInt() != SCHEMA_VERSION) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.UNSUPPORTED_SCHEMA_VERSION)
        }
        val kind = root.get("kind")
        if (kind == null || kind.isNull) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TYPE_MISMATCH)
        }
        if (!kind.isString || kind.asString() != expectedKind) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.TYPE_MISMATCH)
        }
        if (!root.has("value")) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.NULL_VALUE)
        }
        val unknown = root.propertyNames().filter { it !in setOf("schemaVersion", "kind", "value") }
        if (unknown.isNotEmpty()) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.UNKNOWN_FIELD)
        }
    }

    private fun preflight(node: JsonNode, limits: PrivacyDerivativeJsonLimits, depth: Int = 0) {
        if (depth > limits.maxDepth) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
        }
        if (node.isString && node.asString().length > limits.maxJsonBytes) {
            throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
        }
        if (node.isArray) {
            if (node.size() > limits.maxRedactions.coerceAtLeast(limits.maxActions).coerceAtLeast(limits.maxFailures)) {
                throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
            }
            node.forEach { preflight(it, limits, depth + 1) }
        } else if (node.isObject) {
            if (node.size() > limits.maxMetadataEntries + 16) {
                throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
            }
            node.values().forEach { preflight(it, limits, depth + 1) }
            val encodedBytes = node.get("bytes") ?: node.get("encodedBytes")
            if (encodedBytes != null && encodedBytes.isString) {
                val maximumBase64Chars = ((limits.maxPayloadBytes + 2L) / 3L * 4L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                if (encodedBytes.asString().length > maximumBase64Chars) {
                    throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
                }
            }
        }
    }

    private fun <T : Any> validateDecoded(value: T, limits: PrivacyDerivativeJsonLimits) {
        when (value) {
            is PrivacyDerivativePayload -> {
                if (value.bytes.size > limits.maxPayloadBytes) {
                    throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
                }
                validateDecoded(value.report, limits)
            }

            is PrivacyDerivativeOptionsSnapshot -> {
                if (value.redactions.size > limits.maxRedactions || value.maxPixels > limits.maxPixels ||
                    (value.maxSide ?: 0) > limits.maxSide ||
                    (value.thumbnailSize?.width ?: 0) > limits.maxThumbnailSide ||
                    (value.thumbnailSize?.height ?: 0) > limits.maxThumbnailSide
                ) {
                    throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
                }
            }

            is PrivacyDerivativeReportSnapshot -> {
                if ((value.sourceId?.length ?: 0) > limits.maxSourceIdLength) {
                    throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
                }
                if (value.appliedActions.size > limits.maxActions || value.failures.size > limits.maxFailures ||
                    value.redactions.size > limits.maxRedactions ||
                    value.strippedMetadataCategories.size > limits.maxMetadataEntries ||
                    value.metadataVerification.requested.size > limits.maxMetadataEntries ||
                    value.metadataVerification.sourcePresent.size > limits.maxMetadataEntries ||
                    value.metadataVerification.remaining.size > limits.maxMetadataEntries ||
                    value.failures.any {
                        it.code.name.length > limits.maxCodeLength || it.stage.name.length > limits.maxCodeLength
                    }
                ) {
                    throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
                }
            }

            is PrivacyDerivativeBatchSnapshot -> {
                if (value.sourceId.length > limits.maxSourceIdLength) {
                    throw PrivacyDerivativeCodecException(PrivacyDerivativeCodecReason.LIMIT_EXCEEDED)
                }
                value.payload?.let { validateDecoded(it, limits) }
            }
        }
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Int,
    ) : InputStream() {
        private var consumed: Int = 0
        private var overflowChecked: Boolean = false
        private var overflow: Boolean = false

        val overflowed: Boolean
            get() = overflow

        override fun read(): Int {
            if (consumed == maxBytes) {
                checkOverflow()
                return -1
            }
            val value = delegate.read()
            if (value >= 0) consumed++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (consumed == maxBytes) {
                checkOverflow()
                return -1
            }
            val allowed = minOf(length, maxBytes - consumed)
            val count = delegate.read(buffer, offset, allowed)
            if (count > 0) consumed += count
            return count
        }

        override fun close() {
            // The caller owns the source stream.
        }

        private fun checkOverflow() {
            if (overflowChecked) return
            overflowChecked = true
            if (delegate.read() >= 0) {
                overflow = true
                throw CodecInputLimitException()
            }
        }
    }

    private fun JacksonException.toCodecException(fallback: PrivacyDerivativeCodecReason): PrivacyDerivativeCodecException =
        PrivacyDerivativeCodecException(
            findCodecReason() ?: if (cause is IOException) PrivacyDerivativeCodecReason.IO_FAILURE else fallback,
        )

    private fun JacksonException.findCodecReason(): PrivacyDerivativeCodecReason? {
        var current: Throwable? = this
        while (current != null) {
            if (current is PrivacyDerivativeCodecException) return current.reason
            if (current is CodecInputLimitException) return PrivacyDerivativeCodecReason.LIMIT_EXCEEDED
            if (current is IOException) return PrivacyDerivativeCodecReason.IO_FAILURE
            current = current.cause
        }
        return null
    }

    private fun JacksonException.isUnknownFieldFailure(): Boolean =
        message?.let { it.contains("unknown", ignoreCase = true) || it.contains("unrecognized", ignoreCase = true) } == true

    private class CodecInputLimitException : IOException("input limit exceeded")

    private data class JsonEnvelope<T>(
        val schemaVersion: Int,
        val kind: String,
        val value: T,
    )
}
