package io.bluetape4k.images.ocr

import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requireNotEmpty
import java.io.Serializable
import net.sourceforge.tess4j.ITessAPI

/**
 * Options for Tesseract-backed OCR recognition.
 *
 * ## Contract
 * - [languages] are joined with `+` and passed to Tess4J as the Tesseract
 *   language expression.
 * - [tessdataPath] is optional. When omitted, Tesseract resolves trained data
 *   from `TESSDATA_PREFIX` or its own default lookup path.
 * - [variables] and [configs] are applied to the per-call Tess4J instance
 *   before OCR starts.
 *
 * ```kotlin
 * val options = OcrOptions(languages = listOf("eng", "kor"))
 * ```
 */
data class OcrOptions(
    val languages: List<String> = listOf(DEFAULT_LANGUAGE),
    val tessdataPath: String? = null,
    val engineMode: TesseractEngineMode = TesseractEngineMode.DEFAULT,
    val pageSegmentationMode: TesseractPageSegmentationMode = TesseractPageSegmentationMode.AUTO,
    val variables: Map<String, String> = emptyMap(),
    val configs: List<String> = emptyList(),
    val trimText: Boolean = true,
): Serializable {

    init {
        languages.requireNotEmpty("languages")
        languages.forEach { it.requireNotBlank("language") }
        tessdataPath?.requireNotBlank("tessdataPath")
        variables.keys.forEach { it.requireNotBlank("variable key") }
        configs.forEach { it.requireNotBlank("config") }
    }

    /**
     * Language expression passed to Tess4J.
     */
    val languageExpression: String
        get() = languages.joinToString(separator = "+")

    companion object {
        private const val serialVersionUID: Long = -2101859296994037212L

        const val DEFAULT_LANGUAGE: String = "eng"
    }
}

/**
 * OCR result returned by an [OcrEngine].
 *
 * ## Contract
 * [text] contains the exact recognized text after the engine-level post
 * processing defined by [options].
 */
data class OcrResult(
    val text: String,
    val options: OcrOptions,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 4084561701396397733L
    }
}

/**
 * Stable wrapper around Tess4J OCR engine mode constants.
 */
enum class TesseractEngineMode(
    val value: Int,
) {
    TESSERACT_ONLY(ITessAPI.TessOcrEngineMode.OEM_TESSERACT_ONLY),
    LSTM_ONLY(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY),
    TESSERACT_LSTM_COMBINED(ITessAPI.TessOcrEngineMode.OEM_TESSERACT_LSTM_COMBINED),
    DEFAULT(ITessAPI.TessOcrEngineMode.OEM_DEFAULT),
}

/**
 * Stable wrapper around Tess4J page segmentation mode constants.
 */
enum class TesseractPageSegmentationMode(
    val value: Int,
) {
    OSD_ONLY(ITessAPI.TessPageSegMode.PSM_OSD_ONLY),
    AUTO_OSD(ITessAPI.TessPageSegMode.PSM_AUTO_OSD),
    AUTO_ONLY(ITessAPI.TessPageSegMode.PSM_AUTO_ONLY),
    AUTO(ITessAPI.TessPageSegMode.PSM_AUTO),
    SINGLE_COLUMN(ITessAPI.TessPageSegMode.PSM_SINGLE_COLUMN),
    SINGLE_BLOCK_VERTICAL_TEXT(ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT),
    SINGLE_BLOCK(ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK),
    SINGLE_LINE(ITessAPI.TessPageSegMode.PSM_SINGLE_LINE),
    SINGLE_WORD(ITessAPI.TessPageSegMode.PSM_SINGLE_WORD),
    CIRCLE_WORD(ITessAPI.TessPageSegMode.PSM_CIRCLE_WORD),
    SINGLE_CHAR(ITessAPI.TessPageSegMode.PSM_SINGLE_CHAR),
    SPARSE_TEXT(ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT),
    SPARSE_TEXT_OSD(ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT_OSD),
    RAW_LINE(ITessAPI.TessPageSegMode.PSM_RAW_LINE),
}
