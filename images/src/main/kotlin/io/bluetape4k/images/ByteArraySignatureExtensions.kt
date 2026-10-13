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
