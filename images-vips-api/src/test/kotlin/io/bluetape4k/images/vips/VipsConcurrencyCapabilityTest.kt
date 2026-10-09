package io.bluetape4k.images.vips

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class VipsConcurrencyCapabilityTest {

    companion object: KLogging()

    @Test
    fun `capability preserves requested effective and support state`() {
        val capability = VipsConcurrencyCapability(
            support = VipsConcurrencySupport.CONFIGURABLE,
            requested = 3,
            effective = 3,
        )

        log.debug { "capability=$capability" }
        capability.support shouldBeEqualTo VipsConcurrencySupport.CONFIGURABLE
        capability.requested shouldBeEqualTo 3
        capability.effective shouldBeEqualTo 3
    }
}
