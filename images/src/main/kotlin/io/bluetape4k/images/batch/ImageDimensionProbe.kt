package io.bluetape4k.images.batch

import java.nio.file.Path

/**
 * 이미지를 완전히 디코딩하지 않고 첫 프레임의 픽셀 수를 읽습니다.
 */
fun probeImagePixelCount(path: Path): Long? =
    io.bluetape4k.images.probeImagePixelCount(path)
