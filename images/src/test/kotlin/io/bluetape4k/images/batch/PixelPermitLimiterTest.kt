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

        // Run a sequence of permits — if not released, the second call would deadlock
        limiter.withPermit(500L) { }
        limiter.withPermit(500L) { }
        limiter.withPermit(500L) { } // third — works because earlier ones released
        // Reaching here without timeout means permits were properly released
    }

    @Test
    fun `withPermit with pixelCount exceeding maxPixels is coerced to maxPixels`() = runTest {
        // pixelCount is coerced to [MIN_PIXEL_PERMIT, maxPixels], so a very large
        // request should succeed immediately (treated as maxPixels)
        val limiter = PixelPermitLimiter(maxPixels = 100L)
        val counter = AtomicInteger(0)

        limiter.withPermit(pixelCount = 99_999L) { counter.incrementAndGet() }

        counter.get() shouldBeEqualTo 1
    }

    @Test
    fun `withPermit with zero pixelCount is coerced to MIN_PIXEL_PERMIT and succeeds`() = runTest {
        val limiter = PixelPermitLimiter(maxPixels = 1_000L)
        val counter = AtomicInteger(0)

        // pixelCount 0 is coerced to MIN_PIXEL_PERMIT (1)
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
        // Fill the limiter so the second acquire must wait
        val maxPixels = 100L
        val limiter = PixelPermitLimiter(maxPixels = maxPixels)

        // Occupy all permits in a long-running block
        var holderStarted = false
        val holder = launch {
            limiter.withPermit(maxPixels) {
                holderStarted = true
                // simulate holding permits for a while by suspending the test
                delay(timeMillis = Long.MAX_VALUE)
            }
        }

        // Wait until holder has started
        while (!holderStarted) {
            kotlinx.coroutines.yield()
        }

        // Now launch a waiter — it cannot acquire immediately
        val waiter = launch {
            limiter.withPermit(1L) {
                // Should not reach here during this test
            }
        }

        // Cancel the waiter; it must not throw or deadlock
        waiter.cancelAndJoin()

        // Cancel the holder too
        holder.cancelAndJoin()
    }
}
