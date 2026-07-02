package io.bluetape4k.images.ocr

import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requireNotEmpty
import io.bluetape4k.support.requirePositiveNumber
import java.awt.Rectangle
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
 * - [structuredDetail] controls how much structured OCR data is requested.
 * - [regions] limits recognition to caller-supplied source regions when the
 *   underlying engine supports region-limited extraction.
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
    val structuredDetail: OcrStructuredDetail = OcrStructuredDetail.PLAIN_TEXT,
    val regions: List<OcrRegion> = emptyList(),
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
 * Structured OCR extraction detail requested from an [OcrEngine].
 *
 * ## Contract
 * - [PLAIN_TEXT] preserves the current text-only baseline.
 * - [LINE] requests block and line entries when available.
 * - [WORD] requests block, line, and word entries when available.
 */
enum class OcrStructuredDetail {
    PLAIN_TEXT,
    LINE,
    WORD,
}

/**
 * Pixel-space OCR bounding box.
 *
 * ## Contract
 * Coordinates are zero-based pixel values in the source image coordinate
 * system. Width and height must be positive. Missing engine box data is modeled
 * as `null` on OCR entries instead of fabricating a zero-sized box.
 */
@ConsistentCopyVisibility
data class OcrBoundingBox private constructor(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
): Serializable {

    init {
        require(x >= 0) { "x must be >= 0, but was $x" }
        require(y >= 0) { "y must be >= 0, but was $y" }
        width.requirePositiveNumber("width")
        height.requirePositiveNumber("height")
    }

    /** Converts this box to an AWT rectangle for Tess4J region APIs. */
    fun toAwtRectangle(): Rectangle =
        Rectangle(x, y, width, height)

    /** Returns true when this box intersects [region]. */
    fun intersects(region: OcrRegion): Boolean =
        toAwtRectangle().intersects(region.boundingBox.toAwtRectangle())

    companion object {
        private const val serialVersionUID: Long = -4678925178294803917L

        operator fun invoke(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ): OcrBoundingBox =
            OcrBoundingBox(x, y, width, height)

        fun from(rectangle: Rectangle?): OcrBoundingBox? =
            rectangle
                ?.takeIf { it.x >= 0 && it.y >= 0 && it.width > 0 && it.height > 0 }
                ?.let {
                    OcrBoundingBox(
                        x = it.x,
                        y = it.y,
                        width = it.width,
                        height = it.height,
                    )
                }
    }
}

/**
 * Caller-supplied source region for region-limited OCR.
 *
 * ## Contract
 * [id] is optional and is copied to structured entries when a recognized box
 * intersects this region. It is metadata only; storage and workflow side
 * effects remain caller-owned.
 */
@ConsistentCopyVisibility
data class OcrRegion private constructor(
    val boundingBox: OcrBoundingBox,
    val id: String?,
): Serializable {

    init {
        id?.requireNotBlank("id")
    }

    companion object {
        private const val serialVersionUID: Long = 3158388464829739136L

        operator fun invoke(
            boundingBox: OcrBoundingBox,
            id: String? = null,
        ): OcrRegion =
            OcrRegion(boundingBox, id)
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
 * Structured OCR result returned by a [StructuredOcrEngine].
 *
 * ## Contract
 * - [text] is the same plain-text extraction surface used by [OcrResult].
 * - [pages] always contains at least one source page entry.
 * - [blocks], [lines], and [words] are populated according to
 *   [OcrOptions.structuredDetail] and engine support.
 * - Missing confidence or bounding-box data stays `null`.
 */
data class OcrStructuredResult(
    val text: String,
    val options: OcrOptions,
    val pages: List<OcrPage>,
    val blocks: List<OcrTextBlock> = emptyList(),
    val lines: List<OcrTextLine> = emptyList(),
    val words: List<OcrWord> = emptyList(),
): Serializable {

    init {
        pages.requireNotEmpty("pages")
    }

    companion object {
        private const val serialVersionUID: Long = 6856357658866475452L
    }
}

/**
 * OCR source page metadata.
 */
data class OcrPage(
    val pageIndex: Int,
    val text: String,
    val boundingBox: OcrBoundingBox? = null,
    val confidence: Double? = null,
    val sourceRegion: OcrRegion? = null,
): Serializable {

    init {
        pageIndex.requireNonNegative("pageIndex")
        confidence?.requireConfidence("confidence")
    }

    companion object {
        private const val serialVersionUID: Long = -7671084136414993225L
    }
}

/**
 * OCR text block entry.
 */
data class OcrTextBlock(
    val pageIndex: Int,
    val text: String,
    val boundingBox: OcrBoundingBox? = null,
    val confidence: Double? = null,
    val sourceRegion: OcrRegion? = null,
): Serializable {

    init {
        pageIndex.requireNonNegative("pageIndex")
        confidence?.requireConfidence("confidence")
    }

    companion object {
        private const val serialVersionUID: Long = -7691293991597434082L
    }
}

/**
 * OCR text line entry.
 */
data class OcrTextLine(
    val pageIndex: Int,
    val text: String,
    val boundingBox: OcrBoundingBox? = null,
    val confidence: Double? = null,
    val sourceRegion: OcrRegion? = null,
): Serializable {

    init {
        pageIndex.requireNonNegative("pageIndex")
        confidence?.requireConfidence("confidence")
    }

    companion object {
        private const val serialVersionUID: Long = 7334580711811413156L
    }
}

/**
 * OCR word entry.
 */
data class OcrWord(
    val pageIndex: Int,
    val text: String,
    val boundingBox: OcrBoundingBox? = null,
    val confidence: Double? = null,
    val sourceRegion: OcrRegion? = null,
): Serializable {

    init {
        pageIndex.requireNonNegative("pageIndex")
        confidence?.requireConfidence("confidence")
    }

    companion object {
        private const val serialVersionUID: Long = 1134024054627217479L
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

internal fun Int.requireNonNegative(name: String) {
    require(this >= 0) { "$name must be >= 0, but was $this" }
}

internal fun Double.requireConfidence(name: String) {
    require(isFinite()) { "$name must be finite, but was $this" }
    require(this in CONFIDENCE_MIN..CONFIDENCE_MAX) { "$name must be in 0.0..100.0, but was $this" }
}

private const val CONFIDENCE_MIN = 0.0
private const val CONFIDENCE_MAX = 100.0
