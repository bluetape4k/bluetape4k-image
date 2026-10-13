package io.bluetape4k.images

/**
 * 이 바이트 배열이 지정한 unsigned byte 값으로 시작하는지 확인합니다.
 *
 * 빈 prefix는 `true`이고, 배열이 짧거나 prefix 값이 `0..255` 범위를 벗어나면 `false`입니다.
 */
fun ByteArray.hasPrefix(prefix: IntArray): Boolean {
    if (size < prefix.size) return false

    for (index in prefix.indices) {
        val expected = prefix[index]
        if (expected !in 0..0xFF || (this[index].toInt() and 0xFF) != expected) {
            return false
        }
    }
    return true
}

private val PNG_SIGNATURE = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val JPEG_SIGNATURE = intArrayOf(0xFF, 0xD8, 0xFF)
private const val WEBP_HEADER_SIZE: Int = 12

/** PNG 파일 시그니처로 시작하는지 확인합니다. 이 검사는 전체 페이로드의 유효성이나 디코딩 성공을 보증하지 않습니다. */
fun ByteArray.isPng(): Boolean = hasPrefix(PNG_SIGNATURE)

/** JPEG 파일 시그니처로 시작하는지 확인합니다. 이 검사는 전체 페이로드의 유효성이나 디코딩 성공을 보증하지 않습니다. */
fun ByteArray.isJpeg(): Boolean = hasPrefix(JPEG_SIGNATURE)

/** 12바이트 이상의 WebP 헤더에서 `RIFF`와 offset 8의 `WEBP`를 확인합니다. 전체 페이로드 검증은 수행하지 않습니다. */
fun ByteArray.isWebp(): Boolean =
    size >= WEBP_HEADER_SIZE &&
        (this[0].toInt() and 0xFF) == 0x52 &&
        (this[1].toInt() and 0xFF) == 0x49 &&
        (this[2].toInt() and 0xFF) == 0x46 &&
        (this[3].toInt() and 0xFF) == 0x46 &&
        (this[8].toInt() and 0xFF) == 0x57 &&
        (this[9].toInt() and 0xFF) == 0x45 &&
        (this[10].toInt() and 0xFF) == 0x42 &&
        (this[11].toInt() and 0xFF) == 0x50
