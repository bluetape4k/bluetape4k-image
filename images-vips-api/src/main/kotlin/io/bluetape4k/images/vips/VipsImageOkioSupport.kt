package io.bluetape4k.images.vips

import io.bluetape4k.okio.buffered
import okio.BufferedSink
import okio.Sink

/**
 * 이 [VipsImage]를 caller-owned [BufferedSink]에 씁니다.
 *
 * ## 계약
 * - sink는 현재 vips backend용 [java.io.OutputStream]으로 adapt됩니다.
 * - sink는 flush하지만 close하지 않습니다.
 * - 현재 vips 구현체는 non-path sink에 쓰기 전에 backend byte output을 통해 encode합니다. 큰 local file 성능을
 *   측정해야 하는 경로에서는 [java.nio.file.Path]를 받는 [VipsImage.writeTo]를 선호합니다.
 */
fun VipsImage.writeTo(
    sink: BufferedSink,
    format: VipsImageFormat,
    options: VipsEncodeOptions = VipsEncodeOptions.Default,
) {
    writeTo(sink.outputStream(), format, options)
    sink.flush()
}

/**
 * 이 [VipsImage]를 Okio [Sink]에 씁니다.
 *
 * 이 overload는 [sink]를 buffer하고 close합니다. caller가 sink ownership을 유지해야 하면 [BufferedSink] overload를
 * 사용해야 합니다.
 */
fun VipsImage.writeTo(
    sink: Sink,
    format: VipsImageFormat,
    options: VipsEncodeOptions = VipsEncodeOptions.Default,
) {
    sink.buffered().use { bufferedSink ->
        writeTo(bufferedSink, format, options)
    }
}
