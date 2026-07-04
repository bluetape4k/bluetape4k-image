package io.bluetape4k.images

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.coroutines.SuspendImageWriter
import io.bluetape4k.images.coroutines.SuspendWriteContext
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.BufferedSuspendedSink
import io.bluetape4k.okio.coroutines.BufferedSuspendedSource
import io.bluetape4k.okio.coroutines.SuspendedSink
import io.bluetape4k.okio.coroutines.SuspendedSource
import io.bluetape4k.okio.coroutines.asBlocking
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.Sink
import okio.Source
import java.awt.Graphics2D
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

@PublishedApi
internal const val IMAGE_BUFFER_SIZE: Int = 128 * 1024

/**
 * [ByteArray]를 읽어 [ImmutableImage]로 변환합니다.
 *
 * ## 동작/계약
 * - Scrimage loader를 사용해 메모리 바이트를 디코딩합니다.
 * - 디코딩 실패 시 loader 예외가 전파됩니다.
 *
 * ```kotlin
 * val image = immutableImageOf(bytes)
 * // image.width > 0
 * ```
 *
 * @param bytes 이미지 정보를 담은 [ByteArray]
 * @return 이미지 정보를 담은 [ImmutableImage]
 */
fun immutableImageOf(bytes: ByteArray): ImmutableImage =
    ImmutableImage.loader().fromBytes(bytes)

/**
 * Loads an [ImmutableImage] from encoded bytes after applying resource limits.
 *
 * ## Contract
 * - [ImageDecodeLimits.maxEncodedBytes] is checked before any decode work.
 * - Header-derived dimensions are checked before full pixel decode when an
 *   ImageIO reader can identify the payload.
 * - Decoded dimensions are checked again after Scrimage returns the image.
 *
 * Use this overload at external input boundaries. The one-argument overload is
 * preserved for source compatibility and trusted in-process payloads.
 */
fun immutableImageOf(
    bytes: ByteArray,
    limits: ImageDecodeLimits,
): ImmutableImage {
    bytes.requireWithinEncodedLimit(limits, "Image input")
    probeImageDimensions(bytes)?.requireWithinDecodeLimits(limits, "Image input")

    return ImmutableImage.loader().fromBytes(bytes)
        .also { it.requireWithinDecodeLimits(limits, "Decoded image") }
}

/**
 * [InputStream]을 읽어 [ImmutableImage]로 변환합니다.
 *
 * ## 동작/계약
 * - 내부적으로 `buffered()` 스트림을 사용합니다.
 * - 스트림 close는 호출자가 관리해야 합니다.
 *
 * ```kotlin
 * val image = immutableImageOf(File("image.jpg").inputStream())
 * // image.height > 0
 * ```
 *
 * @param inputStream 이미지 정보를 담은 [InputStream]
 * @return 이미지 정보를 담은 [ImmutableImage]
 */
fun immutableImageOf(inputStream: InputStream): ImmutableImage =
    ImmutableImage.loader().fromStream(inputStream.buffered())

/**
 * Loads an [ImmutableImage] from an [InputStream] after applying resource limits.
 *
 * This overload reads at most [ImageDecodeLimits.maxEncodedBytes] plus one byte
 * from the caller-owned stream, then delegates to the bounded byte-array
 * overload. The stream lifecycle remains caller-owned.
 */
fun immutableImageOf(
    inputStream: InputStream,
    limits: ImageDecodeLimits,
): ImmutableImage =
    immutableImageOf(inputStream.readBoundedImageBytes(limits), limits)

/**
 * Loads an [ImmutableImage] from a caller-owned [BufferedSource].
 *
 * ## Contract
 * - The source is adapted to Scrimage through [BufferedSource.inputStream].
 * - The caller owns source closing.
 *
 * ```kotlin
 * File("image.jpg").inputStream().asSource().buffered().use { source ->
 *     val image = immutableImageOf(source)
 * }
 * ```
 *
 * @param source buffered image source
 * @return decoded [ImmutableImage]
 */
