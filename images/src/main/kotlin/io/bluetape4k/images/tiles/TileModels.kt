package io.bluetape4k.images.tiles

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * 타일 크기입니다.
 */
data class TileSize(
    val width: Int,
    val height: Int,
): Serializable {
    init {
        width.requirePositiveNumber("width")
        height.requirePositiveNumber("height")
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * 분할된 이미지 타일입니다.
 */
data class ImageTile(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val image: ImmutableImage,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
