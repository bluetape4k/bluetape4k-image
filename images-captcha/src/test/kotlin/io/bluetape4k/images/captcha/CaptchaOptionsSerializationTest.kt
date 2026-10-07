package io.bluetape4k.images.captcha

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.io.serializer.JdkBinarySerializer
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class CaptchaOptionsSerializationTest {

    companion object: KLogging()


    @Test
    fun `jdk serializable options round trip keeps duration and singleton options`() {
        val options = CaptchaOptions(
            expiresAfter = 3.minutes,
            noise = CaptchaNoise.Low,
            distortion = CaptchaDistortion.None,
        )
        log.debug { "options=$options" }

        val restored = roundTripJdk(options).shouldNotBeNull()

        log.debug { "restored=$restored" }

        restored shouldBeEqualTo options
        restored.expiresAfter shouldBeEqualTo 3.minutes
        restored.noise shouldBeSameInstanceAs CaptchaNoise.Low
        restored.distortion shouldBeSameInstanceAs CaptchaDistortion.None
    }


    @Test
    fun `fory serializable options round trip keeps duration and singleton options`() {
        val options = CaptchaOptions(
            expiresAfter = 3.minutes,
            noise = CaptchaNoise.Low,
            distortion = CaptchaDistortion.None,
        )
        log.debug { "options=$options" }

        val restored = roundTripFory(options).shouldNotBeNull()

        log.debug { "restored=$restored" }

        restored shouldBeEqualTo options
        restored.expiresAfter shouldBeEqualTo 3.minutes
        restored.noise shouldBeSameInstanceAs CaptchaNoise.Low
        restored.distortion shouldBeSameInstanceAs CaptchaDistortion.None
    }

    @Suppress("DEPRECATION")
    private fun <T: Any> roundTripJdk(value: T): T? {
        // NOTE: 현재 JDK_DEFAULT_OBJECT_INPUT_FILTER 에는 `java.awt.**` 가 등록되어 있지 않아 `BinarySerializer.Jdk` 를 사용하면 예외가 발생한다.
        val jdkSerializer = JdkBinarySerializer(DEFAULT_BUFFER_SIZE, null)
        val bytes = jdkSerializer.serialize(value).shouldNotBeEmpty()
        return jdkSerializer.deserialize(bytes)
    }

    private fun <T: Any> roundTripFory(value: T): T? {
        val bytes = BinarySerializers.FastFory.serialize(value).shouldNotBeEmpty()
        return BinarySerializers.FastFory.deserialize(bytes)
    }
}
