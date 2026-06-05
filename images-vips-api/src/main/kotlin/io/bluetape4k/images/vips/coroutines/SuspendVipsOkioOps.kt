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
 * Writes this [VipsImage] to a caller-owned [BufferedSink] on [Dispatchers.IO].
 *
 * The sink is flushed but not closed.
 */
suspend fun VipsImage.suspendWriteTo(
    sink: BufferedSink,
    format: VipsImageFormat,
    options: VipsEncodeOptions = VipsEncodeOptions.Default,
): Unit = withContext(Dispatchers.IO) {
    writeTo(sink, format, options)
}

/**
 * Writes this [VipsImage] to an Okio [Sink] on [Dispatchers.IO].
 *
 * This overload buffers and closes [sink].
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
 * Writes this [VipsImage] to a caller-owned [BufferedSuspendedSink].
 *
 * The vips encoder remains blocking, so this bridge uses
 * `bluetape4k-okio`'s [BufferedSuspendedSink.asBlocking] adapter and flushes
 * the bridged sink without closing the caller-owned suspended sink.
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
 * Writes this [VipsImage] to a [SuspendedSink].
 *
 * This overload buffers and closes [sink]. Use the [BufferedSuspendedSink]
 * overload when the caller must keep sink ownership.
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
