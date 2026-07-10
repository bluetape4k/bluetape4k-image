package io.bluetape4k.images.vips.java25

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsError
import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.images.vips.VipsDecodeException
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsLimits
import io.bluetape4k.images.vips.java25.internal.FfmVipsFormatSupport
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.BufferedSuspendedSource
import io.bluetape4k.okio.coroutines.SuspendedSource
import io.bluetape4k.okio.coroutines.asBlocking
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.Source
import org.apache.commons.io.input.BoundedInputStream
import java.io.File
import java.io.InputStream
import java.lang.foreign.Arena
import java.nio.file.Files
import java.nio.file.Path

private val MAX_INPUT_BYTES = VipsLimits.MAX_INPUT_BYTES

private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
private val WEBP_RIFF = byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
private val WEBP_MARKER = byteArrayOf(0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte())
private val FTYP_MARKER = byteArrayOf(0x66, 0x74, 0x79, 0x70)
private val AVIF_BRANDS = setOf("avif", "avis")
private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")

/**
 * 바이트 배열에서 [VipsImage]를 생성합니다.
 *
 * 보안 검사 순서:
 * 1. 입력 크기 제한 — 최대 50 MB
 * 2. 포맷 허용 목록 (매직 바이트) — JPEG, PNG, WebP만 허용
 * 3. maxPixels 초과 검사 (`width × height × bands`)
 *
 * @throws VipsDecodeException 지원하지 않는 포맷, 손상된 입력, 50 MB 초과, maxPixels 초과 시
 */
fun ffmVipsImageOf(bytes: ByteArray): VipsImage {
    if (bytes.size.toLong() > MAX_INPUT_BYTES) {
        throw VipsDecodeException("Input bytes exceed ${MAX_INPUT_BYTES / (1024 * 1024)} MB limit")
    }
    checkFormatAllowlist(bytes)
    return decodeAndCheckPixels(bytes)
}

/**
 * [File]에서 [VipsImage]를 생성합니다.
 *
 * **경로 탐색(Path Traversal) 주의**: 호출자는 파일 경로가 허용된 디렉토리 내에 있음을 사전에 검증해야 합니다.
 */
fun ffmVipsImageOf(file: File): VipsImage = ffmVipsImageOf(file.toPath())

/**
 * [Path]에서 [VipsImage]를 생성합니다.
 *
 * **경로 탐색(Path Traversal) 주의**: 호출자는 경로가 허용된 디렉토리 내에 있음을 사전에 검증해야 합니다.
 */
fun ffmVipsImageOf(path: Path): VipsImage {
    val bytes = readPathBytesBounded(path)
    checkFormatAllowlist(bytes)
    return decodeAndCheckPixels(bytes)
}

/**
 * [InputStream]에서 [VipsImage]를 생성합니다.
 *
 * 입력 스트림은 최대 50 MB로 제한됩니다. 초과 시 [VipsDecodeException]이 발생합니다.
 */
fun ffmVipsImageOf(stream: InputStream): VipsImage {
    val bytes = readBounded(stream)
    checkFormatAllowlist(bytes)
    return decodeAndCheckPixels(bytes)
}

/**
 * Creates a [VipsImage] from a caller-owned [BufferedSource].
 *
 * The caller owns [source] and this function does not close it. Input is still
 * bounded by the existing [InputStream] path.
 */
fun ffmVipsImageOf(source: BufferedSource): VipsImage =
    ffmVipsImageOf(source.inputStream())

/**
 * Creates a [VipsImage] from an Okio [Source].
 *
 * This overload buffers and closes [source]. Use the [BufferedSource] overload
 * when the caller must keep source ownership.
 */
fun ffmVipsImageOf(source: Source): VipsImage =
    source.buffered().use { bufferedSource ->
        ffmVipsImageOf(bufferedSource)
    }

/** [ByteArray]에서 [VipsImage]를 코루틴으로 생성합니다. */
suspend fun suspendFfmVipsImageOf(bytes: ByteArray): VipsImage =
    withContext(Dispatchers.IO) { ffmVipsImageOf(bytes) }

/** [File]에서 [VipsImage]를 코루틴으로 생성합니다. */
suspend fun suspendFfmVipsImageOf(file: File): VipsImage =
    withContext(Dispatchers.IO) { ffmVipsImageOf(file) }

/** [Path]에서 [VipsImage]를 코루틴으로 생성합니다. */
suspend fun suspendFfmVipsImageOf(path: Path): VipsImage =
    withContext(Dispatchers.IO) { ffmVipsImageOf(path) }

/**
 * Creates a [VipsImage] from a caller-owned [BufferedSource] on
 * [Dispatchers.IO].
 *
 * The caller owns [source] and must close it.
 */
suspend fun suspendFfmVipsImageOf(source: BufferedSource): VipsImage =
    withContext(Dispatchers.IO) { ffmVipsImageOf(source) }

