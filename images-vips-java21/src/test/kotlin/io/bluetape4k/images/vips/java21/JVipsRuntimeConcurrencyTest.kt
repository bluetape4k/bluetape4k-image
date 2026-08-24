package io.bluetape4k.images.vips.java21

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.vips.VipsConcurrencySupport
import io.bluetape4k.images.vips.VipsInitializationException
import io.bluetape4k.images.vips.java21.internal.DefaultJVipsNativeRuntime
import io.bluetape4k.images.vips.java21.internal.JVipsNativeRuntime
import io.bluetape4k.junit5.concurrency.MultithreadingTester
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * [JVipsRuntime.init] concurrent call이 native init을 정확히 한 번만 실행하고,
 * sequential repeated call이 idempotent인지 검증합니다.
 *
 * 실제 libvips 설치가 없어도 되도록 [JVipsNativeRuntime] adapter seam을 사용합니다.
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

    @Test
    fun `repeated init with the same effective configuration is idempotent`() {
        JVipsRuntime.init(concurrency = 3, maxPixels = 1_000L)
        JVipsRuntime.init(concurrency = 3, maxPixels = 1_000L)

        initCount.get() shouldBeEqualTo 1
        JVipsRuntime.maxPixels shouldBeEqualTo 1_000L
        JVipsRuntime.concurrencyCapability.requested shouldBeEqualTo 3
    }

    @Test
    fun `initialized runtime rejects a different configuration with requested and effective values`() {
        JVipsRuntime.init(concurrency = 3, maxPixels = 1_000L)

        val error = assertFailsWith<VipsInitializationException> {
            JVipsRuntime.init(concurrency = 5, maxPixels = 2_000L)
        }

        error.message shouldContain "requested=(concurrency=5, maxPixels=2000)"
        error.message shouldContain "effective=(concurrency=3, maxPixels=1000)"
        initCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `initialized runtime still validates invalid arguments`() {
        JVipsRuntime.init()

        assertFailsWith<IllegalArgumentException> {
            JVipsRuntime.init(concurrency = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            JVipsRuntime.init(maxPixels = 0)
        }

        initCount.get() shouldBeEqualTo 1
        JVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `concurrent loser rejects a different configuration after owner initialization`() {
        // owner를 INITIALIZING에 고정해 loser의 대기 후 설정 비교 경로를 결정적으로 검증합니다.
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        JVipsRuntime.nativeRuntime = object : JVipsNativeRuntime {
            override fun nativeInit(concurrency: Int) {
                nativeInitStarted.countDown()
                releaseNativeInit.await(5, TimeUnit.SECONDS).shouldBeTrue()
                initCount.incrementAndGet()
            }

            override fun nativeShutdown() = Unit
        }

        val ownerFailure = AtomicReference<Throwable?>()
        val owner = Thread.ofPlatform().daemon(true).start {
            try {
                JVipsRuntime.init(concurrency = 3, maxPixels = 1_000L)
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        nativeInitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val loserFailure = AtomicReference<Throwable?>()
        val loser = Thread.ofPlatform().daemon(true).start {
            try {
                JVipsRuntime.init(concurrency = 3, maxPixels = 2_000L)
            } catch (t: Throwable) {
                loserFailure.set(t)
            }
        }

        releaseNativeInit.countDown()
        owner.join(5_000)
        loser.join(5_000)
        owner.isAlive.shouldBeFalse()
        loser.isAlive.shouldBeFalse()
        ownerFailure.get().shouldBeNull()

        val error = loserFailure.get().shouldNotBeNull() as VipsInitializationException
        error.message shouldContain "requested=(concurrency=3, maxPixels=2000)"
        error.message shouldContain "effective=(concurrency=3, maxPixels=1000)"
        initCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `initialized runtime reports requested and effective concurrency`() {
        JVipsRuntime.init(concurrency = 3)

        JVipsRuntime.concurrencyCapability.support shouldBeEqualTo VipsConcurrencySupport.CONFIGURABLE
        JVipsRuntime.concurrencyCapability.requested shouldBeEqualTo 3
        JVipsRuntime.concurrencyCapability.effective shouldBeEqualTo 3
    }

    @Test
    fun `invalid init arguments are rejected before native initialization`() {
        listOf(0, -1).forEach { concurrency ->
            assertFailsWith<IllegalArgumentException> {
                JVipsRuntime.init(concurrency = concurrency)
            }
        }
        listOf(0L, -1L).forEach { maxPixels ->
            assertFailsWith<IllegalArgumentException> {
                JVipsRuntime.init(maxPixels = maxPixels)
            }
        }

        initCount.get() shouldBeEqualTo 0
        JVipsRuntime.isInitialized.shouldBeFalse()
    }

    @Test
    fun `shutdown wins over invalid init arguments`() {
        JVipsRuntime.init()
        JVipsRuntime.shutdown()

        assertFailsWith<VipsInitializationException> {
            JVipsRuntime.init(concurrency = 0)
        }
        assertFailsWith<VipsInitializationException> {
            JVipsRuntime.init(maxPixels = 0)
        }
    }
}
