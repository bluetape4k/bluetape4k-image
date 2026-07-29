package io.bluetape4k.images.svg

import io.bluetape4k.support.requirePositiveNumber
import java.awt.Color
import java.io.Serializable

/**
 * SVG 래스터화 옵션입니다.
 *
 * ## 동작/계약
 * - `width`와 `height`가 모두 `null`이면 rasterizer가 SVG의 고유 크기를 유지합니다.
 * - `allowExternalResources` 기본값은 `false`이며, 신뢰하지 않는 SVG 입력에서는 계속 비활성화해야 합니다.
 * - `timeoutMillis`는 Batik 래스터화 작업 시간을 제한합니다.
 * - `maxWidthPx`와 `maxHeightPx`는 요청된 출력 크기나 SVG 고유 출력 크기의 상한을 정합니다.
 * - 숫자 크기, DPI, timeout, 최대 크기 한계는 모두 양수여야 합니다.
 *
 * ```kotlin
 * val opts = SvgRasterizeOptions(width = 800, height = 600, dpi = 144)
 * val rasterizer: SuspendSvgRasterizer = BatikSvgRasterizer()
 * val image = rasterizer.rasterize(svgInputStream, opts)
 * ```
 *
 * @property width 요청 출력 너비(px). `null`이면 SVG 고유 너비를 유지합니다.
 * @property height 요청 출력 높이(px). `null`이면 SVG 고유 높이를 유지합니다.
 * @property dpi 래스터화 DPI입니다. 기본값은 96입니다.
 * @property backgroundColor 배경색입니다. `null`이면 투명 배경을 유지합니다.
 * @property allowExternalResources Batik이 외부 리소스를 읽을 수 있는지 여부입니다.
 * @property allowedSchemes 향후 외부 리소스 필터링에 사용할 허용 URL scheme 목록입니다.
 * @property timeoutMillis 래스터화 timeout입니다. 단위는 millisecond입니다.
 * @property maxWidthPx 허용되는 최대 출력 너비(px)입니다.
 * @property maxHeightPx 허용되는 최대 출력 높이(px)입니다.
 */
data class SvgRasterizeOptions(
    val width: Int? = null,
    val height: Int? = null,
    val dpi: Int = 96,
    val backgroundColor: Color? = null,
    val allowExternalResources: Boolean = false,
    val allowedSchemes: Set<String> = setOf("data"),
    val timeoutMillis: Long = 10_000L,
    val maxWidthPx: Int = 8192,
    val maxHeightPx: Int = 8192,
): Serializable {

    init {
        width?.requirePositiveNumber("width")
        height?.requirePositiveNumber("height")
        dpi.requirePositiveNumber("dpi")
        timeoutMillis.requirePositiveNumber("timeoutMillis")
        maxWidthPx.requirePositiveNumber("maxWidthPx")
        maxHeightPx.requirePositiveNumber("maxHeightPx")
    }

    companion object {
        private const val serialVersionUID: Long = -3374466010677860426L

        @JvmStatic
        val Default = SvgRasterizeOptions()
    }
}
