package io.bluetape4k.images.vips.java25

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.vips.VipsInitializationException
import io.bluetape4k.images.vips.java25.internal.DefaultFfmVipsNativeRuntime
import io.bluetape4k.images.vips.java25.internal.FfmVipsNativeRuntime
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies that concurrent calls to [FfmVipsRuntime.init] execute native init exactly once,
 * and that sequential repeated calls are idempotent.
 *
 * Uses a [FfmVipsNativeRuntime] adapter seam so no real libvips installation is required.
 */
class FfmVipsRuntimeConcurrencyTest {

    companion object : KLogging()

    private val initCount = AtomicInteger(0)

    // startLatch holds all workers until they are all queued, forcing genuine overlap.
    // nativeInit sleeps briefly so contenders actually enter init() concurrently.
    private lateinit var startLatch: CountDownLatch

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
        startLatch = CountDownLatch(1)
    }

    @AfterEach
    fun teardown() {
        FfmVipsRuntime.resetForTest()
        FfmVipsRuntime.nativeRuntime = DefaultFfmVipsNativeRuntime
    }

    @Test
    fun `concurrent init calls native init exactly once`() {
        val concurrency = 10
        val readyLatch = CountDownLatch(concurrency)

        runBlocking(Dispatchers.Default) {
            repeat(concurrency) {
                launch {
                    readyLatch.countDown()
                    startLatch.await() // all workers wait until the gate opens
                    FfmVipsRuntime.init()
                }
            }
            readyLatch.await() // wait until all workers are blocked at the gate
            startLatch.countDown() // release all workers simultaneously
        }

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
