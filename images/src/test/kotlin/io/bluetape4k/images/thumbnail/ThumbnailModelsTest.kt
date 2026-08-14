package io.bluetape4k.images.thumbnail

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.images.batch.ImageBatchFailureStage
import io.bluetape4k.images.coroutines.SuspendJpegWriter
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ThumbnailModelsTest : AbstractImageTest() {

    // ── ThumbnailSize 검증 ─────────────────────────────────────────────────

    @Test
    fun `ThumbnailSize stores width and height`() {
        val size = ThumbnailSize(800, 600)
        size.width shouldBeEqualTo 800
        size.height shouldBeEqualTo 600
    }

    @Test
    fun `ThumbnailSize generates default suffix from dimensions`() {
        val size = ThumbnailSize(1024, 768)
        size.suffix shouldBeEqualTo "1024x768"
    }

    @Test
    fun `ThumbnailSize allows custom suffix`() {
        val size = ThumbnailSize(200, 200, "thumbnail")
        size.suffix shouldBeEqualTo "thumbnail"
    }

    @Test
    fun `ThumbnailSize rejects zero width`() {
        assertFailsWith<IllegalArgumentException> {
            ThumbnailSize(0, 100)
        }
    }

    @Test
    fun `ThumbnailSize rejects negative height`() {
        assertFailsWith<IllegalArgumentException> {
            ThumbnailSize(100, -1)
        }
    }

    @Test
    fun `ThumbnailSize rejects blank suffix`() {
        assertFailsWith<IllegalArgumentException> {
            ThumbnailSize(100, 100, "   ")
        }
    }

    // ── ThumbnailCrop 검증 ─────────────────────────────────────────────────

    @Test
    fun `ThumbnailCrop Fit is a singleton`() {
        (ThumbnailCrop.Fit === ThumbnailCrop.Fit).shouldBeTrue()
    }

    @Test
    fun `ThumbnailCrop Smart stores strategy`() {
        val smart = ThumbnailCrop.Smart()
        smart.shouldNotBeNull()
    }

    // ── ThumbnailFormat 검증 ───────────────────────────────────────────────

    @Test
    fun `ThumbnailFormat Jpeg has jpg extension`() {
        ThumbnailFormat.Jpeg.normalizedExtension shouldBeEqualTo "jpg"
    }

    @Test
    fun `ThumbnailFormat normalizes extension by removing leading dot`() {
        val fmt = ThumbnailFormat(SuspendJpegWriter.Default, ".jpg")
        fmt.normalizedExtension shouldBeEqualTo "jpg"
    }

    @Test
    fun `ThumbnailFormat normalizes extension to lowercase`() {
        val fmt = ThumbnailFormat(SuspendJpegWriter.Default, "JPG")
        fmt.normalizedExtension shouldBeEqualTo "jpg"
    }

    @Test
    fun `ThumbnailFormat rejects extension with path separator`() {
        assertFailsWith<IllegalArgumentException> {
            ThumbnailFormat(SuspendJpegWriter.Default, "foo/bar")
        }
    }

    @Test
    fun `ThumbnailFormat rejects extension with Windows path separator`() {
        assertFailsWith<IllegalArgumentException> {
            ThumbnailFormat(SuspendJpegWriter.Default, "foo\\bar")
        }
    }

    // ── ThumbnailOutputName 검증 ───────────────────────────────────────────

    @Test
    fun `ThumbnailOutputName Default appends size suffix and extension`() {
        val source = Path.of("/photos/holiday.jpg")
        val size = ThumbnailSize(800, 600)
        val format = ThumbnailFormat.Jpeg

        val name = ThumbnailOutputName.Default.create(source, size, format)

        name shouldBeEqualTo "holiday-800x600.jpg"
    }

    @Test
    fun `ThumbnailOutputName Default works with custom suffix`() {
        val source = Path.of("/photos/img.png")
        val size = ThumbnailSize(200, 200, "thumb")
        val format = ThumbnailFormat.Jpeg

        val name = ThumbnailOutputName.Default.create(source, size, format)

        name shouldBeEqualTo "img-thumb.jpg"
    }

    // ── ThumbnailResult 검증 ───────────────────────────────────────────────

    @Test
    fun `ThumbnailResult stage is null for Success status`() {
        val result = ThumbnailResult(
            source = Path.of("/in.jpg"),
            output = Path.of("/out.jpg"),
            size = ThumbnailSize(100, 100),
            status = ThumbnailStatus.Success(bytes = 1024L),
        )
        result.stage shouldBeEqualTo null
        result.cause shouldBeEqualTo null
    }

    @Test
    fun `ThumbnailResult stage is non-null for Failure status`() {
        val cause = RuntimeException("oops")
        val result = ThumbnailResult(
            source = Path.of("/in.jpg"),
            output = Path.of("/out.jpg"),
            size = ThumbnailSize(100, 100),
            status = ThumbnailStatus.Failure(
                stage = ImageBatchFailureStage.LOAD,
                cause = cause,
            ),
        )
        result.stage shouldBeEqualTo ImageBatchFailureStage.LOAD
        result.cause shouldBeEqualTo cause
    }
}
