package io.bluetape4k.images.vips.java21.writer

import com.criteo.vips.VipsImage
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsEncodeOptions
import com.criteo.vips.VipsException

/**
 * JVips WebP 인코더.
 *
 * 출력 바이트의 첫 4바이트는 반드시 `52 49 46 46` (RIFF/WebP 매직 바이트)입니다.
 */
internal object JVipsWebpWriter {

    /**
     * [VipsImage]를 WebP 바이트 배열로 인코딩합니다.
     *
     * @param image 원본 JVips 이미지
     * @param options 인코딩 옵션 (`quality`, `lossless`, `stripMetadata` 사용)
     * @throws VipsEncodeException 인코딩 실패 시
     */
    fun writeToBytes(image: VipsImage, options: VipsEncodeOptions): ByteArray {
        return try {
            // WebP 전용 API로 무손실 옵션까지 전달합니다.
            image.writeWEBPToArray(options.quality, options.lossless, options.stripMetadata)
        } catch (e: VipsException) {
            throw VipsEncodeException("WebP encoding failed", e)
        }
    }
}
