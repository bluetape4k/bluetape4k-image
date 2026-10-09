package io.bluetape4k.images.barcode

/**
 * bluetape4k barcode provider가 이해하는 안정적인 barcode symbology입니다.
 *
 * ## 동작/계약
 * provider는 backend-specific format name을 이 enum으로 매핑하고, 필요하면 원본 backend
 * format을 [BarcodeResult.rawBackendFormat]에 보존해야 합니다. 알 수 없거나 provider
 * 전용 format은 [UNKNOWN]을 사용합니다.
 */
enum class BarcodeFormat {
    /** QR Code 2차원 barcode입니다. */
    QR_CODE,

    /** Code 128 1차원 barcode입니다. */
    CODE_128,

    /** Code 39 1차원 barcode입니다. */
    CODE_39,

    /** EAN-13 retail barcode입니다. */
    EAN_13,

    /** EAN-8 retail barcode입니다. */
    EAN_8,

    /** UPC-A retail barcode입니다. */
    UPC_A,

    /** UPC-E retail barcode입니다. */
    UPC_E,

    /** Data Matrix 2차원 barcode입니다. */
    DATA_MATRIX,

    /** Aztec 2차원 barcode입니다. */
    AZTEC,

    /** PDF417 stacked barcode입니다. */
    PDF_417,

    /** Codabar 1차원 barcode입니다. */
    CODABAR,

    /** Interleaved 2 of 5 1차원 barcode입니다. */
    ITF,

    /** backend-specific 또는 알 수 없는 format입니다. */
    UNKNOWN,
}
