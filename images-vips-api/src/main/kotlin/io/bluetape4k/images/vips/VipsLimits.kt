package io.bluetape4k.images.vips

/**
 * libvips 이미지 처리 제한값 상수.
 *
 * 두 구현 모듈(images-vips-java21, images-vips-java25)이 동일한 기본값을 사용하도록
 * 이 파일에서 공통 정의합니다.
 */
object VipsLimits {

    /** 공통 런타임 초기화의 기본 libvips 스레드 수 */
    const val DEFAULT_CONCURRENCY: Int = 4

    /** 입력 이미지 최대 크기: 50 MB */
    const val MAX_INPUT_BYTES: Long = 50L * 1024 * 1024

    /** 기본 최대 픽셀 수 (width × height × bands): 1억 5천만 */
    const val DEFAULT_MAX_PIXELS: Long = 150_000_000L
}
