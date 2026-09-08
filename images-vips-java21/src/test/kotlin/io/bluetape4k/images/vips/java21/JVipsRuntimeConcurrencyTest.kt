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
 * [JVipsRuntime.init] concurrent call이 native init을 정확히 한 번만 실행하고,
 * sequential repeated call이 idempotent인지 검증합니다.
 *
 * 실제 libvips 설치가 없어도 되도록 [JVipsNativeRuntime] adapter seam을 사용합니다.
 */
class JVipsRuntimeConcurrencyTest {

    companion object : KLogging()

    private val initCount = AtomicInteger(0)
    private val shutdownCount = AtomicInteger(0)

    private val testAdapter = object : JVipsNativeRuntime {
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
        JVipsRuntime.resetForTest()
        JVipsRuntime.nativeRuntime = testAdapter
        initCount.set(0)
        shutdownCount.set(0)
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
    fun `default wait cap matches the shared api contract`() {
        JVipsRuntime.initializationWaitTimeoutNanos shouldBeEqualTo
            TimeUnit.SECONDS.toNanos(VipsInitializationWaitContract.DEFAULT_TIMEOUT_SECONDS)
    }

    @Test
    fun `failed owner returns to retryable state`() {
        val attempts = AtomicInteger(0)
        JVipsRuntime.nativeRuntime = object : JVipsNativeRuntime {
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
            JVipsRuntime.init()
        }
        JVipsRuntime.isInitialized.shouldBeFalse()
        JVipsRuntime.isShutdown.shouldBeFalse()

        JVipsRuntime.init()
        attempts.get() shouldBeEqualTo 2
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
    fun `interrupted competing init waiter exits without changing owner state`() {
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        JVipsRuntime.nativeRuntime = object : JVipsNativeRuntime {
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
                JVipsRuntime.init()
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
                JVipsRuntime.init()
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
            JVipsRuntime.isInitialized.shouldBeFalse()
            JVipsRuntime.isShutdown.shouldBeFalse()
            shutdownCount.get() shouldBeEqualTo 0
        } finally {
            releaseNativeInit.countDown()
            owner.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        ownerFailure.get().shouldBeNull()
        initCount.get() shouldBeEqualTo 1
        JVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `timed out competing init waiter exits without changing owner state`() {
        JVipsRuntime.initializationWaitTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(25)
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        JVipsRuntime.nativeRuntime = object : JVipsNativeRuntime {
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
                JVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        nativeInitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiterFailure = AtomicReference<Throwable?>()
        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                JVipsRuntime.init()
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
            JVipsRuntime.isInitialized.shouldBeFalse()
            JVipsRuntime.isShutdown.shouldBeFalse()
            shutdownCount.get() shouldBeEqualTo 0
        } finally {
            releaseNativeInit.countDown()
            owner.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        ownerFailure.get().shouldBeNull()
        initCount.get() shouldBeEqualTo 1
        JVipsRuntime.isInitialized.shouldBeTrue()
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

        JVipsRuntime.nativeRuntime = object : JVipsNativeRuntime {
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
        JVipsRuntime.initializationWaitStartedHook = {
            when (waitStarts.incrementAndGet()) {
                1 -> waiterWaitStarted.countDown()
                2 -> waiterSecondWaitStarted.countDown()
            }
        }
        JVipsRuntime.initializationWaitCompletedHook = {
            if (completionHookUsed.compareAndSet(false, true)) {
                val retryOwner = Thread.ofPlatform().daemon(true).start {
                    try {
                        JVipsRuntime.init()
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
                JVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        firstOwnerEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                JVipsRuntime.init()
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
            JVipsRuntime.isInitialized.shouldBeFalse()
            JVipsRuntime.isShutdown.shouldBeFalse()
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
        JVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `competing init waiter enforces one deadline across retry owners`() {
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(250)
        JVipsRuntime.initializationWaitTimeoutNanos = timeoutNanos
        val clock = AtomicLong(0L)
        JVipsRuntime.initializationWaitClock = clock::get
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

        JVipsRuntime.nativeRuntime = object : JVipsNativeRuntime {
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
        JVipsRuntime.initializationWaitStartedHook = {
            when (waitStarts.incrementAndGet()) {
                1 -> waiterFirstWaitStarted.countDown()
                2 -> waiterSecondWaitStarted.countDown()
            }
        }
        JVipsRuntime.initializationWaitCompletedHook = {
            if (completionHookUsed.compareAndSet(false, true)) {
                val retryOwner = Thread.ofPlatform().daemon(true).start {
                    try {
                        JVipsRuntime.init()
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
                JVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
                ownerFailureReady.countDown()
            }
        }
        firstOwnerEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                JVipsRuntime.init()
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
            JVipsRuntime.isInitialized.shouldBeFalse()
            JVipsRuntime.isShutdown.shouldBeFalse()
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
        JVipsRuntime.isInitialized.shouldBeTrue()
    }

    @Test
    fun `interrupted shutdown waiter does not release owner native state`() {
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        JVipsRuntime.nativeRuntime = object : JVipsNativeRuntime {
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
                JVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        nativeInitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiterFailure = AtomicReference<Throwable?>()
        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                JVipsRuntime.shutdown()
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
            JVipsRuntime.isInitialized.shouldBeFalse()
            JVipsRuntime.isShutdown.shouldBeFalse()
            shutdownCount.get() shouldBeEqualTo 0
        } finally {
            releaseNativeInit.countDown()
            owner.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        ownerFailure.get().shouldBeNull()
        JVipsRuntime.isInitialized.shouldBeTrue()
        JVipsRuntime.shutdown()
        shutdownCount.get() shouldBeEqualTo 1
        JVipsRuntime.isShutdown.shouldBeTrue()
    }

    @Test
    fun `timed out shutdown waiter does not release owner native state`() {
        JVipsRuntime.initializationWaitTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(25)
        val nativeInitStarted = CountDownLatch(1)
        val releaseNativeInit = CountDownLatch(1)
        JVipsRuntime.nativeRuntime = object : JVipsNativeRuntime {
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
                JVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
            }
        }
        nativeInitStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiterFailure = AtomicReference<Throwable?>()
        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                JVipsRuntime.shutdown()
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
            JVipsRuntime.isInitialized.shouldBeFalse()
            JVipsRuntime.isShutdown.shouldBeFalse()
            shutdownCount.get() shouldBeEqualTo 0
        } finally {
            releaseNativeInit.countDown()
            owner.join(5_000)
        }

        owner.isAlive.shouldBeFalse()
        ownerFailure.get().shouldBeNull()
        JVipsRuntime.isInitialized.shouldBeTrue()
        JVipsRuntime.shutdown()
        shutdownCount.get() shouldBeEqualTo 1
        JVipsRuntime.isShutdown.shouldBeTrue()
    }

    @Test
    fun `shutdown waiter enforces one deadline across retry owners`() {
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(250)
        JVipsRuntime.initializationWaitTimeoutNanos = timeoutNanos
        val clock = AtomicLong(0L)
        JVipsRuntime.initializationWaitClock = clock::get
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

        JVipsRuntime.nativeRuntime = object : JVipsNativeRuntime {
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
        JVipsRuntime.initializationWaitStartedHook = {
            when (waitStarts.incrementAndGet()) {
                1 -> waiterFirstWaitStarted.countDown()
                2 -> waiterSecondWaitStarted.countDown()
            }
        }
        JVipsRuntime.initializationWaitCompletedHook = {
            if (completionHookUsed.compareAndSet(false, true)) {
                val retryOwner = Thread.ofPlatform().daemon(true).start {
                    try {
                        JVipsRuntime.init()
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
                JVipsRuntime.init()
            } catch (t: Throwable) {
                ownerFailure.set(t)
                ownerFailureReady.countDown()
            }
        }
        firstOwnerEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()

        val waiter = Thread.ofPlatform().daemon(true).start {
            try {
                JVipsRuntime.shutdown()
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
            JVipsRuntime.isInitialized.shouldBeFalse()
            JVipsRuntime.isShutdown.shouldBeFalse()
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
        JVipsRuntime.isInitialized.shouldBeTrue()
        JVipsRuntime.shutdown()
        shutdownCount.get() shouldBeEqualTo 1
        JVipsRuntime.isShutdown.shouldBeTrue()
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
