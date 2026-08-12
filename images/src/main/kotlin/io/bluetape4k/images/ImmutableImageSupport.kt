package io.bluetape4k.images

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.analysis.ImageMetadataReadOptions
import io.bluetape4k.images.analysis.ImageMetadataReadResult
import io.bluetape4k.images.analysis.readImageMetadataReportStrict
import io.bluetape4k.images.coroutines.SuspendImageWriter
import io.bluetape4k.images.coroutines.SuspendWriteContext
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.BufferedSuspendedSink
import io.bluetape4k.okio.coroutines.BufferedSuspendedSource
import io.bluetape4k.okio.coroutines.SuspendedSink
import io.bluetape4k.okio.coroutines.SuspendedSource
import io.bluetape4k.okio.coroutines.asBlocking
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import kotlinx.coroutines.CancellationException
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
 * 리소스 한계를 적용한 뒤 인코딩 바이트에서 [ImmutableImage]를 읽습니다.
 *
 * ## 동작/계약
 * - 어떤 디코딩 작업보다 먼저 [ImageDecodeLimits.maxEncodedBytes]를 확인합니다.
 * - ImageIO reader가 payload를 식별할 수 있으면 전체 픽셀 디코딩 전에 헤더 기반
 *   크기를 확인합니다.
 * - Scrimage가 이미지를 반환한 뒤 디코딩된 크기를 다시 확인합니다.
 *
 * 기존 bounded overload는 source compatibility와 신뢰된 in-process payload를 위해
 * 유지됩니다. 신뢰하지 않는 외부 입력은 [immutableExternalImageOf]를 사용합니다.
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
 * 신뢰하지 않는 외부 입력을 strict하게 검증한 뒤 [ImmutableImage]로 읽습니다.
 *
 * ImageIO가 헤더를 읽지 못하는 포맷은 제한 없는 decoder 호출로 넘기지 않습니다.
 * bounded metadata reader를 한 번 더 사용해 크기를 확인하고, 두 경로 모두 실패하면
 * decode 전에 거부합니다. 기존 [immutableImageOf] overload의 동작은 유지됩니다.
 */
