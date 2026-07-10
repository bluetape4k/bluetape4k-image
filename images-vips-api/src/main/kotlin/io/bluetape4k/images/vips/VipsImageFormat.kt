package io.bluetape4k.images.vips

/**
 * Image output formats supported by libvips.
 *
 * Stable formats (`JPEG`, `PNG`, and `WEBP`) are immediately available.
 * AVIF and HEIC are [VipsIncubatingApi] formats and require libvips builds with libaom or libheif.
 */
enum class VipsImageFormat {

    /** JPEG — 손실 압축, 인터넷 범용 포맷 */
    JPEG,

    /** PNG — 무손실 압축, 투명도 지원 */
    PNG,

    /** WebP — Google 고효율 포맷, 손실/무손실 모두 지원 */
    WEBP,

    /** AVIF — AV1-based efficient format. Requires libaom in the libvips build. */
    @VipsIncubatingApi
    AVIF,

    /** HEIC — Apple efficient format. Requires libheif in the libvips build. */
    @VipsIncubatingApi
    HEIC,
}
