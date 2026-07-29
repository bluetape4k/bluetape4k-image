package io.bluetape4k.images.ocr

import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requireNotEmpty
import io.bluetape4k.support.requirePositiveNumber
import java.awt.Rectangle
import java.io.Serializable
import net.sourceforge.tess4j.ITessAPI

/**
 * Tesseract 기반 OCR recognition option입니다.
 *
 * ## 동작/계약
 * - [languages]는 `+`로 join되어 Tess4J에 Tesseract language expression으로 전달됩니다.
 * - [tessdataPath]는 선택값입니다. 생략하면 Tesseract가 `TESSDATA_PREFIX` 또는 자체
 *   default lookup path에서 trained data를 찾습니다.
 * - [variables]와 [configs]는 OCR 시작 전에 call별 Tess4J instance에 적용됩니다.
 * - [structuredDetail]은 요청할 structured OCR data의 양을 제어합니다.
 * - [regions]는 underlying engine이 region-limited extraction을 지원할 때 caller가
 *   제공한 source region으로 recognition 범위를 제한합니다.
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
     * Tess4J에 전달되는 language expression입니다.
     */
    val languageExpression: String
        get() = languages.joinToString(separator = "+")

    companion object {
        private const val serialVersionUID: Long = -2101859296994037212L

        const val DEFAULT_LANGUAGE: String = "eng"
    }
}

/**
 * [OcrEngine]에 요청하는 structured OCR extraction detail입니다.
 *
 * ## 동작/계약
 * - [PLAIN_TEXT]는 현재 text-only baseline을 보존합니다.
 * - [LINE]은 사용 가능할 때 block 및 line entry를 요청합니다.
 * - [WORD]는 사용 가능할 때 block, line, word entry를 요청합니다.
 */
enum class OcrStructuredDetail {
    PLAIN_TEXT,
    LINE,
    WORD,
}

/**
 * pixel-space OCR bounding box입니다.
 *
 * ## 동작/계약
 * coordinate는 source image coordinate system의 0 기준 pixel 값입니다. width와 height는
 * 양수여야 합니다. engine box data가 없으면 zero-sized box를 만들어내지 않고 OCR entry에서
 * `null`로 모델링합니다.
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

    /** Tess4J region API에 넘길 AWT rectangle로 이 box를 변환합니다. */
    fun toAwtRectangle(): Rectangle =
        Rectangle(x, y, width, height)

    /** 이 box가 [region]과 교차하면 `true`를 반환합니다. */
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
 * region-limited OCR을 위해 caller가 제공하는 source region입니다.
 *
 * ## 동작/계약
 * [id]는 선택값이며, 인식된 box가 이 region과 교차하면 structured entry로 복사됩니다.
 * 이는 metadata일 뿐이고 storage 및 workflow side effect는 계속 caller 소유입니다.
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
 * [OcrEngine]이 반환하는 OCR result입니다.
 *
 * ## 동작/계약
 * [text]는 [options]가 정의한 engine-level post processing 이후의 정확한 인식 text를 담습니다.
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
 * [StructuredOcrEngine]이 반환하는 structured OCR result입니다.
 *
 * ## 동작/계약
 * - [text]는 [OcrResult]와 같은 plain-text extraction surface입니다.
 * - [pages]는 항상 하나 이상의 source page entry를 포함합니다.
 * - [blocks], [lines], [words]는 [OcrOptions.structuredDetail]와 engine support에 따라 채워집니다.
 * - confidence 또는 bounding-box data가 없으면 `null`로 유지됩니다.
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
 * OCR source page metadata입니다.
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
 * OCR text block entry입니다.
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
 * OCR text line entry입니다.
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
 * OCR word entry입니다.
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
 * Tess4J OCR engine mode constant를 감싸는 안정적인 wrapper입니다.
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
 * Tess4J page segmentation mode constant를 감싸는 안정적인 wrapper입니다.
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