fun immutableExternalImageOf(
    bytes: ByteArray,
    limits: ImageDecodeLimits = ImageDecodeLimits.ExternalInput,
): ImmutableImage {
    bytes.requireWithinEncodedLimit(limits, "Image input")

    val dimensions = probeImageDimensions(bytes)
        ?: readImageMetadataDimensions(bytes, limits)
        ?: throw IllegalArgumentException("Image input dimensions could not be determined.")
    dimensions.requireWithinDecodeLimits(limits, "Image input")

    return try {
        ImmutableImage.loader().fromBytes(bytes)
            .also { it.requireWithinDecodeLimits(limits, "Decoded image") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("Image input could not be decoded.", e)
    }
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
 * 리소스 한계를 적용한 뒤 [InputStream]에서 [ImmutableImage]를 읽습니다.
 *
 * 호출자가 소유한 stream에서 최대 [ImageDecodeLimits.maxEncodedBytes]보다 한 바이트
 * 많은 양까지만 읽은 뒤, 한계가 적용되는 byte-array overload에 위임합니다.
 * stream lifecycle은 계속 호출자가 소유합니다.
 */
fun immutableImageOf(
    inputStream: InputStream,
    limits: ImageDecodeLimits,
): ImmutableImage =
    immutableImageOf(inputStream.readBoundedImageBytes(limits), limits)

/**
 * 호출자가 소유한 [BufferedSource]에서 [ImmutableImage]를 읽습니다.
 *
 * ## 동작/계약
 * - [BufferedSource.inputStream]을 통해 source를 Scrimage에 맞게 연결합니다.
 * - source를 닫는 책임은 호출자에게 있습니다.
 *
 * ```kotlin
 * File("image.jpg").inputStream().asSource().buffered().use { source ->
 *     val image = immutableImageOf(source)
 * }
 * ```
 *
 * @param source 버퍼링된 이미지 source
 * @return 디코딩된 [ImmutableImage]
 */
fun immutableImageOf(source: BufferedSource): ImmutableImage =
    ImmutableImage.loader().fromStream(source.inputStream())

/**
 * Okio [Source]에서 [ImmutableImage]를 읽습니다.
 *
 * ## 동작/계약
 * - 이 함수는 [source]를 buffer하고 닫습니다.
 * - 호출자가 source lifecycle을 계속 소유해야 한다면 [BufferedSource]를 받는
 *   [immutableImageOf]를 사용합니다.
 *
 * @param source 이미지 source
 * @return 디코딩된 [ImmutableImage]
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
 * 리소스 한계를 적용한 뒤 [File]에서 [ImmutableImage]를 읽습니다.
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
 * 리소스 한계를 적용한 뒤 [Path]에서 [ImmutableImage]를 읽습니다.
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
 * 호출자가 소유한 [BufferedSource]에서 [Dispatchers.IO] 위로 [ImmutableImage]를 읽습니다.
 *
 * @param source 버퍼링된 이미지 source
 * @return 디코딩된 [ImmutableImage]
 */
suspend fun suspendImmutableImageOf(source: BufferedSource): ImmutableImage =
    withContext(Dispatchers.IO) {
        immutableImageOf(source)
    }

/**
 * Okio [Source]에서 [Dispatchers.IO] 위로 [ImmutableImage]를 읽습니다.
 *
 * 이 overload는 [source]를 buffer하고 닫습니다.
 *
 * @param source 이미지 source
 * @return 디코딩된 [ImmutableImage]
 */
suspend fun suspendImmutableImageOf(source: Source): ImmutableImage =
    withContext(Dispatchers.IO) {
        immutableImageOf(source)
    }

/**
 * 호출자가 소유한 [BufferedSuspendedSource]에서 [Dispatchers.IO] 위로
 * [ImmutableImage]를 읽습니다.
 *
 * Scrimage decoder는 blocking 방식이므로, 이 overload는 호출자가 source lifecycle을
 * 소유한 상태를 보존하면서 suspended source를 blocking Okio source로 연결합니다.
 *
 * @param source 버퍼링된 suspended 이미지 source
 * @return 디코딩된 [ImmutableImage]
 */
suspend fun suspendImmutableImageOf(source: BufferedSuspendedSource): ImmutableImage =
    withContext(Dispatchers.IO) {
        val blockingSource = source.asBlocking().buffered()
        ImmutableImage.loader().fromStream(blockingSource.inputStream())
    }

/**
 * [SuspendedSource]에서 [Dispatchers.IO] 위로 [ImmutableImage]를 읽습니다.
 *
 * 이 overload는 [source]를 buffer하고 닫습니다.
 *
 * @param source suspend 기반 이미지 source 입력
 * @return 디코딩된 [ImmutableImage]
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
 * 호출자가 소유한 [BufferedSource]에서 [Dispatchers.IO] 위로 [ImmutableImage]를 읽습니다.
 *
 * @param source 버퍼링된 이미지 source
 * @return 디코딩된 [ImmutableImage]
 */
suspend fun suspendLoadImage(source: BufferedSource): ImmutableImage =
    suspendImmutableImageOf(source)

/**
 * Okio [Source]에서 [Dispatchers.IO] 위로 [ImmutableImage]를 읽습니다.
 *
 * 이 overload는 [source]를 buffer하고 닫습니다.
 *
 * @param source 이미지 source
 * @return 디코딩된 [ImmutableImage]
 */
suspend fun suspendLoadImage(source: Source): ImmutableImage =
    suspendImmutableImageOf(source)

/**
 * 호출자가 소유한 [BufferedSuspendedSource]에서 [Dispatchers.IO] 위로
 * [ImmutableImage]를 읽습니다.
 *
 * @param source 버퍼링된 suspended 이미지 source
 * @return 디코딩된 [ImmutableImage]
 */
suspend fun suspendLoadImage(source: BufferedSuspendedSource): ImmutableImage =
    suspendImmutableImageOf(source)

/**
 * [SuspendedSource]에서 [Dispatchers.IO] 위로 [ImmutableImage]를 읽습니다.
 *
 * 이 overload는 [source]를 buffer하고 닫습니다.
 *
 * @param source suspend 기반 이미지 source 입력
 * @return 디코딩된 [ImmutableImage]
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
 * 이 [ImmutableImage]를 호출자가 소유한 [BufferedSink]에 씁니다.
 *
 * ## 동작/계약
 * - 내부 writer가 사용할 수 있도록 sink를 [java.io.OutputStream]으로 연결합니다.
 * - sink는 flush하지만 닫지 않습니다.
 *
 * @param writer 이미지 writer
 * @param sink 버퍼링된 출력 sink
 */
suspend fun ImmutableImage.suspendWrite(writer: SuspendImageWriter, sink: BufferedSink) {
    writer.suspendWrite(this, this.metadata, sink.outputStream())
    sink.flush()
}

/**
 * 이 [ImmutableImage]를 Okio [Sink]에 씁니다.
 *
 * 이 overload는 [sink]를 buffer하고 닫습니다. 호출자가 sink lifecycle을 계속
 * 소유해야 한다면 [BufferedSink] overload를 사용합니다.
 *
 * @param writer 이미지 writer
 * @param sink 출력 sink
 */
suspend fun ImmutableImage.suspendWrite(writer: SuspendImageWriter, sink: Sink) {
    sink.buffered().use { bufferedSink ->
        suspendWrite(writer, bufferedSink)
    }
}

/**
 * 이 [ImmutableImage]를 호출자가 소유한 [BufferedSuspendedSink]에 씁니다.
 *
 * Scrimage encoder는 blocking 방식이므로, 이 overload는 호출자가 sink lifecycle을
 * 소유한 상태를 보존하면서 suspended sink를 blocking Okio sink로 연결합니다.
 *
 * @param writer 이미지 writer
 * @param sink 버퍼링된 suspended 출력 sink
 */
suspend fun ImmutableImage.suspendWrite(writer: SuspendImageWriter, sink: BufferedSuspendedSink) {
    val blockingSink = sink.asBlocking().buffered()
    writer.suspendWrite(this, this.metadata, blockingSink.outputStream())
    blockingSink.flush()
}

/**
 * 이 [ImmutableImage]를 [SuspendedSink]에 씁니다.
 *
 * 이 overload는 [sink]를 buffer하고 닫습니다. 호출자가 sink lifecycle을 계속
 * 소유해야 한다면 [BufferedSuspendedSink] overload를 사용합니다.
 *
 * @param writer 이미지 writer
 * @param sink suspend 기반 출력 sink
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
 * @return [SuspendWriteContext] 인스턴스
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

private fun readImageMetadataDimensions(
    bytes: ByteArray,
    limits: ImageDecodeLimits,
): ImageDimensions? {
    val maxBytes = limits.maxEncodedBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return when (
        val result = readImageMetadataReportStrict(
            bytes,
            ImageMetadataReadOptions(maxBytes = maxBytes),
        )
    ) {
        is ImageMetadataReadResult.Success -> result.report.dimensions
        is ImageMetadataReadResult.Failure -> null
    }
}

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
