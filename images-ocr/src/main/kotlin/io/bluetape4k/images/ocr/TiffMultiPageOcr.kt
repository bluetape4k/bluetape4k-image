package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.IIORegistryUtils
import io.bluetape4k.images.ImageDecodeLimits
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.support.requirePositiveNumber
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream
import kotlin.jvm.JvmSynthetic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/**
 * 다중 페이지 TIFF OCR에 적용하는 입력·metadata·결과 resource budget입니다.
 *
 * 모든 값은 양수여야 하며, metadata preflight가 완료되기 전에는 어떤 page도 decode하거나
 * OCR engine에 전달하지 않습니다.
 */
data class TiffMultiPageOcrLimits @JvmOverloads constructor(
    val maxEncodedBytes: Long = ImageDecodeLimits.DEFAULT_MAX_ENCODED_BYTES,
    val maxPages: Int = 16,
    val maxPixelsPerPage: Long = ImageDecodeLimits.DEFAULT_MAX_DECODED_PIXELS,
    val maxTotalPixels: Long = 64_000_000L,
    val maxDecodedSide: Int = ImageDecodeLimits.DEFAULT_MAX_DECODED_SIDE,
    val maxMetadataBytes: Long = 2L * 1024L * 1024L,
    val maxResultTextChars: Int = 1_000_000,
    val maxResultEntries: Int = 100_000,
) : Serializable {

    init {
        maxEncodedBytes.requirePositiveNumber("maxEncodedBytes")
        maxPages.requirePositiveNumber("maxPages")
        maxPixelsPerPage.requirePositiveNumber("maxPixelsPerPage")
        maxTotalPixels.requirePositiveNumber("maxTotalPixels")
        maxDecodedSide.requirePositiveNumber("maxDecodedSide")
        maxMetadataBytes.requirePositiveNumber("maxMetadataBytes")
        maxResultTextChars.requirePositiveNumber("maxResultTextChars")
        maxResultEntries.requirePositiveNumber("maxResultEntries")
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** TIFF multi-page OCR 입력을 거부한 안정적인 이유 코드입니다. */
enum class TiffMultiPageOcrFailureReason {
    INPUT_TOO_LARGE,
    READER_UNAVAILABLE,
    UNSUPPORTED_FORMAT,
    PAGE_COUNT_UNKNOWN,
    PAGE_LIMIT_EXCEEDED,
    DIMENSIONS_UNAVAILABLE,
    SIDE_LIMIT_EXCEEDED,
    PIXELS_PER_PAGE_LIMIT_EXCEEDED,
    TOTAL_PIXELS_LIMIT_EXCEEDED,
    METADATA_LIMIT_EXCEEDED,
    DECODE_FAILED,
    ENGINE_FAILED,
    RESULT_LIMIT_EXCEEDED,
}

/** TIFF OCR metadata 또는 resource budget validation 실패입니다. */
class TiffMultiPageOcrValidationException(
    val reason: TiffMultiPageOcrFailureReason,
    val pageIndex: Int?,
    message: String,
) : IllegalArgumentException(message), Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** TIFF OCR decode 또는 provider 실행 실패입니다. */
class TiffMultiPageOcrException(
    val reason: TiffMultiPageOcrFailureReason,
    val pageIndex: Int?,
    message: String,
) : OcrException(message), Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * ByteArray TIFF를 순차적으로 structured OCR합니다.
 *
 * metadata preflight는 하나의 ImageIO reader와 stream에서 완료되며, 모든 page가 검증된 뒤
 * 같은 reader/stream을 payload phase로 전환합니다. GIF animation과 parallel page OCR은
 * 이 API의 범위가 아닙니다.
 */
class TiffMultiPageOcr private constructor(
    private val engine: StructuredOcrEngine,
    private val inputFactory: TiffImageInputFactory,
    private val readerFactory: TiffImageReaderFactory,
) {

    /** 기본 reader와 engine을 사용하는 public Java/Kotlin 진입점입니다. */
    @JvmOverloads
    constructor(engine: StructuredOcrEngine = TesseractOcrEngine()) :
        this(engine, DefaultTiffImageInputFactory, DefaultTiffImageReaderFactory)

    /**
     * TIFF 전체를 preflight한 뒤 page 순서대로 OCR합니다.
     *
     * 실패 시 partial result를 반환하지 않으며, public exception에는 원본 payload·경로·native
     * provider cause를 포함하지 않습니다.
     */
    fun recognize(
        bytes: ByteArray,
        options: OcrOptions = OcrOptions(),
        limits: TiffMultiPageOcrLimits = TiffMultiPageOcrLimits(),
    ): OcrStructuredResult {
        validateEncodedSize(bytes, limits)

        var session: TiffImageSession? = null
        var primary: Throwable? = null
        try {
            session = openSession(bytes, limits)
            val pages = preflight(session, limits)
            session.input.allowPayloadReads()
            return processPages(session, pages, options, limits)
        } catch (e: CancellationException) {
            primary = e
            throw e
        } catch (e: TiffMultiPageOcrValidationException) {
            primary = e
            throw e
        } catch (e: TiffMultiPageOcrException) {
            primary = e
            throw e
        } catch (e: Exception) {
            val mapped = TiffMultiPageOcrValidationException(
                TiffMultiPageOcrFailureReason.PAGE_COUNT_UNKNOWN,
                null,
                "TIFF OCR metadata could not be read.",
            )
            primary = mapped
            throw mapped
        } finally {
            closeSession(session, primary)
        }
    }

    /**
     * [recognize]의 suspend variant입니다. blocking reader와 engine 호출은 [dispatcher]에서
     * 실행되며, cancellation은 page 경계에서 확인됩니다. native provider가 interrupt를 무시할
     * 수 있으므로 caller가 timeout을 함께 설정해야 합니다.
     */
    suspend fun suspendRecognize(
        bytes: ByteArray,
        options: OcrOptions = OcrOptions(),
        limits: TiffMultiPageOcrLimits = TiffMultiPageOcrLimits(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): OcrStructuredResult {
        validateEncodedSize(bytes, limits)

        var session: TiffImageSession? = null
        var primary: Throwable? = null
        try {
            // session을 만든 뒤 caller cancellation이 발생해도 finally가 소유권을 유지하도록
            // open 단계만 non-cancellable로 완료합니다. metadata/page 작업은 계속 interruptible입니다.
            val opened = withContext(NonCancellable + dispatcher) { openSession(bytes, limits) }
            session = opened
            currentCoroutineContext().ensureActive()
            val pages = runInterruptible(dispatcher) { preflight(opened, limits) }
            currentCoroutineContext().ensureActive()
            opened.input.allowPayloadReads()

            val aggregate = AggregateResult(options, limits)
            pages.forEach { page ->
                currentCoroutineContext().ensureActive()
                val result = runInterruptible(dispatcher) {
                    processPage(opened, page, options, limits)
                }
                aggregate.append(page.index, result)
            }
            return aggregate.toResult()
        } catch (e: CancellationException) {
            primary = e
            throw e
        } catch (e: TiffMultiPageOcrValidationException) {
            primary = e
            throw e
        } catch (e: TiffMultiPageOcrException) {
            primary = e
            throw e
        } catch (e: Exception) {
            val mapped = TiffMultiPageOcrValidationException(
                TiffMultiPageOcrFailureReason.PAGE_COUNT_UNKNOWN,
                null,
                "TIFF OCR metadata could not be read.",
            )
            primary = mapped
            throw mapped
        } finally {
            withContext(NonCancellable + dispatcher) {
                closeSession(session, primary)
            }
        }
    }

    private fun openSession(bytes: ByteArray, limits: TiffMultiPageOcrLimits): TiffImageSession {
        if (limits.maxMetadataBytes < TIFF_HEADER_BYTES) {
            throw validation(TiffMultiPageOcrFailureReason.METADATA_LIMIT_EXCEEDED, null)
        }
        val input = try {
            inputFactory.open(bytes, limits.maxMetadataBytes)
        } catch (e: TiffMultiPageOcrValidationException) {
            throw e
        } catch (e: Exception) {
            if (findCause<MetadataLimitExceededException>(e) != null) {
                throw validation(TiffMultiPageOcrFailureReason.METADATA_LIMIT_EXCEEDED, null)
            }
            throw TiffMultiPageOcrValidationException(
                TiffMultiPageOcrFailureReason.READER_UNAVAILABLE,
                null,
                "TIFF OCR reader is unavailable.",
            )
        }

        return try {
            val reader = readerFactory.open(input.stream)
            input.stream.seek(0)
            reader.setInput(input.stream, false, false)
            TiffImageSession(input, reader)
        } catch (e: TiffMultiPageOcrValidationException) {
            closeInputAfterOpenFailure(input, e)
            throw e
        } catch (e: Exception) {
            val mapped = if (findCause<MetadataLimitExceededException>(e) != null) {
                validation(TiffMultiPageOcrFailureReason.METADATA_LIMIT_EXCEEDED, null)
            } else {
                TiffMultiPageOcrValidationException(
                    TiffMultiPageOcrFailureReason.READER_UNAVAILABLE,
                    null,
                    "TIFF OCR reader is unavailable.",
                )
            }
            closeInputAfterOpenFailure(input, mapped)
            throw mapped
        }
    }

    private fun closeInputAfterOpenFailure(input: TiffImageInput, primary: Throwable) {
        try {
            input.close()
        } catch (cleanup: Throwable) {
            logCleanup(cleanup)
            primary.addSuppressed(SanitizedCleanupMarker())
        }
    }

    private fun preflight(session: TiffImageSession, limits: TiffMultiPageOcrLimits): List<PageMetadata> {
        val pageCount = try {
            session.reader.getNumImages(false)
        } catch (e: Exception) {
            if (findCause<MetadataLimitExceededException>(e) != null) {
                throw validation(TiffMultiPageOcrFailureReason.METADATA_LIMIT_EXCEEDED, null)
            }
            throw TiffMultiPageOcrValidationException(
                TiffMultiPageOcrFailureReason.PAGE_COUNT_UNKNOWN,
                null,
                "TIFF OCR metadata could not be read.",
            )
        }
        if (pageCount <= 0) {
            throw validation(TiffMultiPageOcrFailureReason.PAGE_COUNT_UNKNOWN, null)
        }
        if (pageCount > limits.maxPages) {
            throw validation(TiffMultiPageOcrFailureReason.PAGE_LIMIT_EXCEEDED, null)
        }

        val pages = ArrayList<PageMetadata>(pageCount)
        var totalPixels = 0L
        for (index in 0 until pageCount) {
            val width: Int
            val height: Int
            try {
                width = session.reader.getWidth(index)
                height = session.reader.getHeight(index)
            } catch (e: Exception) {
                if (findCause<MetadataLimitExceededException>(e) != null) {
                    throw validation(TiffMultiPageOcrFailureReason.METADATA_LIMIT_EXCEEDED, index)
                }
                throw validation(TiffMultiPageOcrFailureReason.DIMENSIONS_UNAVAILABLE, index)
            }
            if (width <= 0 || height <= 0) {
                throw validation(TiffMultiPageOcrFailureReason.DIMENSIONS_UNAVAILABLE, index)
            }
            if (width > limits.maxDecodedSide || height > limits.maxDecodedSide) {
                throw validation(TiffMultiPageOcrFailureReason.SIDE_LIMIT_EXCEEDED, index)
            }
            val pixels = try {
                Math.multiplyExact(width.toLong(), height.toLong())
            } catch (_: ArithmeticException) {
                throw validation(TiffMultiPageOcrFailureReason.PIXELS_PER_PAGE_LIMIT_EXCEEDED, index)
            }
            if (pixels > limits.maxPixelsPerPage) {
                throw validation(TiffMultiPageOcrFailureReason.PIXELS_PER_PAGE_LIMIT_EXCEEDED, index)
            }
            if (pixels > limits.maxTotalPixels - totalPixels) {
                throw validation(TiffMultiPageOcrFailureReason.TOTAL_PIXELS_LIMIT_EXCEEDED, index)
            }
            totalPixels += pixels
            pages += PageMetadata(index, width, height)
        }
        return pages
    }

    private fun processPages(
        session: TiffImageSession,
        pages: List<PageMetadata>,
        options: OcrOptions,
        limits: TiffMultiPageOcrLimits,
    ): OcrStructuredResult {
        val aggregate = AggregateResult(options, limits)
        pages.forEach { page ->
            val result = processPage(session, page, options, limits)
            aggregate.append(page.index, result)
        }
        return aggregate.toResult()
    }

    private fun processPage(
        session: TiffImageSession,
        page: PageMetadata,
        options: OcrOptions,
        limits: TiffMultiPageOcrLimits,
    ): OcrStructuredResult {
        val buffered = try {
            session.reader.read(page.index)
                ?: throw IOException("decoded page was null")
        } catch (e: CancellationException) {
            throw e
        } catch (_: EOFException) {
            throw validation(TiffMultiPageOcrFailureReason.DECODE_FAILED, null)
        } catch (e: IOException) {
            throw validation(TiffMultiPageOcrFailureReason.DECODE_FAILED, page.index)
        } catch (e: Exception) {
            throw TiffMultiPageOcrException(
                TiffMultiPageOcrFailureReason.DECODE_FAILED,
                page.index,
                "TIFF OCR page decode failed.",
            )
        }

        val image = try {
            validateDecodedImage(buffered, page, limits)
            ImmutableImage.fromAwt(buffered)
        } catch (e: TiffMultiPageOcrValidationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw TiffMultiPageOcrException(
                TiffMultiPageOcrFailureReason.DECODE_FAILED,
                page.index,
                "TIFF OCR page decode failed.",
            )
        }

        return try {
            engine.recognizeStructured(image, options)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw TiffMultiPageOcrException(
                TiffMultiPageOcrFailureReason.ENGINE_FAILED,
                page.index,
                "TIFF OCR engine failed.",
            )
        }
    }

    private fun validateDecodedImage(image: BufferedImage, page: PageMetadata, limits: TiffMultiPageOcrLimits) {
        if (image.width != page.width || image.height != page.height) {
            throw TiffMultiPageOcrException(
                TiffMultiPageOcrFailureReason.DECODE_FAILED,
                page.index,
                "TIFF OCR page decode failed.",
            )
        }
        if (image.width > limits.maxDecodedSide || image.height > limits.maxDecodedSide) {
            throw validation(TiffMultiPageOcrFailureReason.SIDE_LIMIT_EXCEEDED, page.index)
        }
        val pixels = try {
            Math.multiplyExact(image.width.toLong(), image.height.toLong())
        } catch (_: ArithmeticException) {
            throw validation(TiffMultiPageOcrFailureReason.PIXELS_PER_PAGE_LIMIT_EXCEEDED, page.index)
        }
        if (pixels > limits.maxPixelsPerPage) {
            throw validation(TiffMultiPageOcrFailureReason.PIXELS_PER_PAGE_LIMIT_EXCEEDED, page.index)
        }
    }

    private fun closeSession(session: TiffImageSession?, primary: Throwable?) {
        if (session == null) return
        try {
            session.close()
        } catch (cleanup: Throwable) {
            logCleanup(cleanup)
            val marker = SanitizedCleanupMarker()
            if (primary != null) {
                primary.addSuppressed(marker)
            } else {
                throw marker
            }
        }
    }

    private fun validateEncodedSize(bytes: ByteArray, limits: TiffMultiPageOcrLimits) {
        if (bytes.size.toLong() > limits.maxEncodedBytes) {
            throw validation(TiffMultiPageOcrFailureReason.INPUT_TOO_LARGE, null)
        }
    }

    private fun validation(reason: TiffMultiPageOcrFailureReason, pageIndex: Int?): TiffMultiPageOcrValidationException =
        TiffMultiPageOcrValidationException(reason, pageIndex, "TIFF OCR input was rejected ($reason).")

    private inline fun <reified T : Throwable> findCause(error: Throwable): T? {
        var current: Throwable? = error
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    internal companion object : KLoggingChannel() {
        @JvmSynthetic
        fun withFactories(
            engine: StructuredOcrEngine,
            inputFactory: TiffImageInputFactory,
            readerFactory: TiffImageReaderFactory,
        ): TiffMultiPageOcr = TiffMultiPageOcr(engine, inputFactory, readerFactory)

        private const val TIFF_HEADER_BYTES: Long = 8L
        private fun logCleanup(error: Throwable) {
            log.debug { "TIFF OCR resource cleanup failed: ${error::class.java.name}" }
        }
    }
}

private data class PageMetadata(
    val index: Int,
    val width: Int,
    val height: Int,
)

private class AggregateResult(
    private val options: OcrOptions,
    private val limits: TiffMultiPageOcrLimits,
) {
    private val text = StringBuilder()
    private val pages = mutableListOf<OcrPage>()
    private val blocks = mutableListOf<OcrTextBlock>()
    private val lines = mutableListOf<OcrTextLine>()
    private val words = mutableListOf<OcrWord>()

    fun append(pageIndex: Int, result: OcrStructuredResult) {
        val separatorLength = if (text.isEmpty()) 0 else 2
        val remainingText = limits.maxResultTextChars.toLong() - text.length.toLong()
        val requiredText = separatorLength.toLong() + result.text.length.toLong()
        if (requiredText > remainingText) {
            throw TiffMultiPageOcrValidationException(
                TiffMultiPageOcrFailureReason.RESULT_LIMIT_EXCEEDED,
                pageIndex,
                "TIFF OCR result exceeded its configured limit.",
            )
        }
        val accumulatedEntries = pages.size.toLong() + blocks.size.toLong() +
            lines.size.toLong() + words.size.toLong()
        val pageEntries = result.pages.size.toLong() + result.blocks.size.toLong() +
            result.lines.size.toLong() + result.words.size.toLong()
        if (pageEntries > limits.maxResultEntries.toLong() - accumulatedEntries) {
            throw TiffMultiPageOcrValidationException(
                TiffMultiPageOcrFailureReason.RESULT_LIMIT_EXCEEDED,
                pageIndex,
                "TIFF OCR result exceeded its configured limit.",
            )
        }

        if (separatorLength > 0) text.append("\n\n")
        text.append(result.text)
        pages += result.pages.map { it.copy(pageIndex = pageIndex) }
        blocks += result.blocks.map { it.copy(pageIndex = pageIndex) }
        lines += result.lines.map { it.copy(pageIndex = pageIndex) }
        words += result.words.map { it.copy(pageIndex = pageIndex) }
    }

    fun toResult(): OcrStructuredResult = OcrStructuredResult(
        text = text.toString(),
        options = options,
        pages = pages.toList(),
        blocks = blocks.toList(),
        lines = lines.toList(),
        words = words.toList(),
    )
}

internal interface TiffImageInputFactory {
    fun open(bytes: ByteArray, maxMetadataBytes: Long): TiffImageInput
}

internal interface TiffImageInput : AutoCloseable {
    val stream: ImageInputStream

    fun allowPayloadReads()
}

internal interface TiffImageReaderFactory {
    fun open(stream: ImageInputStream): ImageReader
}

private class MetadataLimitExceededException : IOException("metadata budget exceeded")

private class MetadataBudgetInputStream(
    input: InputStream,
    private val maxMetadataBytes: Long,
) : FilterInputStream(input) {
    private var metadataPhase = true
    private var consumedMetadataBytes = 0L

    fun allowPayloadReads() {
        metadataPhase = false
    }

    override fun read(): Int {
        ensureMetadataCapacity()
        val value = super.read()
        if (metadataPhase && value >= 0) consumedMetadataBytes++
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val permitted = if (metadataPhase) {
            val remaining = maxMetadataBytes - consumedMetadataBytes
            if (remaining <= 0) throw MetadataLimitExceededException()
            minOf(length.toLong(), remaining).toInt()
        } else {
            length
        }
        val read = super.read(buffer, offset, permitted)
        if (metadataPhase && read > 0) consumedMetadataBytes += read.toLong()
        return read
    }

    override fun skip(length: Long): Long {
        if (!metadataPhase) return super.skip(length)
        val remaining = maxMetadataBytes - consumedMetadataBytes
        if (remaining <= 0) throw MetadataLimitExceededException()
        val skipped = super.skip(minOf(length, remaining))
        consumedMetadataBytes += skipped
        return skipped
    }

    private fun ensureMetadataCapacity() {
        if (metadataPhase && consumedMetadataBytes >= maxMetadataBytes) {
            throw MetadataLimitExceededException()
        }
    }
}

private class DefaultTiffImageInput(
    private val source: MetadataBudgetInputStream,
    override val stream: ImageInputStream,
) : TiffImageInput {
    override fun allowPayloadReads() {
        source.allowPayloadReads()
    }

    override fun close() {
        var failure: Throwable? = null
        try {
            stream.close()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            source.close()
        } catch (error: Throwable) {
            failure = failure ?: error
        }
        failure?.let { throw it }
    }
}

private object DefaultTiffImageInputFactory : TiffImageInputFactory {
    override fun open(bytes: ByteArray, maxMetadataBytes: Long): TiffImageInput {
        val source = MetadataBudgetInputStream(ByteArrayInputStream(bytes), maxMetadataBytes)
        val stream = try {
            ImageIO.createImageInputStream(source)
                ?: throw IOException("ImageInputStream unavailable")
        } catch (error: Throwable) {
            try {
                source.close()
            } catch (_: Throwable) {
                // The caller has no session yet; keep the primary reader failure sanitized.
            }
            throw error
        }
        return DefaultTiffImageInput(source, stream)
    }
}

private object DefaultTiffImageReaderFactory : TiffImageReaderFactory {
    override fun open(stream: ImageInputStream): ImageReader {
        IIORegistryUtils.registerApplicationClasspathSpis()
        stream.seek(0)
        val readers = ImageIO.getImageReaders(stream).asSequence().toList()
        if (readers.isEmpty()) {
            throw TiffMultiPageOcrValidationException(
                TiffMultiPageOcrFailureReason.READER_UNAVAILABLE,
                null,
                "TIFF OCR reader is unavailable.",
            )
        }

        val tiffReaders = readers.filter { reader ->
            reader.formatName.equals("tiff", ignoreCase = true) ||
                reader.formatName.equals("tif", ignoreCase = true)
        }
        val selected = tiffReaders.firstOrNull { reader ->
            reader.javaClass.name.startsWith("com.twelvemonkeys.imageio.plugins.tiff.")
        } ?: tiffReaders.firstOrNull()
        readers.filter { it !== selected }.forEach(ImageReader::dispose)
        stream.seek(0)
        return selected ?: throw TiffMultiPageOcrValidationException(
            TiffMultiPageOcrFailureReason.UNSUPPORTED_FORMAT,
            null,
            "TIFF OCR input format is unsupported.",
        )
    }
}

private class TiffImageSession(
    val input: TiffImageInput,
    val reader: ImageReader,
) : AutoCloseable {
    override fun close() {
        var failure: Throwable? = null
        try {
            reader.dispose()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            input.close()
        } catch (error: Throwable) {
            failure = failure ?: error
        }
        failure?.let { throw it }
    }
}

private class SanitizedCleanupMarker : RuntimeException("TIFF OCR resource cleanup failed")
