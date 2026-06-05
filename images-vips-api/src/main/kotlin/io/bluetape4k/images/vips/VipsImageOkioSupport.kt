package io.bluetape4k.images.vips

import io.bluetape4k.okio.buffered
import okio.BufferedSink
import okio.Sink

/**
 * Writes this [VipsImage] to a caller-owned [BufferedSink].
 *
 * ## Contract
 * - The sink is adapted to an [java.io.OutputStream] for the current vips
 *   backend.
 * - The sink is flushed, but not closed.
 * - Current vips implementations still encode through backend byte output
 *   before writing to non-path sinks; prefer [VipsImage.writeTo] with a
 *   [java.nio.file.Path] for measured large local-file performance.
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
 * Writes this [VipsImage] to an Okio [Sink].
 *
 * This overload buffers and closes [sink]. Use the [BufferedSink] overload when
 * the caller must keep sink ownership.
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
