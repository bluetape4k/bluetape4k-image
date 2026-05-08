package io.bluetape4k.images.vips

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class VipsEncodeOptionsTest {

    // ─── validation ──────────────────────────────────────────────────────────

    @Test
    fun `quality -1 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { VipsEncodeOptions(quality = -1) }
    }

    @Test
    fun `quality 101 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { VipsEncodeOptions(quality = 101) }
    }

    @Test
    fun `effort 0 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { VipsEncodeOptions(effort = 0) }
    }

    @Test
    fun `effort 10 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { VipsEncodeOptions(effort = 10) }
    }

    @Test
    fun `boundary quality 0 and 100 are valid`() {
        val low = VipsEncodeOptions(quality = 0)
        val high = VipsEncodeOptions(quality = 100)
        low.quality shouldBeEqualTo 0
        high.quality shouldBeEqualTo 100
    }

    @Test
    fun `boundary effort 1 and 9 are valid`() {
        val min = VipsEncodeOptions(effort = 1)
        val max = VipsEncodeOptions(effort = 9)
        min.effort shouldBeEqualTo 1
        max.effort shouldBeEqualTo 9
    }

    // ─── companion constants ─────────────────────────────────────────────────

    @Test
    fun `Default has expected values`() {
        VipsEncodeOptions.Default.quality shouldBeEqualTo 85
        VipsEncodeOptions.Default.effort shouldBeEqualTo 4
        VipsEncodeOptions.Default.lossless shouldBeEqualTo false
        VipsEncodeOptions.Default.stripMetadata shouldBeEqualTo true
    }

    @Test
    fun `HighQuality has expected values`() {
        VipsEncodeOptions.HighQuality.quality shouldBeEqualTo 95
        VipsEncodeOptions.HighQuality.effort shouldBeEqualTo 6
    }

    @Test
    fun `LowBandwidth has expected values`() {
        VipsEncodeOptions.LowBandwidth.quality shouldBeEqualTo 60
        VipsEncodeOptions.LowBandwidth.effort shouldBeEqualTo 3
    }

    // ─── Java serialization round-trip ───────────────────────────────────────

    @Test
    fun `serialization round-trip preserves all fields`() {
        val original = VipsEncodeOptions(quality = 75, effort = 7, lossless = true, stripMetadata = false)

        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use { it.writeObject(original) }

        val restored = ObjectInputStream(ByteArrayInputStream(baos.toByteArray())).use {
            it.readObject() as VipsEncodeOptions
        }

        restored.quality shouldBeEqualTo original.quality
        restored.effort shouldBeEqualTo original.effort
        restored.lossless shouldBeEqualTo original.lossless
        restored.stripMetadata shouldBeEqualTo original.stripMetadata
    }

    @Test
    fun `Default round-trips cleanly`() {
        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use { it.writeObject(VipsEncodeOptions.Default) }
        val restored = ObjectInputStream(ByteArrayInputStream(baos.toByteArray())).use {
            it.readObject() as VipsEncodeOptions
        }
        restored shouldBeEqualTo VipsEncodeOptions.Default
    }
}