fun immutableImageOf(source: BufferedSource): ImmutableImage =
    ImmutableImage.loader().fromStream(source.inputStream())

/**
 * Loads an [ImmutableImage] from an Okio [Source].
 *
 * ## Contract
 * - This function buffers and closes [source].
 * - Use [immutableImageOf] with [BufferedSource] when the caller must keep
 *   ownership of the source lifecycle.
 *
 * @param source image source
 * @return decoded [ImmutableImage]
 */
fun immutableImageOf(source: Source): ImmutableImage =
    source.buffered().use { bufferedSource ->
        immutableImageOf(bufferedSource)
    }

/**
 * [File]을 읽어 [ImmutableImage]로 변환합니다.
 *
 * ## 동작/계약
 * - 파일 읽기 실패 시 예외가 전파됩니다.
 *
 * ```kotlin
 * val image = immutableImageOf(file)
 * // image.width > 0
 * ```
 *
 * @param file 이미지 파일
 * @return 이미지 정보를 담은 [ImmutableImage]
 */
fun immutableImageOf(file: File): ImmutableImage =
    ImmutableImage.loader().fromFile(file)

/**
 * Loads an [ImmutableImage] from a [File] after applying resource limits.
 */
fun immutableImageOf(
    file: File,
    limits: ImageDecodeLimits,
): ImmutableImage =
    immutableImageOf(file.toPath(), limits)

/**
 * [Path]의 파일을 읽어 [ImmutableImage]로 변환합니다.
 *
 * ## 동작/계약
 * - [Path]를 그대로 Scrimage loader에 전달합니다.
 *
 * ```kotlin
 * val image = immutableImageOf(path)
 * // image.height > 0
 * ```
 *
 * @param path 이미지 파일의 경로
 * @return 이미지 정보를 담은 [ImmutableImage]
 */
fun immutableImageOf(path: Path): ImmutableImage =
    ImmutableImage.loader().fromPath(path)

/**
 * Loads an [ImmutableImage] from a [Path] after applying resource limits.
 */
fun immutableImageOf(
    path: Path,
    limits: ImageDecodeLimits,
): ImmutableImage {
    val size = Files.size(path)
    require(size <= limits.maxEncodedBytes) {
        "Image input encodedBytes=$size exceeds maxEncodedBytes=${limits.maxEncodedBytes}."
    }
    probeImageDimensions(path)?.requireWithinDecodeLimits(limits, "Image input")

    return ImmutableImage.loader().fromPath(path)
        .also { it.requireWithinDecodeLimits(limits, "Decoded image") }
}

/**
 * Coroutines 환경에서 [File]을 읽어 [ImmutableImage]로 변환합니다.
 *
 * ## 동작/계약
 * - 내부적으로 [suspendImmutableImageOf] 경로 변환 버전을 호출합니다.
 *
 * ```kotlin
 * val image = suspendImmutableImageOf(file)
 * // image.width > 0
 * ```
 *
 * @param file 이미지 파일
 * @return 이미지 정보를 담은 [ImmutableImage]
 */
suspend fun suspendImmutableImageOf(file: File): ImmutableImage =
    suspendImmutableImageOf(file.toPath())

/**
 * Coroutines 환경에서 [Path]의 파일을 읽어 [ImmutableImage]로 변환합니다.
 *
 * ## 동작/계약
 * - [Path]를 Scrimage loader에 직접 전달해 압축 파일 전체를 [ByteArray]로 복사하지 않습니다.
 *
 * ```kotlin
 * val image = suspendImmutableImageOf(path)
 * // image.height > 0
 * ```
 *
 * @param path 이미지 파일의 경로
 * @return 이미지 정보를 담은 [ImmutableImage]
 */
suspend fun suspendImmutableImageOf(path: Path): ImmutableImage =
    withContext(Dispatchers.IO) {
        immutableImageOf(path)
    }

