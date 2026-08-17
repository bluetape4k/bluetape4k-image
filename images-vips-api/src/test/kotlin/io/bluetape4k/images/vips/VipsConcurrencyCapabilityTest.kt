package io.bluetape4k.images.vips

import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class VipsConcurrencyCapabilityTest {

    @Test
    fun `capability preserves requested effective and support state`() {
        val capability = VipsConcurrencyCapability(
            support = VipsConcurrencySupport.CONFIGURABLE,
            requested = 3,
            effective = 3,
        )

        capability.support shouldBeEqualTo VipsConcurrencySupport.CONFIGURABLE
        capability.requested shouldBeEqualTo 3
        capability.effective shouldBeEqualTo 3
    }
}
