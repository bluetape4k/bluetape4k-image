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
import io.bluetape4k.images.vips.testfixtures.VipsInitializationWaitContract
import io.bluetape4k.junit5.concurrency.MultithreadingTester
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    private val shutdownCount = AtomicInteger(0)

    private val testAdapter = object : FfmVipsNativeRuntime {
        override fun nativeInit(concurrency: Int) {
            Thread.sleep(20) // keep the INITIALIZING window open so contenders overlap
            initCount.incrementAndGet()
        }
        override fun nativeShutdown() {
            shutdownCount.incrementAndGet()
        }
    }

    @BeforeEach
    fun setup() {
        FfmVipsRuntime.resetForTest()
        FfmVipsRuntime.nativeRuntime = testAdapter
        initCount.set(0)
        shutdownCount.set(0)
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
    fun `default wait cap matches the shared api contract`() {
        FfmVipsRuntime.initializationWaitTimeoutNanos shouldBeEqualTo
            TimeUnit.SECONDS.toNanos(VipsInitializationWaitContract.DEFAULT_TIMEOUT_SECONDS)
    }

    @Test
    fun `failed owner returns to retryable state`() {
        val attempts = AtomicInteger(0)
        FfmVipsRuntime.nativeRuntime = object : FfmVipsNativeRuntime {
            override fun nativeInit(concurrency: Int) {
                if (attempts.getAndIncrement() == 0) {
                    throw IllegalStateException("synthetic initialization failure")
                }
                initCount.incrementAndGet()
            }

            override fun nativeShutdown() {
                shutdownCount.incrementAndGet()
            }
        }

        assertFailsWith<VipsInitializationException> {
            FfmVipsRuntime.init()
        }
        FfmVipsRuntime.isInitialized.shouldBeFalse()
        FfmVipsRuntime.isShutdown.shouldBeFalse()

        FfmVipsRuntime.init()
        attempts.get() shouldBeEqualTo 2
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
    fun `interrupted competing init waiter exits without changing owner state`() {
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        FfmVipsRuntime.nativeRuntime = object : FfmVipsNativeRuntime {
            override fun nativeInit(concurrency: Int) {
                nativeInitStarted.countDown()
                releaseNativeInit.await(5, TimeUnit.SECONDS).shouldBeTrue()
                initCount.incrementAndGet()
            }

            override fun nativeShutdown() {
                shutdownCount.incrementAndGet()
            }
        }

        val ownerFailure = AtomicReference<Throwable?>()
        val owner = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        nativeInitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiterStarted = CountDownLatch(1)
        val waiterFailure = AtomicReference<Throwable?>()
        val waiter = Thread.ofPlatform().daemon(true).start {
            waiterStarted.countDown()
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                waiterFailure.set(t)
            }
        }
        waiterStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()
        waiter.interrupt()
        waiter.join(5_000)

        try {
            waiter.isAlive.shouldBeFalse()
            val error = waiterFailure.get().shouldNotBeNull() as VipsInitializationException
            VipsInitializationWaitContract.assertInterrupted(error)
            waiter.isInterrupted.shouldBeTrue()
            FfmVipsRuntime.isInitialized.shouldBeFalse()
            FfmVipsRuntime.isShutdown.shouldBeFalse()
            shutdownCount.get() shouldBeEqualTo 0
        } finally {
            releaseNativeInit.countDown()
            owner.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        ownerFailure.get().shouldBeNull()
        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `timed out competing init waiter exits without changing owner state`() {
        FfmVipsRuntime.initializationWaitTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(25)
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        FfmVipsRuntime.nativeRuntime = object : FfmVipsNativeRuntime {
            override fun nativeInit(concurrency: Int) {
                nativeInitStarted.countDown()
                releaseNativeInit.await(5, TimeUnit.SECONDS).shouldBeTrue()
                initCount.incrementAndGet()
            }

            override fun nativeShutdown() {
                shutdownCount.incrementAndGet()
            }
        }

        val ownerFailure = AtomicReference<Throwable?>()
        val owner = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        nativeInitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiterFailure = AtomicReference<Throwable?>()
        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                waiterFailure.set(t)
            }
        }
        waiter.join(5_000)

        try {
            waiter.isAlive.shouldBeFalse()
            val error = waiterFailure.get().shouldNotBeNull() as VipsInitializationException
            VipsInitializationWaitContract.assertTimedOut(error)
            waiter.isInterrupted.shouldBeFalse()
            FfmVipsRuntime.isInitialized.shouldBeFalse()
            FfmVipsRuntime.isShutdown.shouldBeFalse()
            shutdownCount.get() shouldBeEqualTo 0
        } finally {
            releaseNativeInit.countDown()
            owner.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        ownerFailure.get().shouldBeNull()
        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `competing init waiter rechecks when retry owner starts after failure`() {
        val firstOwnerEntered = CountDownLatch(1)
        val releaseFirstOwner = CountDownLatch(1)
        val retryOwnerEntered = CountDownLatch(1)
        val releaseRetryOwner = CountDownLatch(1)
        val waiterWaitStarted = CountDownLatch(1)
        val waiterSecondWaitStarted = CountDownLatch(1)
        val attempts = AtomicInteger(0)
        val waitStarts = AtomicInteger(0)
        val completionHookUsed = AtomicBoolean(false)
        val ownerFailure = AtomicReference<Throwable?>()
        val retryFailure = AtomicReference<Throwable?>()
        val waiterFailure = AtomicReference<Throwable?>()
        val retryOwnerReference = AtomicReference<Thread?>()

        FfmVipsRuntime.nativeRuntime = object : FfmVipsNativeRuntime {
            override fun nativeInit(concurrency: Int) {
                when (attempts.incrementAndGet()) {
                    1 -> {
                        firstOwnerEntered.countDown()
                        releaseFirstOwner.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        throw IllegalStateException("synthetic owner failure")
                    }

                    else -> {
                        retryOwnerEntered.countDown()
                        releaseRetryOwner.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        initCount.incrementAndGet()
                    }
                }
            }

            override fun nativeShutdown() {
                shutdownCount.incrementAndGet()
            }
        }
        FfmVipsRuntime.initializationWaitStartedHook = {
            when (waitStarts.incrementAndGet()) {
                1 -> waiterWaitStarted.countDown()
                2 -> waiterSecondWaitStarted.countDown()
            }
        }
        FfmVipsRuntime.initializationWaitCompletedHook = {
            if (completionHookUsed.compareAndSet(false, true)) {
                val retryOwner = Thread.ofPlatform().daemon(true).start {
                    try {
                        FfmVipsRuntime.init()
                    } catch (t: Throwable) {
                        retryFailure.set(t)
                    }
                }
                retryOwnerReference.set(retryOwner)
                retryOwnerEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()
            }
        }

        val owner = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        firstOwnerEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                waiterFailure.set(t)
            }
        }
        waiterWaitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()
        releaseFirstOwner.countDown()
        waiterSecondWaitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        try {
            waiter.isAlive.shouldBeTrue()
            waiterFailure.get().shouldBeNull()
            FfmVipsRuntime.isInitialized.shouldBeFalse()
            FfmVipsRuntime.isShutdown.shouldBeFalse()
        } finally {
            releaseRetryOwner.countDown()
            releaseFirstOwner.countDown()
            owner.join(5_000)
            waiter.join(5_000)
            retryOwnerReference.get()?.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        waiter.isAlive.shouldBeFalse()
        retryOwnerReference.get()?.isAlive.shouldBeFalse()
        ownerFailure.get().shouldNotBeNull()
        retryFailure.get().shouldBeNull()
        waiterFailure.get().shouldBeNull()
        attempts.get() shouldBeEqualTo 2
        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `competing init waiter enforces one deadline across retry owners`() {
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(250)
        FfmVipsRuntime.initializationWaitTimeoutNanos = timeoutNanos
        val clock = AtomicLong(0L)
        FfmVipsRuntime.initializationWaitClock = clock::get
        val firstOwnerEntered = CountDownLatch(1)
        val releaseFirstOwner = CountDownLatch(1)
        val retryOwnerEntered = CountDownLatch(1)
        val releaseRetryOwner = CountDownLatch(1)
        val waiterFirstWaitStarted = CountDownLatch(1)
        val waiterSecondWaitStarted = CountDownLatch(1)
        val attempts = AtomicInteger(0)
        val waitStarts = AtomicInteger(0)
        val completionHookUsed = AtomicBoolean(false)
        val ownerFailure = AtomicReference<Throwable?>()
        val ownerFailureReady = CountDownLatch(1)
        val retryFailure = AtomicReference<Throwable?>()
        val waiterFailure = AtomicReference<Throwable?>()
        val retryOwnerReference = AtomicReference<Thread?>()

        FfmVipsRuntime.nativeRuntime = object : FfmVipsNativeRuntime {
            override fun nativeInit(concurrency: Int) {
                when (attempts.incrementAndGet()) {
                    1 -> {
                        firstOwnerEntered.countDown()
                        releaseFirstOwner.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        throw IllegalStateException("synthetic owner failure")
                    }

                    else -> {
                        retryOwnerEntered.countDown()
                        releaseRetryOwner.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        initCount.incrementAndGet()
                    }
                }
            }

            override fun nativeShutdown() {
                shutdownCount.incrementAndGet()
            }
        }
        FfmVipsRuntime.initializationWaitStartedHook = {
            when (waitStarts.incrementAndGet()) {
                1 -> waiterFirstWaitStarted.countDown()
                2 -> waiterSecondWaitStarted.countDown()
            }
        }
        FfmVipsRuntime.initializationWaitCompletedHook = {
            if (completionHookUsed.compareAndSet(false, true)) {
                val retryOwner = Thread.ofPlatform().daemon(true).start {
                    try {
                        FfmVipsRuntime.init()
                    } catch (t: Throwable) {
                        retryFailure.set(t)
                    }
                }
                retryOwnerReference.set(retryOwner)
                retryOwnerEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()
            }
        }

        val owner = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
                ownerFailureReady.countDown()
            }
        }
        firstOwnerEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                waiterFailure.set(t)
            }
        }
        waiterFirstWaitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()
        releaseFirstOwner.countDown()
        ownerFailureReady.await(5, TimeUnit.SECONDS).shouldBeTrue()
        clock.set(timeoutNanos)
        waiterSecondWaitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        try {
            waiter.join(2_000)
            waiter.isAlive.shouldBeFalse()
            val error = waiterFailure.get().shouldNotBeNull() as VipsInitializationException
            VipsInitializationWaitContract.assertTimedOut(error)
            FfmVipsRuntime.isInitialized.shouldBeFalse()
            FfmVipsRuntime.isShutdown.shouldBeFalse()
        } finally {
            releaseRetryOwner.countDown()
            releaseFirstOwner.countDown()
            owner.join(5_000)
            waiter.join(5_000)
            retryOwnerReference.get()?.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        waiter.isAlive.shouldBeFalse()
        retryOwnerReference.get()?.isAlive.shouldBeFalse()
        ownerFailure.get().shouldNotBeNull()
        retryFailure.get().shouldBeNull()
        attempts.get() shouldBeEqualTo 2
        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `interrupted shutdown waiter does not release owner native state`() {
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        FfmVipsRuntime.nativeRuntime = object : FfmVipsNativeRuntime {
            override fun nativeInit(concurrency: Int) {
                nativeInitStarted.countDown()
                releaseNativeInit.await(5, TimeUnit.SECONDS).shouldBeTrue()
                initCount.incrementAndGet()
            }

            override fun nativeShutdown() {
                shutdownCount.incrementAndGet()
            }
        }

        val ownerFailure = AtomicReference<Throwable?>()
        val owner = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        nativeInitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiterFailure = AtomicReference<Throwable?>()
        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.shutdown()
            } catch (t: Throwable) {
                waiterFailure.set(t)
            }
        }
        waiter.interrupt()
        waiter.join(5_000)

        try {
            waiter.isAlive.shouldBeFalse()
            val error = waiterFailure.get().shouldNotBeNull() as VipsInitializationException
            VipsInitializationWaitContract.assertInterrupted(error)
            waiter.isInterrupted.shouldBeTrue()
            FfmVipsRuntime.isInitialized.shouldBeFalse()
            FfmVipsRuntime.isShutdown.shouldBeFalse()
            shutdownCount.get() shouldBeEqualTo 0
        } finally {
            releaseNativeInit.countDown()
            owner.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        ownerFailure.get().shouldBeNull()
        FfmVipsRuntime.isInitialized.shouldBeTrue()
        FfmVipsRuntime.shutdown()
        shutdownCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isShutdown.shouldBeTrue()
    }

    @Test
    fun `timed out shutdown waiter does not release owner native state`() {
        FfmVipsRuntime.initializationWaitTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(25)
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        FfmVipsRuntime.nativeRuntime = object : FfmVipsNativeRuntime {
            override fun nativeInit(concurrency: Int) {
                nativeInitStarted.countDown()
                releaseNativeInit.await(5, TimeUnit.SECONDS).shouldBeTrue()
                initCount.incrementAndGet()
            }

            override fun nativeShutdown() {
                shutdownCount.incrementAndGet()
            }
        }

        val ownerFailure = AtomicReference<Throwable?>()
        val owner = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        nativeInitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiterFailure = AtomicReference<Throwable?>()
        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.shutdown()
            } catch (t: Throwable) {
                waiterFailure.set(t)
            }
        }
        waiter.join(5_000)

        try {
            waiter.isAlive.shouldBeFalse()
            val error = waiterFailure.get().shouldNotBeNull() as VipsInitializationException
            VipsInitializationWaitContract.assertTimedOut(error)
            waiter.isInterrupted.shouldBeFalse()
            FfmVipsRuntime.isInitialized.shouldBeFalse()
            FfmVipsRuntime.isShutdown.shouldBeFalse()
            shutdownCount.get() shouldBeEqualTo 0
        } finally {
            releaseNativeInit.countDown()
            owner.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        ownerFailure.get().shouldBeNull()
        FfmVipsRuntime.isInitialized.shouldBeTrue()
        FfmVipsRuntime.shutdown()
        shutdownCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isShutdown.shouldBeTrue()
    }

    @Test
    fun `shutdown waiter enforces one deadline across retry owners`() {
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(250)
        FfmVipsRuntime.initializationWaitTimeoutNanos = timeoutNanos
        val clock = AtomicLong(0L)
        FfmVipsRuntime.initializationWaitClock = clock::get
        val firstOwnerEntered = CountDownLatch(1)
        val releaseFirstOwner = CountDownLatch(1)
        val retryOwnerEntered = CountDownLatch(1)
        val releaseRetryOwner = CountDownLatch(1)
        val waiterFirstWaitStarted = CountDownLatch(1)
        val waiterSecondWaitStarted = CountDownLatch(1)
        val attempts = AtomicInteger(0)
        val waitStarts = AtomicInteger(0)
        val completionHookUsed = AtomicBoolean(false)
        val ownerFailure = AtomicReference<Throwable?>()
        val ownerFailureReady = CountDownLatch(1)
        val retryFailure = AtomicReference<Throwable?>()
        val waiterFailure = AtomicReference<Throwable?>()
        val retryOwnerReference = AtomicReference<Thread?>()

        FfmVipsRuntime.nativeRuntime = object : FfmVipsNativeRuntime {
            override fun nativeInit(concurrency: Int) {
                when (attempts.incrementAndGet()) {
                    1 -> {
                        firstOwnerEntered.countDown()
                        releaseFirstOwner.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        throw IllegalStateException("synthetic owner failure")
                    }

                    else -> {
                        retryOwnerEntered.countDown()
                        releaseRetryOwner.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        initCount.incrementAndGet()
                    }
                }
            }

            override fun nativeShutdown() {
                shutdownCount.incrementAndGet()
            }
        }
        FfmVipsRuntime.initializationWaitStartedHook = {
            when (waitStarts.incrementAndGet()) {
                1 -> waiterFirstWaitStarted.countDown()
                2 -> waiterSecondWaitStarted.countDown()
            }
        }
        FfmVipsRuntime.initializationWaitCompletedHook = {
            if (completionHookUsed.compareAndSet(false, true)) {
                val retryOwner = Thread.ofPlatform().daemon(true).start {
                    try {
                        FfmVipsRuntime.init()
                    } catch (t: Throwable) {
                        retryFailure.set(t)
                    }
                }
                retryOwnerReference.set(retryOwner)
                retryOwnerEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()
            }
        }

        val owner = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
                ownerFailureReady.countDown()
            }
        }
        firstOwnerEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                FfmVipsRuntime.shutdown()
            } catch (t: Throwable) {
                waiterFailure.set(t)
            }
        }
        waiterFirstWaitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()
        releaseFirstOwner.countDown()
        ownerFailureReady.await(5, TimeUnit.SECONDS).shouldBeTrue()
        clock.set(timeoutNanos)
        waiterSecondWaitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        try {
            waiter.join(2_000)
            waiter.isAlive.shouldBeFalse()
            val error = waiterFailure.get().shouldNotBeNull() as VipsInitializationException
            VipsInitializationWaitContract.assertTimedOut(error)
            FfmVipsRuntime.isInitialized.shouldBeFalse()
            FfmVipsRuntime.isShutdown.shouldBeFalse()
            shutdownCount.get() shouldBeEqualTo 0
        } finally {
            releaseRetryOwner.countDown()
            releaseFirstOwner.countDown()
            owner.join(5_000)
            waiter.join(5_000)
            retryOwnerReference.get()?.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        waiter.isAlive.shouldBeFalse()
        retryOwnerReference.get()?.isAlive.shouldBeFalse()
        ownerFailure.get().shouldNotBeNull()
        retryFailure.get().shouldBeNull()
        attempts.get() shouldBeEqualTo 2
        initCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isInitialized.shouldBeTrue()
        FfmVipsRuntime.shutdown()
        shutdownCount.get() shouldBeEqualTo 1
        FfmVipsRuntime.isShutdown.shouldBeTrue()
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