/**
 * Loads an [ImmutableImage] from a caller-owned [BufferedSource] on
 * [Dispatchers.IO].
 *
 * @param source buffered image source
 * @return decoded [ImmutableImage]
 */
suspend fun suspendImmutableImageOf(source: BufferedSource): ImmutableImage =
    withContext(Dispatchers.IO) {
        immutableImageOf(source)
    }

/**
 * Loads an [ImmutableImage] from an Okio [Source] on [Dispatchers.IO].
 *
 * This overload buffers and closes [source].
 *
 * @param source image source
 * @return decoded [ImmutableImage]
 */
suspend fun suspendImmutableImageOf(source: Source): ImmutableImage =
    withContext(Dispatchers.IO) {
        immutableImageOf(source)
    }

/**
 * Loads an [ImmutableImage] from a caller-owned [BufferedSuspendedSource] on
 * [Dispatchers.IO].
 *
 * Scrimage decoders are blocking, so this overload bridges the suspended source
 * to a blocking Okio source while preserving the caller-owned source lifecycle.
 *
 * @param source buffered suspended image source
 * @return decoded [ImmutableImage]
 */
suspend fun suspendImmutableImageOf(source: BufferedSuspendedSource): ImmutableImage =
    withContext(Dispatchers.IO) {
        val blockingSource = source.asBlocking().buffered()
        ImmutableImage.loader().fromStream(blockingSource.inputStream())
    }

/**
 * Loads an [ImmutableImage] from a [SuspendedSource] on [Dispatchers.IO].
 *
 * This overload buffers and closes [source].
 *
 * @param source suspended image source
 * @return decoded [ImmutableImage]
 */
suspend fun suspendImmutableImageOf(source: SuspendedSource): ImmutableImage {
    val bufferedSource = source.bufferedSuspended()
    return try {
        suspendImmutableImageOf(bufferedSource)
    } finally {
        bufferedSource.close()
    }
}

/**
 * Coroutines 환경에서 [File]을 읽어 [ImmutableImage]로 변환합니다.
 *
 * ## 동작/계약
 * - [suspendLoadImage]의 `File` 오버로드는 Path 오버로드에 위임합니다.
 *
 * ```kotlin
 * val image = suspendLoadImage(file)
 * // image.width > 0
 * ```
 *
 * @param file 이미지 파일
 * @return 이미지 정보를 담은 [ImmutableImage]
 */
suspend fun suspendLoadImage(file: File): ImmutableImage =
    suspendLoadImage(file.toPath())

/**
 * Coroutines 환경에서 [Path]의 파일을 읽어 [ImmutableImage]로 변환합니다.
 *
 * ## 동작/계약
 * - [Path]를 Scrimage loader에 직접 전달해 압축 파일 전체를 [ByteArray]로 복사하지 않습니다.
 *
 * ```kotlin
 * val image = suspendLoadImage(path)
 * // image.height > 0
 * ```
 *
 * @param path 이미지 파일의 경로
 * @return 이미지 정보를 담은 [ImmutableImage]
 */
suspend fun suspendLoadImage(path: Path): ImmutableImage =
    withContext(Dispatchers.IO) {
        immutableImageOf(path)
    }

/**
 * Loads an [ImmutableImage] from a caller-owned [BufferedSource] on
 * [Dispatchers.IO].
 *
 * @param source buffered image source
 * @return decoded [ImmutableImage]
 */
suspend fun suspendLoadImage(source: BufferedSource): ImmutableImage =
    suspendImmutableImageOf(source)

/**
 * Loads an [ImmutableImage] from an Okio [Source] on [Dispatchers.IO].
 *
 * This overload buffers and closes [source].
 *
 * @param source image source
 * @return decoded [ImmutableImage]
 */
suspend fun suspendLoadImage(source: Source): ImmutableImage =
    suspendImmutableImageOf(source)

