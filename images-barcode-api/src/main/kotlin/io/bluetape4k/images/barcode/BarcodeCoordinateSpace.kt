package io.bluetape4k.images.barcode

/**
 * barcode localization data에 사용하는 coordinate system입니다.
 */
enum class BarcodeCoordinateSpace {
    /** 원본 이미지 coordinate space의 pixel coordinate입니다. */
    PIXEL,

    /** 두 축이 모두 `0.0..1.0` 범위인 normalized coordinate입니다. */
    NORMALIZED,
}
