package io.bluetape4k.images.barcode

/**
 * provider-neutral barcode failure reason입니다.
 */
enum class BarcodeFailureReason {
    /** 이미지에서 barcode를 찾지 못했습니다. */
    NO_BARCODE,

    /** 요청한 symbology를 provider가 지원하지 않습니다. */
    UNSUPPORTED_FORMAT,

    /** 이미지 입력을 디코딩할 수 없거나 입력이 malformed입니다. */
    MALFORMED_INPUT,

    /** provider가 decoding 중 실패했습니다. */
    DECODE_FAILED,

    /** provider를 사용할 수 없거나 설정이 잘못되었습니다. */
    PROVIDER_UNAVAILABLE,

    /** 작업이 취소되었습니다. */
    CANCELLED,

    /** failure reason이 알 수 없거나 provider-specific입니다. */
    UNKNOWN,
}
