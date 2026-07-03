package io.bluetape4k.images.svg

import io.bluetape4k.support.requirePositiveNumber
import java.awt.Color
import java.io.Serializable

/**
 * Options for SVG rasterization.
 *
 * ## Contract
 * - If both `width` and `height` are `null`, the rasterizer keeps the SVG's intrinsic size.
 * - `allowExternalResources` defaults to `false` and should stay disabled for untrusted SVG input.
 * - `timeoutMillis` bounds Batik rasterization work.
 * - `maxWidthPx` and `maxHeightPx` cap the requested or intrinsic output dimensions.
 * - Numeric dimensions, DPI, timeout, and maximum bounds must be positive.
 *
 * ```kotlin
 * val opts = SvgRasterizeOptions(width = 800, height = 600, dpi = 144)
 * val rasterizer: SuspendSvgRasterizer = BatikSvgRasterizer()
 * val image = rasterizer.rasterize(svgInputStream, opts)
 * ```
 *
 * @property width requested output width in pixels (`null` keeps the intrinsic width)
 * @property height requested output height in pixels (`null` keeps the intrinsic height)
 * @property dpi rasterization DPI, defaulting to 96
 * @property backgroundColor background color (`null` keeps transparency)
 * @property allowExternalResources whether Batik may load external resources
 * @property allowedSchemes allowed URL schemes for future external-resource filtering
 * @property timeoutMillis rasterization timeout in milliseconds
 * @property maxWidthPx maximum allowed output width in pixels
 * @property maxHeightPx maximum allowed output height in pixels
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