/**
 * Creates a [VipsImage] from an Okio [Source] on [Dispatchers.IO].
 *
 * This overload buffers and closes [source].
 */
suspend fun suspendFfmVipsImageOf(source: Source): VipsImage =
    withContext(Dispatchers.IO) { ffmVipsImageOf(source) }

/**
 * Creates a [VipsImage] from a caller-owned [BufferedSuspendedSource].
 *
 * vips-ffm decoding is blocking, so this overload uses the `bluetape4k-okio`
 * blocking bridge. The caller owns [source] and must close it.
 */
suspend fun suspendFfmVipsImageOf(source: BufferedSuspendedSource): VipsImage =
    withContext(Dispatchers.IO) {
        val blockingSource = source.asBlocking().buffered()
        ffmVipsImageOf(blockingSource)
    }

/**
 * Creates a [VipsImage] from a [SuspendedSource].
 *
 * This overload buffers and closes [source].
 */
suspend fun suspendFfmVipsImageOf(source: SuspendedSource): VipsImage {
    val bufferedSource = source.bufferedSuspended()
    return try {
        suspendFfmVipsImageOf(bufferedSource)
    } finally {
        bufferedSource.close()
    }
}

// ─── internal helpers ────────────────────────────────────────────────────────

private fun readBounded(stream: InputStream): ByteArray {
    val bounded = BoundedInputStream.builder()
        .setInputStream(stream)
        .setMaxCount(MAX_INPUT_BYTES)
        .setPropagateClose(false)
        .setOnMaxCount { _, maxCount ->
            throw VipsDecodeException("Input stream exceeds ${maxCount / (1024 * 1024)} MB limit")
        }
        .get()
    return bounded.readBytes()
}

private fun readPathBytesBounded(path: Path): ByteArray =
    Files.newInputStream(path).use(::readBounded)

@OptIn(VipsIncubatingApi::class)
private fun checkFormatAllowlist(bytes: ByteArray) {
    val format = bytes.detectAllowedFormat()
        ?: throw VipsDecodeException("Unsupported image format — only JPEG, PNG, WebP, AVIF, and HEIC are allowed")
    FfmVipsFormatSupport.requireDecoding(format)
}

@OptIn(VipsIncubatingApi::class)
private fun ByteArray.detectAllowedFormat(): VipsImageFormat? {
    if (startsWith(JPEG_MAGIC)) return VipsImageFormat.JPEG
    if (startsWith(PNG_MAGIC)) return VipsImageFormat.PNG
    if (startsWith(WEBP_RIFF) && size >= 12 && regionMatches(8, WEBP_MARKER)) return VipsImageFormat.WEBP
    if (size >= 12 && regionMatches(4, FTYP_MARKER)) {
        return when (String(this, 8, 4, Charsets.US_ASCII)) {
            in AVIF_BRANDS -> VipsImageFormat.AVIF
            in HEIF_BRANDS -> VipsImageFormat.HEIC
            else -> null
        }
    }
    return null
}

private fun decodeAndCheckPixels(bytes: ByteArray): VipsImage {
    return try {
        withOwnedArena { arena ->
            val vImage = VImage.newFromBytes(arena, bytes)
            checkPixelCount(vImage)
            FfmVipsImage(arena, vImage)
        }
    } catch (e: VipsError) {
        throw VipsDecodeException("Image decode failed: unsupported format or corrupted input", e)
    }
}

internal fun <T> withOwnedArena(block: (Arena) -> T): T {
    val arena = Arena.ofShared()
    try {
        return block(arena)
    } catch (failure: Throwable) {
        closeArenaAfterFailure(arena, failure)
        throw failure
    }
}

private fun closeArenaAfterFailure(arena: Arena, failure: Throwable) {
    try {
        arena.close()
    } catch (closeFailure: Throwable) {
        failure.addSuppressed(closeFailure)
    }
}

// arena를 받지 않음: 호출자(decodeAndCheckPixels/ffmVipsImageOf)가 arena 정리 책임을 단일하게 가짐
private fun checkPixelCount(vImage: VImage) {
    val bands = vImage.getInt("bands")
        ?: throw VipsDecodeException("Failed to read bands count from decoded image")
    val pixelCount = vImage.width.toLong() * vImage.height.toLong() * bands.toLong()
    val maxPixels = FfmVipsRuntime.maxPixels
    if (pixelCount < 0 || pixelCount > maxPixels) {
        throw VipsDecodeException(
            "Image exceeds maximum pixel count: $pixelCount > $maxPixels " +
                "(width=${vImage.width}, height=${vImage.height}, bands=$bands)"
        )
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

private fun ByteArray.regionMatches(offset: Int, other: ByteArray): Boolean {
    if (size < offset + other.size) return false
    for (i in other.indices) {
        if (this[offset + i] != other[i]) return false
    }
    return true
}
