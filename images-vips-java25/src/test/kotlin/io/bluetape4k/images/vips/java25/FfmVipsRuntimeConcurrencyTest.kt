package io.bluetape4k.images.vips.java25

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.vips.VipsConcurrencySupport
import io.bluetape4k.images.vips.VipsInitializationException
import io.bluetape4k.images.vips.java25.internal.DefaultFfmVipsNativeRuntime
import io.bluetape4k.images.vips.java25.internal.FfmVipsNativeRuntime
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
    fun `repeated init with the same effective configuration is idempotent`() {
        FfmVipsRuntime.init(maxPixels = 1_000L)
        FfmVipsRuntime.init(maxPixels = 1_000L)

        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.maxPixels shouldBeEqualTo 1_000L
        FfmVipsRuntime.concurrencyCapability.requested shouldBeEqualTo 4
    }

    @Test
    fun `initialized runtime rejects a different max pixels configuration with requested and effective values`() {
        FfmVipsRuntime.init(maxPixels = 1_000L)

        val error = assertFailsWith<VipsInitializationException> {
            FfmVipsRuntime.init(maxPixels = 2_000L)
        }

        error.message shouldContain "requested=(concurrency=4, maxPixels=2000)"
        error.message shouldContain "effective=(concurrency=4, maxPixels=1000)"
        initCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `initialized runtime still validates invalid arguments`() {
        FfmVipsRuntime.init()

        assertFailsWith<IllegalArgumentException> {
            FfmVipsRuntime.init(concurrency = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            FfmVipsRuntime.init(maxPixels = 0)
        }

        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `concurrent loser rejects a different max pixels configuration after owner initialization`() {
        // owner를 INITIALIZING에 고정해 loser의 대기 후 설정 비교 경로를 결정적으로 검증합니다.
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        FfmVipsRuntime.nativeRuntime = object : FfmVipsNativeRuntime {
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
                FfmVipsRuntime.init(maxPixels = 1_000L)
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        nativeInitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val loserFailure = AtomicReference<Throwable?>()
        val loser = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init(maxPixels = 2_000L)
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
        error.message shouldContain "requested=(concurrency=4, maxPixels=2000)"
        error.message shouldContain "effective=(concurrency=4, maxPixels=1000)"
        initCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `initialized runtime reports unsupported effective concurrency explicitly`() {
        FfmVipsRuntime.init()

        FfmVipsRuntime.concurrencyCapability.support shouldBeEqualTo VipsConcurrencySupport.UNSUPPORTED
        FfmVipsRuntime.concurrencyCapability.requested shouldBeEqualTo 4
        FfmVipsRuntime.concurrencyCapability.effective shouldBeEqualTo null
        FfmVipsRuntime.concurrencyCapability.reason shouldContain "does not expose"
    }

    @Test
    fun `initialized runtime preserves unsupported non-default concurrency`() {
        FfmVipsRuntime.init(maxPixels = 1_000L)

        val error = assertFailsWith<VipsInitializationException> {
            FfmVipsRuntime.init(concurrency = 2, maxPixels = 2_000L)
        }

        error.message shouldContain "requested=2"
        error.message shouldContain "effective=unknown"
        error.message shouldContain "support=UNSUPPORTED"
        FfmVipsRuntime.maxPixels shouldBeEqualTo 1_000L
        initCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `invalid init arguments are rejected before native initialization`() {
        listOf(0, -1).forEach { concurrency ->
            assertFailsWith<IllegalArgumentException> {
                FfmVipsRuntime.init(concurrency = concurrency)
            }
        }
        listOf(0L, -1L).forEach { maxPixels ->
            assertFailsWith<IllegalArgumentException> {
                FfmVipsRuntime.init(maxPixels = maxPixels)
            }
        }

        initCount.get() shouldBeEqualTo 0
        FfmVipsRuntime.isInitialized.shouldBeFalse()
    }

    @Test
    fun `unsupported non-default concurrency is rejected before native initialization`() {
        val error = assertFailsWith<VipsInitializationException> {
            FfmVipsRuntime.init(concurrency = 2)
        }

        error.message shouldContain "requested=2"
        error.message shouldContain "effective=unknown"
        error.message shouldContain "support=UNSUPPORTED"
        initCount.get() shouldBeEqualTo 0
        FfmVipsRuntime.isInitialized.shouldBeFalse()
    }

    @Test
    fun `init after shutdown throws VipsInitializationException`() {
        FfmVipsRuntime.init()
        FfmVipsRuntime.shutdown()

        assertFailsWith<VipsInitializationException> {
            FfmVipsRuntime.init()
        }
    }

    @Test
    fun `shutdown wins over invalid and unsupported init arguments`() {
        FfmVipsRuntime.init()
        FfmVipsRuntime.shutdown()

        assertFailsWith<VipsInitializationException> {
            FfmVipsRuntime.init(concurrency = 0)
        }
        assertFailsWith<VipsInitializationException> {
            FfmVipsRuntime.init(maxPixels = 0)
        }
        assertFailsWith<VipsInitializationException> {
            FfmVipsRuntime.init(concurrency = 2)
        }
    }
}
