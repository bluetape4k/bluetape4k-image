package io.bluetape4k.images.vips.java25

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.vips.VipsInitializationException
import io.bluetape4k.images.vips.java25.internal.DefaultFfmVipsNativeRuntime
import io.bluetape4k.images.vips.java25.internal.FfmVipsNativeRuntime
import io.bluetape4k.junit5.concurrency.MultithreadingTester
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [FfmVipsRuntime.init] concurrent call이 native init을 정확히 한 번만 실행하고,
 * sequential repeated call이 idempotent인지 검증합니다.
 *
 * 실제 libvips 설치가 없어도 되도록 [FfmVipsNativeRuntime] adapter seam을 사용합니다.
 */
class FfmVipsRuntimeConcurrencyTest {

    companion object : KLogging()

    private val initCount = AtomicInteger(0)

    private val testAdapter = object : FfmVipsNativeRuntime {
        override fun nativeInit(concurrency: Int) {
            Thread.sleep(20) // keep the INITIALIZING window open so contenders overlap
            initCount.incrementAndGet()
        }
        override fun nativeShutdown() {}
    }

    @BeforeEach
    fun setup() {
        FfmVipsRuntime.resetForTest()
        FfmVipsRuntime.nativeRuntime = testAdapter
        initCount.set(0)
    }

    @AfterEach
    fun teardown() {
        FfmVipsRuntime.resetForTest()
        FfmVipsRuntime.nativeRuntime = DefaultFfmVipsNativeRuntime
    }

    @Test
    fun `concurrent init calls native init exactly once with platform threads`() {
        MultithreadingTester()
            .workers(10)
            .rounds(1)
            .add { FfmVipsRuntime.init() }
            .run()

        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `concurrent init calls native init exactly once with virtual threads`() {
        StructuredTaskScopeTester()
            .rounds(10)
            .add { FfmVipsRuntime.init() }
            .run()

        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `repeated sequential init is idempotent`() {
        FfmVipsRuntime.init()
        FfmVipsRuntime.init()
        FfmVipsRuntime.init()

        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `init after shutdown throws VipsInitializationException`() {
        FfmVipsRuntime.init()
        FfmVipsRuntime.shutdown()

        assertFailsWith<VipsInitializationException> {
            FfmVipsRuntime.init()
        }
    }
}
