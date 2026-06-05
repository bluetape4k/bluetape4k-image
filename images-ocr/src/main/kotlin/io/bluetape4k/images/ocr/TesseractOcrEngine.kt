package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import java.awt.image.BufferedImage
import net.sourceforge.tess4j.ITesseract
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException

/**
 * Tess4J-backed OCR engine.
 *
 * ## Contract
 * - A fresh Tess4J [ITesseract] instance is created for each recognition call.
 * - No mutable native OCR state is shared across calls.
 * - Tesseract and traineddata packages must be installed by the runtime
 *   environment; this module does not bundle language data.
 *
 * ```kotlin
 * val text = image.extractText(engine = TesseractOcrEngine())
 * ```
 */
class TesseractOcrEngine private constructor(
    private val tesseractFactory: () -> TesseractClient,
): OcrEngine {

    constructor(): this({ Tess4jTesseractClient(Tesseract()) })

    internal companion object {
        @JvmSynthetic
        fun withClientFactory(tesseractFactory: () -> TesseractClient): TesseractOcrEngine =
            TesseractOcrEngine(tesseractFactory)
    }

    override fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult {
        val tesseract = tesseractFactory()
        configure(tesseract, options)

        val rawText = try {
            tesseract.doOCR(image.awt())
        } catch (e: UnsatisfiedLinkError) {
            throw nativeConfigurationException(options, e)
        } catch (e: NoClassDefFoundError) {
            throw nativeConfigurationException(options, e)
        } catch (e: TesseractException) {
            throw OcrException(failureMessage(options), e)
        } catch (e: RuntimeException) {
            throw OcrException(failureMessage(options), e)
        }
        val text = if (options.trimText) rawText.trim() else rawText
        return OcrResult(text = text, options = options)
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

    private fun nativeConfigurationException(options: OcrOptions, cause: Throwable): OcrConfigurationException =
        OcrConfigurationException(configurationMessage(options), cause)

    private fun configurationMessage(options: OcrOptions): String =
        buildString {
            append("Tesseract OCR native runtime is not available for languages=")
            append(options.languageExpression)
            append(". Install Tesseract and required traineddata packages")
            options.tessdataPath?.let {
                append(", or verify tessdataPath=")
                append(it)
            } ?: append(", or verify TESSDATA_PREFIX")
            append(".")
        }

    private fun failureMessage(options: OcrOptions): String =
        buildString {
            append("Tesseract OCR failed for languages=")
            append(options.languageExpression)
            options.tessdataPath?.let {
                append(" with tessdataPath=")
                append(it)
            }
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
}
