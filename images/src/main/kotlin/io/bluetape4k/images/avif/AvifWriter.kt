package io.bluetape4k.images.avif

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.IncubatingImageApi
import java.io.OutputStream

/**
 * AVIF 인코딩 옵션입니다.
 *
 * ## 동작/계약
 * - `quality`는 0.0(최대 압축) ~ 1.0(최고 품질) 범위입니다.
 * - `lossless`가 `true`이면 `quality` 값은 무시됩니다.
 *
 * ```kotlin
 * val opts = AvifEncodeOptions(quality = 0.85f)
 * ```
 *
 * @property quality   인코딩 품질 (0.0~1.0, 기본값: 0.85f)
 * @property lossless  무손실 인코딩 여부 (기본값: `false`)
 */
data class AvifEncodeOptions(
    val quality: Float = 0.85f,
    val lossless: Boolean = false,
) {
    init {
        require(quality in 0.0f..1.0f) { "quality must be in 0.0..1.0: $quality" }
    }

    companion object {
        @JvmStatic
        val Default = AvifEncodeOptions()
    }
}

/**
 * AVIF 형식으로 이미지를 기록하는 코루틴 기반 writer 인터페이스입니다.
 *
 * ## 동작/계약
 * - 이 인터페이스는 incubating 상태입니다 ([IncubatingImageApi]).
 * - The core `images` module only defines the contract.
 * - Runtime AVIF support is provided by a libvips backend such as `images-vips-java21` or `images-vips-java25`.
 *
 * ```kotlin
 * @OptIn(IncubatingImageApi::class)
 * val image: VipsImage = vipsRuntime.load(input)
 * val bos = ByteArrayOutputStream()
 * image.writeTo(bos, VipsImageFormat.AVIF)
 * ```
 *
 * @see AvifEncodeOptions
 * @see IncubatingImageApi
 */
@IncubatingImageApi
interface AvifWriter {

    /**
     * [image]를 AVIF 형식으로 [out]에 씁니다.
     *
     * @param image   쓸 이미지
     * @param out     쓰기 대상 [OutputStream]
     * @param options AVIF 인코딩 옵션 (기본값: [AvifEncodeOptions.Default])
     */
    suspend fun suspendWrite(
        image: ImmutableImage,
        out: OutputStream,
        options: AvifEncodeOptions = AvifEncodeOptions.Default,
    )
}
