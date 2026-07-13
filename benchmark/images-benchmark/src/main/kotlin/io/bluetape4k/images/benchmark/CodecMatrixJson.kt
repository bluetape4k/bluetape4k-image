package io.bluetape4k.images.benchmark

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object CodecMatrixJson {

    private const val MAX_JSON_BYTES: Long = 1_048_576L
    private const val MAX_JSON_DEPTH: Int = 32
    private const val MAX_JSON_STRING_LENGTH: Int = 4096
    private const val MAX_JSON_COLLECTION_SIZE: Int = 128

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
        allowSpecialFloatingPointValues = false
    }

    internal fun encode(manifest: CodecMatrixEligibilityManifest): String =
        json.encodeToString(manifest.validateEligibility())

    internal fun encode(manifest: CodecMatrixFixtureManifest): String =
        json.encodeToString(manifest)

    internal fun encode(manifest: CodecMatrixPreflightManifest): String =
        json.encodeToString(manifest)

    internal fun encode(manifest: CodecMatrixFinalizedManifest): String =
        json.encodeToString(manifest.validateAccepted())

    internal fun write(target: Path, manifest: CodecMatrixEligibilityManifest): CodecMatrixSha256 =
        writeBytes(target, encode(manifest).toByteArray(StandardCharsets.UTF_8))

    internal fun write(target: Path, manifest: CodecMatrixFixtureManifest): CodecMatrixSha256 =
        writeBytes(target, encode(manifest).toByteArray(StandardCharsets.UTF_8))

    internal fun write(target: Path, manifest: CodecMatrixPreflightManifest): CodecMatrixSha256 =
        writeBytes(target, encode(manifest).toByteArray(StandardCharsets.UTF_8))

    internal fun write(target: Path, manifest: CodecMatrixFinalizedManifest): CodecMatrixSha256 =
        writeBytes(target, encode(manifest).toByteArray(StandardCharsets.UTF_8))

    internal fun readEligibility(
        source: Path,
        expectedSha256: CodecMatrixSha256,
    ): CodecMatrixEligibilityManifest {
        val bytes = readVerifiedBytes(source, expectedSha256)
        val text = bytes.toString(StandardCharsets.UTF_8)
        StrictJsonScanner(text).validate()
        return try {
            json.decodeFromString<CodecMatrixEligibilityManifest>(text).validateEligibility()
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("invalid codec matrix eligibility JSON", e)
        }
    }

    internal fun readFixture(
        source: Path,
        expectedSha256: CodecMatrixSha256,
    ): CodecMatrixFixtureManifest {
        val bytes = readVerifiedBytes(source, expectedSha256)
        val text = bytes.toString(StandardCharsets.UTF_8)
        StrictJsonScanner(text).validate()
        return try {
            json.decodeFromString<CodecMatrixFixtureManifest>(text)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("invalid codec matrix fixture JSON", e)
        }
    }

    internal fun readPreflight(
        source: Path,
        expectedSha256: CodecMatrixSha256,
    ): CodecMatrixPreflightManifest {
        val bytes = readVerifiedBytes(source, expectedSha256)
        val text = bytes.toString(StandardCharsets.UTF_8)
        StrictJsonScanner(text).validate()
        return try {
            json.decodeFromString<CodecMatrixPreflightManifest>(text)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("invalid codec matrix preflight JSON", e)
        }
    }

    internal fun readFinalized(
        source: Path,
        expectedSha256: CodecMatrixSha256,
    ): CodecMatrixFinalizedManifest {
        val bytes = readVerifiedBytes(source, expectedSha256)
        val text = bytes.toString(StandardCharsets.UTF_8)
        StrictJsonScanner(text).validate()
        return try {
            json.decodeFromString<CodecMatrixFinalizedManifest>(text).validateAccepted()
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("invalid finalized codec matrix JSON", e)
        }
    }

    internal fun sha256(bytes: ByteArray): CodecMatrixSha256 {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return CodecMatrixSha256(digest.joinToString(separator = "") { byte -> "%02x".format(byte) })
    }

    private fun writeBytes(target: Path, bytes: ByteArray): CodecMatrixSha256 {
        require(bytes.size <= MAX_JSON_BYTES) { "codec matrix JSON exceeds $MAX_JSON_BYTES bytes" }
        StrictJsonScanner(bytes.toString(StandardCharsets.UTF_8)).validate()
        val parent = requireNotNull(target.parent) { "target must have a parent directory" }
        Files.createDirectories(parent)
        val temporary = parent.resolve(".${target.fileName}.tmp-${UUID.randomUUID()}")
        try {
            Files.write(temporary, bytes)
            Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
        return sha256(bytes)
    }

    private fun readVerifiedBytes(source: Path, expectedSha256: CodecMatrixSha256): ByteArray {
        require(Files.isRegularFile(source)) { "codec matrix JSON is not a regular file" }
        val size = Files.size(source)
        require(size in 1..MAX_JSON_BYTES) { "codec matrix JSON size is outside the accepted range" }
        val bytes = Files.readAllBytes(source)
        require(sha256(bytes) == expectedSha256) { "codec matrix JSON hash mismatch" }
        return bytes
    }

    private class StrictJsonScanner(
        private val text: String,
    ) {
        private var index: Int = 0

        fun validate() {
            skipWhitespace()
            scanValue(depth = 0)
            skipWhitespace()
            require(index == text.length) { "unexpected JSON content at offset $index" }
        }

        private fun scanValue(depth: Int) {
            require(depth <= MAX_JSON_DEPTH) { "JSON nesting exceeds $MAX_JSON_DEPTH" }
            skipWhitespace()
            require(index < text.length) { "unexpected end of JSON" }
            when (text[index]) {
                '{' -> scanObject(depth + 1)
                '[' -> scanArray(depth + 1)
                '"' -> scanString()
                't' -> consumeLiteral("true")
                'f' -> consumeLiteral("false")
                'n' -> consumeLiteral("null")
                else -> scanNumber()
            }
        }

        private fun scanObject(depth: Int) {
            consume('{')
            skipWhitespace()
            if (consumeIf('}')) return
            val keys = HashSet<String>()
            var count = 0
            while (true) {
                skipWhitespace()
                require(index < text.length && text[index] == '"') { "object key expected at offset $index" }
                val key = scanString()
                require(keys.add(key)) { "duplicate JSON key: $key" }
                skipWhitespace()
                consume(':')
                scanValue(depth)
                count += 1
                require(count <= MAX_JSON_COLLECTION_SIZE) { "JSON object exceeds $MAX_JSON_COLLECTION_SIZE entries" }
                skipWhitespace()
                if (consumeIf('}')) return
                consume(',')
            }
        }

        private fun scanArray(depth: Int) {
            consume('[')
            skipWhitespace()
            if (consumeIf(']')) return
            var count = 0
            while (true) {
                scanValue(depth)
                count += 1
                require(count <= MAX_JSON_COLLECTION_SIZE) { "JSON array exceeds $MAX_JSON_COLLECTION_SIZE entries" }
                skipWhitespace()
                if (consumeIf(']')) return
                consume(',')
            }
        }

        private fun scanString(): String {
            consume('"')
            val value = StringBuilder()
            while (index < text.length) {
                val current = text[index++]
                when {
                    current == '"' -> {
                        require(value.length <= MAX_JSON_STRING_LENGTH) {
                            "JSON string exceeds $MAX_JSON_STRING_LENGTH characters"
                        }
                        return value.toString()
                    }

                    current == '\\' -> value.append(scanEscape())
                    current.code < 0x20 -> throw IllegalArgumentException("control character in JSON string")
                    else -> value.append(current)
                }
                require(value.length <= MAX_JSON_STRING_LENGTH) {
                    "JSON string exceeds $MAX_JSON_STRING_LENGTH characters"
                }
            }
            throw IllegalArgumentException("unterminated JSON string")
        }

        private fun scanEscape(): Char {
            require(index < text.length) { "unterminated JSON escape" }
            return when (val escaped = text[index++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    require(index + 4 <= text.length) { "incomplete unicode escape" }
                    val digits = text.substring(index, index + 4)
                    require(digits.all { digit ->
                        digit.isDigit() || digit.lowercaseChar() in 'a'..'f'
                    }) { "invalid unicode escape" }
                    index += 4
                    digits.toInt(16).toChar()
                }

                else -> throw IllegalArgumentException("invalid JSON escape: $escaped")
            }
        }

        private fun scanNumber() {
            val start = index
            while (index < text.length && text[index] in "-+0123456789.eE") index += 1
            require(index > start) { "JSON value expected at offset $index" }
            val number = text.substring(start, index)
            require(JSON_NUMBER_REGEX.matches(number)) { "invalid JSON number: $number" }
            require(number.toDouble().isFinite()) { "non-finite JSON number" }
        }

        private fun consumeLiteral(literal: String) {
            require(text.regionMatches(index, literal, 0, literal.length)) { "invalid JSON literal at offset $index" }
            index += literal.length
        }

        private fun consume(expected: Char) {
            skipWhitespace()
            require(index < text.length && text[index] == expected) {
                "expected '$expected' at offset $index"
            }
            index += 1
        }

        private fun consumeIf(expected: Char): Boolean {
            if (index < text.length && text[index] == expected) {
                index += 1
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index += 1
        }
    }

    private val JSON_NUMBER_REGEX = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
}
