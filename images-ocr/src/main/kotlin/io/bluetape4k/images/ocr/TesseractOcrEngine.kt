package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.Serializable
import kotlinx.coroutines.CancellationException
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.ITesseract
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import net.sourceforge.tess4j.Word

/**
 * Tess4J 기반 OCR engine입니다.
 *
 * ## 동작/계약
 * - 각 recognition call마다 새 Tess4J [ITesseract] instance를 생성합니다.
 * - mutable native OCR state는 call 사이에 공유하지 않습니다.
 * - factory/configuration 오류는 [OcrConfigurationException]으로 정규화하며,
 *   [CancellationException]은 caller에게 그대로 전파합니다.
 * - Tesseract와 traineddata package는 runtime environment가 설치해야 하며, 이 module은
 *   language data를 bundle하지 않습니다.
 *
 * ```kotlin
 * val text = image.extractText(engine = TesseractOcrEngine())
 * ```
 */
class TesseractOcrEngine private constructor(
    private val tesseractFactory: () -> TesseractClient,
): StructuredOcrEngine {

    constructor(): this({ Tess4jTesseractClient(Tesseract()) })

    internal companion object {
        @JvmSynthetic
        fun withClientFactory(tesseractFactory: () -> TesseractClient): TesseractOcrEngine =
            TesseractOcrEngine(tesseractFactory)
    }

    override fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult {
        val tesseract = createConfiguredTesseract(options)

        val text = recognizeText(tesseract, image.awt(), options)
        return OcrResult(text = text, options = options)
    }

    override fun recognizeStructured(image: ImmutableImage, options: OcrOptions): OcrStructuredResult {
        val tesseract = createConfiguredTesseract(options)

        val bufferedImage = image.awt()
        val text = recognizeText(tesseract, bufferedImage, options)
        val pages = listOf(
            OcrPage(
                pageIndex = DEFAULT_PAGE_INDEX,
                text = text,
            ),
        )
        val blocks = if (options.structuredDetail.includesBlocks) {
            recognizeWords(tesseract, bufferedImage, options, ITessAPI.TessPageIteratorLevel.RIL_BLOCK)
                .map { it.toTextBlock(options) }
        } else {
            emptyList()
        }
        val lines = if (options.structuredDetail.includesLines) {
            recognizeWords(tesseract, bufferedImage, options, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)
                .map { it.toTextLine(options) }
        } else {
            emptyList()
        }
        val words = if (options.structuredDetail.includesWords) {
            recognizeWords(tesseract, bufferedImage, options, ITessAPI.TessPageIteratorLevel.RIL_WORD)
                .map { it.toWord(options) }
        } else {
            emptyList()
        }
        return OcrStructuredResult(
            text = text,
            options = options,
            pages = pages,
            blocks = blocks,
            lines = lines,
            words = words,
        )
    }

    private fun configure(tesseract: TesseractClient, options: OcrOptions) {
        options.tessdataPath?.let(tesseract::setDatapath)
        tesseract.setLanguage(options.languageExpression)
        tesseract.setOcrEngineMode(options.engineMode.value)
        tesseract.setPageSegMode(options.pageSegmentationMode.value)
        if (options.configs.isNotEmpty()) {
            tesseract.setConfigs(options.configs)
        }
        options.variables.forEach { (key, value) ->
            tesseract.setVariable(key, value)
        }
    }

    private fun createConfiguredTesseract(options: OcrOptions): TesseractClient {
        val tesseract = try {
            tesseractFactory()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkageError) {
            throw configurationException(options, e)
        } catch (e: TesseractException) {
            throw configurationException(options, e)
        } catch (e: RuntimeException) {
            throw configurationException(options, e)
        }

        try {
            configure(tesseract, options)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkageError) {
            throw configurationException(options, e)
        } catch (e: TesseractException) {
            throw configurationException(options, e)
        } catch (e: RuntimeException) {
            throw configurationException(options, e)
        }
        return tesseract
    }

    private fun recognizeText(
        tesseract: TesseractClient,
        image: BufferedImage,
        options: OcrOptions,
    ): String {
        val rawText = try {
            if (options.regions.isEmpty()) {
                tesseract.doOCR(image)
            } else {
                tesseract.doOCR(image, options.regions.map { it.boundingBox.toAwtRectangle() })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkageError) {
            throw configurationException(options, e)
        } catch (e: TesseractException) {
            throw OcrException(failureMessage(options), e)
        } catch (e: RuntimeException) {
            throw OcrException(failureMessage(options), e)
        }
        return if (options.trimText) rawText.trim() else rawText
    }

    private fun recognizeWords(
        tesseract: TesseractClient,
        image: BufferedImage,
        options: OcrOptions,
        level: Int,
    ): List<TesseractWord> =
        try {
            tesseract.getWords(image, level)
                .filter { it.belongsToRequestedRegions(options) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkageError) {
            throw configurationException(options, e)
        } catch (e: TesseractException) {
            throw OcrException(failureMessage(options), e)
        } catch (e: RuntimeException) {
            throw OcrException(failureMessage(options), e)
        }

    private fun configurationException(options: OcrOptions, cause: Throwable): OcrConfigurationException =
        OcrConfigurationException(configurationMessage(options), cause)

    private fun configurationMessage(options: OcrOptions): String =
        buildString {
            append("Tesseract OCR native runtime is not available for languages=")
            append(options.languageExpression)
            append(". Install Tesseract and required traineddata packages, or verify")
            append(" TESSDATA_PREFIX or the configured tessdata path.")
        }

    private fun failureMessage(options: OcrOptions): String =
        buildString {
            append("Tesseract OCR failed for languages=")
            append(options.languageExpression)
            append(".")
        }
}

internal interface TesseractClient {
    fun setDatapath(datapath: String)
    fun setLanguage(language: String)
    fun setOcrEngineMode(ocrEngineMode: Int)
    fun setPageSegMode(mode: Int)
    fun setConfigs(configs: List<String>)
    fun setVariable(key: String, value: String)
    fun doOCR(image: BufferedImage): String
    fun doOCR(image: BufferedImage, regions: List<Rectangle>): String
    fun getWords(image: BufferedImage, level: Int): List<TesseractWord>
}

private class Tess4jTesseractClient(
    private val delegate: ITesseract,
): TesseractClient {

    override fun setDatapath(datapath: String) {
        delegate.setDatapath(datapath)
    }

    override fun setLanguage(language: String) {
        delegate.setLanguage(language)
    }

    override fun setOcrEngineMode(ocrEngineMode: Int) {
        delegate.setOcrEngineMode(ocrEngineMode)
    }

    override fun setPageSegMode(mode: Int) {
        delegate.setPageSegMode(mode)
    }

    override fun setConfigs(configs: List<String>) {
        delegate.setConfigs(configs)
    }

    override fun setVariable(key: String, value: String) {
        delegate.setVariable(key, value)
    }

    override fun doOCR(image: BufferedImage): String =
        delegate.doOCR(image)

    override fun doOCR(image: BufferedImage, regions: List<Rectangle>): String =
        delegate.doOCR(image, null, regions)

    override fun getWords(image: BufferedImage, level: Int): List<TesseractWord> =
        delegate.getWords(image, level).map(TesseractWord::from)
}

internal data class TesseractWord(
    val text: String,
    val confidence: Double?,
    val boundingBox: OcrBoundingBox?,
): Serializable {

    fun toTextBlock(options: OcrOptions): OcrTextBlock =
        OcrTextBlock(
            pageIndex = DEFAULT_PAGE_INDEX,
            text = text,
            boundingBox = boundingBox,
            confidence = confidence,
            sourceRegion = sourceRegion(options),
        )

    fun toTextLine(options: OcrOptions): OcrTextLine =
        OcrTextLine(
            pageIndex = DEFAULT_PAGE_INDEX,
            text = text,
            boundingBox = boundingBox,
            confidence = confidence,
            sourceRegion = sourceRegion(options),
        )

    fun toWord(options: OcrOptions): OcrWord =
        OcrWord(
            pageIndex = DEFAULT_PAGE_INDEX,
            text = text,
            boundingBox = boundingBox,
            confidence = confidence,
            sourceRegion = sourceRegion(options),
        )

    fun belongsToRequestedRegions(options: OcrOptions): Boolean =
        options.regions.isEmpty() || sourceRegion(options) != null

    private fun sourceRegion(options: OcrOptions): OcrRegion? =
        boundingBox?.let { box ->
            options.regions.firstOrNull(box::intersects)
        }

    companion object {
        private const val serialVersionUID: Long = 2814839135159140268L

        fun from(word: Word): TesseractWord =
            TesseractWord(
                text = word.text.orEmpty(),
                confidence = word.confidence.toConfidenceOrNull(),
                boundingBox = OcrBoundingBox.from(word.boundingBox),
            )
    }
}

private val OcrStructuredDetail.includesBlocks: Boolean
    get() = this == OcrStructuredDetail.LINE || this == OcrStructuredDetail.WORD

private val OcrStructuredDetail.includesLines: Boolean
    get() = this == OcrStructuredDetail.LINE || this == OcrStructuredDetail.WORD

private val OcrStructuredDetail.includesWords: Boolean
    get() = this == OcrStructuredDetail.WORD

private fun Float.toConfidenceOrNull(): Double? =
    toDouble().takeIf { it.isFinite() && it in 0.0..100.0 }

private const val DEFAULT_PAGE_INDEX = 0
