package io.bluetape4k.images.vips

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.io.serializer.JdkBinarySerializer
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class VipsEncodeOptionsTest {

    companion object: KLogging()

    // ─── validation 검증 ─────────────────────────────────────────────────────

    @Test
    fun `quality -1 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            VipsEncodeOptions(quality = -1)
        }
    }

    @Test
    fun `quality 101 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            VipsEncodeOptions(quality = 101)
        }
    }

    @Test
    fun `effort 0 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            VipsEncodeOptions(effort = 0)
        }
    }

    @Test
    fun `effort 10 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            VipsEncodeOptions(effort = 10)
        }
    }

    @Test
    fun `boundary quality 0 and 100 are valid`() {
        val low = VipsEncodeOptions(quality = 0)
        val high = VipsEncodeOptions(quality = 100)

        log.debug { "low=$low, high=$high" }

        low.quality shouldBeEqualTo 0
        high.quality shouldBeEqualTo 100
    }

    @Test
    fun `boundary effort 1 and 9 are valid`() {
        val min = VipsEncodeOptions(effort = 1)
        val max = VipsEncodeOptions(effort = 9)

        log.debug { "min=$min, max=$max" }
        min.effort shouldBeEqualTo 1
        max.effort shouldBeEqualTo 9
    }

    // ─── companion 상수 검증 ─────────────────────────────────────────────────

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

    // ─── Java serialization round-trip 검증 ─────────────────────────────────

    @Test
    fun `serialization round-trip preserves all fields`() {
        val original = VipsEncodeOptions(quality = 75, effort = 7, lossless = true, stripMetadata = false)
        log.debug { "original=$original" }

        val restored = jdkRoundtrip(original).shouldNotBeNull()

        log.debug { "restored=$restored" }
        restored.quality shouldBeEqualTo original.quality
        restored.effort shouldBeEqualTo original.effort
        restored.lossless shouldBeEqualTo original.lossless
        restored.stripMetadata shouldBeEqualTo original.stripMetadata
    }

    @Test
    fun `Default round-trips cleanly`() {
        val restored = jdkRoundtrip(VipsEncodeOptions.Default).shouldNotBeNull()
        restored shouldBeEqualTo VipsEncodeOptions.Default
    }

    private fun <T: Any> jdkRoundtrip(original: T): T? {
        val serializer = JdkBinarySerializer(objectInputFilter = null)
        val bytes = serializer.serialize(original)
        return serializer.deserialize(bytes)
    }

    // ─── Fory serialization round-trip 검증 ─────────────────────────────────

    @Test
    fun `fory serialization round-trip preserves all fields`() {
        val original = VipsEncodeOptions(quality = 75, effort = 7, lossless = true, stripMetadata = false)
        log.debug { "original=$original" }

        val restored = foryRoundtrip(original).shouldNotBeNull()

        log.debug { "restored=$restored" }
        restored.quality shouldBeEqualTo original.quality
        restored.effort shouldBeEqualTo original.effort
        restored.lossless shouldBeEqualTo original.lossless
        restored.stripMetadata shouldBeEqualTo original.stripMetadata
    }

    @Test
    fun `fory Default round-trips cleanly`() {
        val restored = foryRoundtrip(VipsEncodeOptions.Default).shouldNotBeNull()
        restored shouldBeEqualTo VipsEncodeOptions.Default
    }

    private fun <T: Any> foryRoundtrip(original: T): T? {
        val bytes = BinarySerializers.FastFory.serialize(original)
        return BinarySerializers.FastFory.deserialize(bytes)
    }
}
