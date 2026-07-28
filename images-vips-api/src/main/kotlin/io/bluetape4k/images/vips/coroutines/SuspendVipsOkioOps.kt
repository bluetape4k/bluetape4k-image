package io.bluetape4k.images.vips.coroutines

import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.writeTo
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.BufferedSuspendedSink
import io.bluetape4k.okio.coroutines.SuspendedSink
import io.bluetape4k.okio.coroutines.asBlocking
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.Sink

/**
 * 이 [VipsImage]를 [Dispatchers.IO]에서 caller-owned [BufferedSink]에 씁니다.
 *
 * sink는 flush하지만 close하지 않습니다.
 */
suspend fun VipsImage.suspendWriteTo(
    sink: BufferedSink,
    format: VipsImageFormat,
    options: VipsEncodeOptions = VipsEncodeOptions.Default,
): Unit = withContext(Dispatchers.IO) {
    writeTo(sink, format, options)
}

/**
 * 이 [VipsImage]를 [Dispatchers.IO]에서 Okio [Sink]에 씁니다.
 *
 * 이 overload는 [sink]를 buffer하고 close합니다.
 */
suspend fun VipsImage.suspendWriteTo(
    sink: Sink,
    format: VipsImageFormat,
    options: VipsEncodeOptions = VipsEncodeOptions.Default,
): Unit = withContext(Dispatchers.IO) {
    sink.buffered().use { bufferedSink ->
        writeTo(bufferedSink, format, options)
    }
}

/**
 * 이 [VipsImage]를 caller-owned [BufferedSuspendedSink]에 씁니다.
 *
 * vips encoder는 여전히 blocking이므로 이 bridge는 `bluetape4k-okio`의 [BufferedSuspendedSink.asBlocking]
 * adapter를 사용합니다. bridged sink는 flush하지만 caller-owned suspended sink는 close하지 않습니다.
 */
suspend fun VipsImage.suspendWriteTo(
    sink: BufferedSuspendedSink,
    format: VipsImageFormat,
    options: VipsEncodeOptions = VipsEncodeOptions.Default,
): Unit = withContext(Dispatchers.IO) {
    val blockingSink = sink.asBlocking().buffered()
    writeTo(blockingSink, format, options)
}

/**
 * 이 [VipsImage]를 [SuspendedSink]에 씁니다.
 *
 * 이 overload는 [sink]를 buffer하고 close합니다. caller가 sink ownership을 유지해야 하면
 * [BufferedSuspendedSink] overload를 사용해야 합니다.
 */
suspend fun VipsImage.suspendWriteTo(
    sink: SuspendedSink,
    format: VipsImageFormat,
    options: VipsEncodeOptions = VipsEncodeOptions.Default,
) {
    val bufferedSink = sink.bufferedSuspended()
    try {
        suspendWriteTo(bufferedSink, format, options)
    } finally {
        bufferedSink.close()
    }
}