/**
 * Loads an [ImmutableImage] from a caller-owned [BufferedSuspendedSource] on
 * [Dispatchers.IO].
 *
 * @param source buffered suspended image source
 * @return decoded [ImmutableImage]
 */
suspend fun suspendLoadImage(source: BufferedSuspendedSource): ImmutableImage =
    suspendImmutableImageOf(source)

/**
 * Loads an [ImmutableImage] from a [SuspendedSource] on [Dispatchers.IO].
 *
 * This overload buffers and closes [source].
 *
 * @param source suspended image source
 * @return decoded [ImmutableImage]
 */
suspend fun suspendLoadImage(source: SuspendedSource): ImmutableImage =
    suspendImmutableImageOf(source)

/**
 * Coroutines 환경에서 [ImmutableImage] 정보를 [writer]를 통해 [ByteArray]로 변환합니다.
 *
 * ## 동작/계약
 * - `ByteArrayOutputStream`을 새로 할당해 인코딩 결과를 반환합니다.
 * - writer 예외는 호출자에게 그대로 전파됩니다.
 *
 * ```kotlin
 * val bytes = image.suspendBytes(writer)
 * // bytes.isNotEmpty() == true
 * ```
 *
 * @param writer 이미지를 쓰기 위한 [SuspendImageWriter]
 * @return 이미지 정보를 담은 ByteArray
 */
suspend inline fun ImmutableImage.suspendBytes(writer: SuspendImageWriter): ByteArray =
    ByteArrayOutputStream(IMAGE_BUFFER_SIZE).use { bos ->
        writer.suspendWrite(this, this.metadata, bos)
        bos.toByteArray()
    }

/**
 * Coroutines 환경에서 [ImmutableImage] 정보를 [writer]를 통해 [destPath]에 저장합니다.
 *
 * ## 동작/계약
 * - 대상 파일 [OutputStream]에 직접 인코딩해 중간 [ByteArray] 복사를 피합니다.
 * - 반환값은 기록된 바이트 수입니다.
 *
 * ```kotlin
 * val written = image.suspendWrite(writer, path)
 * // written > 0L
 * ```
 *
 * @param writer 이미지를 쓰기 위한 [SuspendImageWriter]
 * @param destPath 저장할 파일의 경로
 * @return 저장된 파일의 크기
 */
suspend fun ImmutableImage.suspendWrite(writer: SuspendImageWriter, destPath: Path): Long {
    withContext(Dispatchers.IO) {
        Files.newOutputStream(destPath).use { out ->
            writer.suspendWrite(this@suspendWrite, this@suspendWrite.metadata, out)
        }
    }
    return Files.size(destPath)
}

/**
 * Writes this [ImmutableImage] to a caller-owned [BufferedSink].
 *
 * ## Contract
 * - The sink is adapted to [java.io.OutputStream] for the underlying writer.
 * - The sink is flushed, but not closed.
 *
 * @param writer image writer
 * @param sink buffered output sink
 */
suspend fun ImmutableImage.suspendWrite(writer: SuspendImageWriter, sink: BufferedSink) {
    writer.suspendWrite(this, this.metadata, sink.outputStream())
    sink.flush()
}

/**
 * Writes this [ImmutableImage] to an Okio [Sink].
 *
 * This overload buffers and closes [sink]. Use the [BufferedSink] overload when
 * the caller must keep ownership of the sink lifecycle.
 *
 * @param writer image writer
 * @param sink output sink
 */
suspend fun ImmutableImage.suspendWrite(writer: SuspendImageWriter, sink: Sink) {
    sink.buffered().use { bufferedSink ->
        suspendWrite(writer, bufferedSink)
    }
}

/**
 * Writes this [ImmutableImage] to a caller-owned [BufferedSuspendedSink].
 *
 * Scrimage encoders are blocking, so this overload bridges the suspended sink to
 * a blocking Okio sink while preserving the caller-owned sink lifecycle.
 *
 * @param writer image writer
 * @param sink buffered suspended output sink
 */
