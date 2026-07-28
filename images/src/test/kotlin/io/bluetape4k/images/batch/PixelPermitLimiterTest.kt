package io.bluetape4k.images.batch

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.images.AbstractImageTest
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith

class PixelPermitLimiterTest : AbstractImageTest() {

    @Test
    fun `withPermit executes block and returns result when permits available`() = runTest {
        val limiter = PixelPermitLimiter(maxPixels = 10_000L)
        val result = limiter.withPermit(1_000L) { "done" }
        result shouldBeEqualTo "done"
    }

    @Test
    fun `withPermit releases permits after block completes`() = runTest {
        val limiter = PixelPermitLimiter(maxPixels = 1_000L)

        // permit을 순차 실행합니다. release되지 않으면 두 번째 호출이 deadlock됩니다.
        limiter.withPermit(500L) { }
        limiter.withPermit(500L) { }
        limiter.withPermit(500L) { } // 세 번째 호출은 이전 permit이 release되어 동작합니다.
        // timeout 없이 여기까지 도달하면 permit이 제대로 release된 것입니다.
    }

    @Test
    fun `withPermit with pixelCount exceeding maxPixels is coerced to maxPixels`() = runTest {
        // pixelCount는 [MIN_PIXEL_PERMIT, maxPixels]로 보정되므로, 매우 큰 요청도
        // maxPixels로 취급되어 즉시 성공해야 합니다.
        val limiter = PixelPermitLimiter(maxPixels = 100L)
        val counter = AtomicInteger(0)

        limiter.withPermit(pixelCount = 99_999L) { counter.incrementAndGet() }

        counter.get() shouldBeEqualTo 1
    }

    @Test
    fun `withPermit with zero pixelCount is coerced to MIN_PIXEL_PERMIT and succeeds`() = runTest {
        val limiter = PixelPermitLimiter(maxPixels = 1_000L)
        val counter = AtomicInteger(0)

        // pixelCount 0은 MIN_PIXEL_PERMIT(1)로 보정됩니다.
        limiter.withPermit(pixelCount = 0L) { counter.incrementAndGet() }

        counter.get() shouldBeEqualTo 1
    }

    @Test
    fun `constructor rejects zero maxPixels`() {
        assertFailsWith<IllegalArgumentException> {
            PixelPermitLimiter(maxPixels = 0L)
        }
    }

    @Test
    fun `constructor rejects negative maxPixels`() {
        assertFailsWith<IllegalArgumentException> {
            PixelPermitLimiter(maxPixels = -1L)
        }
    }

    @Test
    fun `concurrent withPermit calls complete without corruption`() = runTest {
        val limiter = PixelPermitLimiter(maxPixels = 50_000L)
        val counter = AtomicInteger(0)
        val jobs = (1..20).map {
            launch {
                limiter.withPermit(1_000L) {
                    counter.incrementAndGet()
                }
            }
        }
        jobs.joinAll()

        counter.get() shouldBeEqualTo 20
    }

    @Test
    fun `suspended waiter is removed on cancellation`() = runTest {
        // 두 번째 acquire가 기다리도록 limiter를 채웁니다.
        val maxPixels = 100L
        val limiter = PixelPermitLimiter(maxPixels = maxPixels)

        // 오래 실행되는 block에서 모든 permit을 점유합니다.
        var holderStarted = false
        val holder = launch {
            limiter.withPermit(maxPixels) {
                holderStarted = true
                // test를 suspend해서 permit을 한동안 보유하는 상황을 흉내 냅니다.
                delay(timeMillis = Long.MAX_VALUE)
            }
        }

        // holder가 시작될 때까지 기다립니다.
        while (!holderStarted) {
            kotlinx.coroutines.yield()
        }

        // 이제 waiter를 시작합니다. 즉시 acquire할 수 없습니다.
        val waiter = launch {
            limiter.withPermit(1L) {
                // 이 test 중에는 여기까지 도달하면 안 됩니다.
            }
        }

        // waiter를 cancel합니다. 예외나 deadlock이 발생하면 안 됩니다.
        waiter.cancelAndJoin()

        // holder도 cancel합니다.
        holder.cancelAndJoin()
    }
}
