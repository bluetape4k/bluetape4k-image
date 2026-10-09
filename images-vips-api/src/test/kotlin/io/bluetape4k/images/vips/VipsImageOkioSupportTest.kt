package io.bluetape4k.images.vips

import io.bluetape4k.ToStringBuilder
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.vips.coroutines.suspendWriteTo
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.asSuspendedSink
import okio.Buffer
import okio.Sink
import okio.Timeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.OutputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended

class VipsImageOkioSupportTest {

    companion object: KLogging()

    @Test
    fun `writeTo BufferedSink flushes but does not close caller-owned sink`() {
        val trackingSink = TrackingSink()
        val bufferedSink = trackingSink.buffered()

        FakeVipsImage().writeTo(bufferedSink, VipsImageFormat.JPEG)

        log.debug { "trackingSink=$trackingSink" }
        trackingSink.flushed.shouldBeTrue()
        trackingSink.closed.shouldBeFalse()
        trackingSink.output.size shouldBeGreaterThan 0L
    }

    @Test
    fun `writeTo Sink buffers and closes owned sink`() {
        val trackingSink = TrackingSink()

        FakeVipsImage().writeTo(trackingSink, VipsImageFormat.JPEG)

        log.debug { "trackingSink=$trackingSink" }
        trackingSink.flushed.shouldBeTrue()
        trackingSink.closed.shouldBeTrue()
        trackingSink.output.size shouldBeGreaterThan 0L
    }

    @Test
    fun `suspendWriteTo BufferedSuspendedSink keeps caller-owned channel open`(@TempDir tempDir: Path) = runSuspendIO {
        val output = tempDir.resolve("caller-owned.jpg")
        val channel = AsynchronousFileChannel.open(output, CREATE, WRITE, TRUNCATE_EXISTING)
        val sink = channel.asSuspendedSink().bufferedSuspended()

        try {
            FakeVipsImage().suspendWriteTo(sink, VipsImageFormat.JPEG)
            channel.isOpen.shouldBeTrue()
        } finally {
            sink.close()
            channel.close()
        }
        channel.isOpen.shouldBeFalse()
        Files.size(output) shouldBeGreaterThan 0L
    }

    @Test
    fun `suspendWriteTo SuspendedSink buffers and closes owned channel`(@TempDir tempDir: Path) = runSuspendIO {
        val output = tempDir.resolve("owned.jpg")
        val channel = AsynchronousFileChannel.open(output, CREATE, WRITE, TRUNCATE_EXISTING)

        FakeVipsImage().suspendWriteTo(channel.asSuspendedSink(), VipsImageFormat.JPEG)

        channel.isOpen.shouldBeFalse()
        Files.size(output) shouldBeGreaterThan 0L
    }

    private class FakeVipsImage: VipsImage {
        override val width: Int = 1
        override val height: Int = 1
        override val bands: Int = 3

        override fun resize(width: Int, height: Int): VipsImage = this

        override fun thumbnail(maxDimension: Int): VipsImage = this

        override fun crop(left: Int, top: Int, width: Int, height: Int): VipsImage = this

        override fun toBytes(format: VipsImageFormat, options: VipsEncodeOptions): ByteArray =
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

        override fun writeTo(path: Path, format: VipsImageFormat, options: VipsEncodeOptions) {
            Files.write(path, toBytes(format, options))
        }

        override fun writeTo(out: OutputStream, format: VipsImageFormat, options: VipsEncodeOptions) {
            out.write(toBytes(format, options))
        }

        override fun close() = Unit

        override fun toString(): String {
            return ToStringBuilder(this)
                .add("width", width)
                .add("height", height)
                .add("bands", bands)
                .toString()
        }
    }

    private class TrackingSink: Sink {
        val output: Buffer = Buffer()
        var flushed: Boolean = false
        var closed: Boolean = false

        override fun write(source: Buffer, byteCount: Long) {
            output.write(source, byteCount)
        }

        override fun flush() {
            flushed = true
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed = true
        }

        override fun toString(): String {
            return ToStringBuilder(this)
                .add("output", output)
                .add("flushed", flushed)
                .add("closed", closed)
                .toString()
        }
    }
}