suspend fun ImmutableImage.suspendWrite(writer: SuspendImageWriter, sink: BufferedSuspendedSink) {
    val blockingSink = sink.asBlocking().buffered()
    writer.suspendWrite(this, this.metadata, blockingSink.outputStream())
    blockingSink.flush()
}

/**
 * Writes this [ImmutableImage] to a [SuspendedSink].
 *
 * This overload buffers and closes [sink]. Use the [BufferedSuspendedSink]
 * overload when the caller must keep ownership of the sink lifecycle.
 *
 * @param writer image writer
 * @param sink suspended output sink
 */
suspend fun ImmutableImage.suspendWrite(writer: SuspendImageWriter, sink: SuspendedSink) {
    val bufferedSink = sink.bufferedSuspended()
    try {
        suspendWrite(writer, bufferedSink)
    } finally {
        bufferedSink.close()
    }
}

/**
 * [ImmutableImage] 정보를 쓰기 작업을 위해 [writer]를 사용하는 [SuspendWriteContext]를 생성합니다.
 *
 * ## 동작/계약
 * - 실제 인코딩/출력은 수행하지 않고 컨텍스트만 생성합니다.
 *
 * ```kotlin
 * val context = image.forSuspendWriter(writer)
 * // context != null
 * ```
 *
 * @param writer 이미지를 쓰기 위한 [SuspendImageWriter]
 * @return [SuspendWriteContext] instance
 */
fun ImmutableImage.forSuspendWriter(writer: SuspendImageWriter): SuspendWriteContext =
    SuspendWriteContext(writer, this, this.metadata)

private fun ByteArray.requireWithinEncodedLimit(limits: ImageDecodeLimits, subject: String) {
    require(size.toLong() <= limits.maxEncodedBytes) {
        "$subject encodedBytes=$size exceeds maxEncodedBytes=${limits.maxEncodedBytes}."
    }
}

private fun ImageDimensions.requireWithinDecodeLimits(
    limits: ImageDecodeLimits,
    subject: String,
): ImageDimensions =
    requireMaxPixels(limits.maxDecodedPixels, subject)
        .requireMaxSide(limits.maxDecodedSide, subject)

private fun ImmutableImage.requireWithinDecodeLimits(
    limits: ImageDecodeLimits,
    subject: String,
): ImmutableImage {
    ImageDimensions(width, height).requireWithinDecodeLimits(limits, subject)
    return this
}

private fun InputStream.readBoundedImageBytes(limits: ImageDecodeLimits): ByteArray {
    val output = ByteArrayOutputStream(
        limits.maxEncodedBytes.coerceAtMost(IMAGE_BUFFER_SIZE.toLong()).toInt()
    )
    val buffer = ByteArray(IMAGE_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read.toLong()
        require(total <= limits.maxEncodedBytes) {
            "Image input encodedBytes=$total exceeds maxEncodedBytes=${limits.maxEncodedBytes}."
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}


/**
 * [ImmutableImage]의 복사본에 그리기 작업 ([action])을 수행하고 새 [ImmutableImage]를 반환합니다.
 *
 * 원본 이미지는 변경되지 않습니다. `Graphics2D`는 `finally`에서 항상 `dispose()`됩니다.
 *
 * ```kotlin
 * val annotated = image.withGraphics { g ->
 *     g.color = Color.RED
 *     g.drawRect(0, 0, 10, 10)
 * }
 * // image 는 변경되지 않고 annotated 에 사각형이 그려진 새 이미지가 반환된다.
 * ```
 *
 * @param action 복사본에 적용할 그래픽 작업
 * @return 그래픽 작업이 적용된 새 [ImmutableImage]
 */
inline fun ImmutableImage.withGraphics(
    action: (graphics: Graphics2D) -> Unit,
): ImmutableImage {
    val copy = this.copy()
    val graphics: Graphics2D = copy.awt().createGraphics()
    try {
        action(graphics)
    } finally {
        graphics.dispose()
    }
    return copy
}
