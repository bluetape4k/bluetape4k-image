package io.bluetape4k.images.vips.java21

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.vips.java21.internal.DefaultJVipsNativeRuntime
import io.bluetape4k.images.vips.java21.internal.JVipsNativeRuntime
import io.bluetape4k.junit5.concurrency.MultithreadingTester
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies that concurrent calls to [JVipsRuntime.init] execute native init exactly once,
 * and that sequential repeated calls are idempotent.
 *
 * Uses a [JVipsNativeRuntime] adapter seam so no real libvips installation is required.
 */
class JVipsRuntimeConcurrencyTest {

    companion object : KLogging()

    private val initCount = AtomicInteger(0)

    private val testAdapter = object : JVipsNativeRuntime {
        override fun nativeInit(concurrency: Int) {
            Thread.sleep(20) // keep the INITIALIZING window open so contenders overlap
            initCount.incrementAndGet()
        }
        override fun nativeShutdown() {}
    }

    @BeforeEach
    fun setup() {
        JVipsRuntime.resetForTest()
        JVipsRuntime.nativeRuntime = testAdapter
        initCount.set(0)
    }

    @AfterEach
    fun teardown() {
        JVipsRuntime.resetForTest()
        JVipsRuntime.nativeRuntime = DefaultJVipsNativeRuntime
    }

    @Test
    fun `concurrent init calls native init exactly once with platform threads`() {
        MultithreadingTester()
            .workers(10)
            .rounds(1)
            .add { JVipsRuntime.init() }
            .run()

        initCount.get() shouldBeEqualTo 1
        JVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `concurrent init calls native init exactly once with virtual threads`() {
        StructuredTaskScopeTester()
            .rounds(10)
            .add { JVipsRuntime.init() }
            .run()

        initCount.get() shouldBeEqualTo 1
        JVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `repeated sequential init is idempotent`() {
        JVipsRuntime.init()
        JVipsRuntime.init()
        JVipsRuntime.init()

        initCount.get() shouldBeEqualTo 1
        JVipsRuntime.isInitialized.shouldBeTrue()
    }
}
