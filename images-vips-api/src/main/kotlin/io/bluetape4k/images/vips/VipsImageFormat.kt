package io.bluetape4k.images.vips

/**
 * libvips가 지원하는 image output format입니다.
 *
 * stable format(`JPEG`, `PNG`, `WEBP`)은 즉시 사용할 수 있습니다.
 * AVIF와 HEIC는 [VipsIncubatingApi] format이며 libaom 또는 libheif를 포함한 libvips build가 필요합니다.
 */
enum class VipsImageFormat {

    /** JPEG — 손실 압축, 인터넷 범용 포맷 */
    JPEG,

    /** PNG — 무손실 압축, 투명도 지원 */
    PNG,

    /** WebP — Google 고효율 포맷, 손실/무손실 모두 지원 */
    WEBP,

    /** AVIF — AV1 기반 고효율 format입니다. libvips build에 libaom이 필요합니다. */
    @VipsIncubatingApi
    AVIF,

    /** HEIC — Apple 고효율 format입니다. libvips build에 libheif가 필요합니다. */
    @VipsIncubatingApi
    HEIC,
}
