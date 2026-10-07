package io.bluetape4k.images.captcha

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.javatimes.seconds
import io.bluetape4k.junit5.concurrency.MultithreadingTester
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.utils.Runtimex
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

class CaptchaVerificationServiceTest {

    companion object: KLogging()

    @Test
    fun `verify succeeds and consumes issued challenge`() {
        val clock = MutableClock(Instant.parse("2026-05-24T00:00:00Z"))
        val store = InMemoryCaptchaChallengeStore()
        val service = CaptchaVerificationService(store = store, clock = clock)
        val issued = service.issue(CaptchaChallengeId("challenge-1"), newChallenge(clock))

        val result = service.verify(issued.id, issued.answer)
        val replay = service.verify(issued.id, issued.answer)

        log.debug { "result=$result" }
        log.debug { "replay=$replay" }

        result.shouldBeInstanceOf<CaptchaVerificationResult.Success>()
        result.verified.shouldBeTrue()

        replay.shouldBeInstanceOf<CaptchaVerificationResult.NotFound>()
        store.size shouldBeEqualTo 0
    }

    @Test
    fun `verify returns wrong answer and still consumes challenge`() {
        val clock = MutableClock(Instant.parse("2026-05-24T00:00:00Z"))
        val store = InMemoryCaptchaChallengeStore()
        val service = CaptchaVerificationService(store = store, clock = clock)
        val issued = service.issue(CaptchaChallengeId("challenge-2"), newChallenge(clock))

        val result = service.verify(issued.id, "WRONG")
        val retry = service.verify(issued.id, issued.answer)

        log.debug { "result=$result" }
        log.debug { "retry=$retry" }

        result.shouldBeInstanceOf<CaptchaVerificationResult.WrongAnswer>()
        result.verified.shouldBeFalse()

        retry.shouldBeInstanceOf<CaptchaVerificationResult.NotFound>()
        store.size shouldBeEqualTo 0
    }

    @Test
    fun `verify returns expired and consumes stale challenge`() {
        val clock = MutableClock(Instant.parse("2026-05-24T00:00:00Z"))
        val store = InMemoryCaptchaChallengeStore()
        val service = CaptchaVerificationService(store = store, clock = clock)
        val issued = service.issue(CaptchaChallengeId("challenge-3"), newChallenge(clock))

        clock.instant = Instant.parse("2026-05-24T00:02:00Z")

        val result = service.verify(issued.id, issued.answer)
        val replay = service.verify(issued.id, issued.answer)

        log.debug { "result=$result" }
        log.debug { "replay=$replay" }

        result.shouldBeInstanceOf<CaptchaVerificationResult.Expired>()
        result.expiredAt shouldBeEqualTo Instant.parse("2026-05-24T00:01:00Z")
        result.checkedAt shouldBeEqualTo Instant.parse("2026-05-24T00:02:00Z")

        replay.shouldBeInstanceOf<CaptchaVerificationResult.NotFound>()
        store.size shouldBeEqualTo 0
    }

    @Test
    fun `case insensitive matcher accepts normalized user input`() {
        val clock = MutableClock(Instant.parse("2026-05-24T00:00:00Z"))
        val service = CaptchaVerificationService(
            clock = clock,
            answerMatcher = CaptchaAnswerMatcher.caseInsensitive(),
        )
        val issued = service.issue(CaptchaChallengeId("challenge-4"), newChallenge(clock))
        log.debug { "issued=$issued" }

        val result = service.verify(issued.id, " ${issued.answer.lowercase()} ")
        log.debug { "result=$result" }
        result.shouldBeInstanceOf<CaptchaVerificationResult.Success>()
    }

    @Test
    fun `in-memory store removes expired challenges on save`() {
        val clock = MutableClock(Instant.parse("2026-05-24T00:00:00Z"))
        val store = InMemoryCaptchaChallengeStore(clock = clock)
        val expired = issued("expired", "OLD", Instant.parse("2026-05-24T00:00:30Z"))
        val active = issued("active", "NEW", Instant.parse("2026-05-24T00:05:00Z"))

        store.save(expired)
        clock.instant = Instant.parse("2026-05-24T00:01:00Z")
        store.save(active)

        store.size shouldBeEqualTo 1
        store.consume(expired.id).shouldBeNull()
        store.consume(active.id) shouldBeEqualTo active
    }

    @Test
    fun `in-memory store evicts earliest expiring challenges over max entries`() {
        val clock = MutableClock(Instant.parse("2026-05-24T00:00:00Z"))
        val store = InMemoryCaptchaChallengeStore(clock = clock, maxEntries = 2)
        val first = issued("first", "ONE", Instant.parse("2026-05-24T00:03:00Z"))
        val second = issued("second", "TWO", Instant.parse("2026-05-24T00:04:00Z"))
        val third = issued("third", "THREE", Instant.parse("2026-05-24T00:05:00Z"))

        store.save(first)
        store.save(second)
        store.save(third)

        store.size shouldBeEqualTo 2
        store.consume(first.id).shouldBeNull()
        store.consume(second.id) shouldBeEqualTo second
        store.consume(third.id) shouldBeEqualTo third
    }

    @Test
    fun `in-memory store keeps hard max under concurrent saves`() {
        val clock = MutableClock(Instant.parse("2026-05-24T00:00:00Z"))
        val store = InMemoryCaptchaChallengeStore(clock = clock, maxEntries = 3)
        val sequence = AtomicInteger()
        val maximumObserved = AtomicInteger()

        MultithreadingTester()
            .workers(Runtimex.availableProcessors)
            .rounds(20)
            .add {
                val number = sequence.incrementAndGet()
                store.save(issued("concurrent-$number", "ANSWER", clock.instant() + 60.seconds()))
                val currentSize = store.size
                maximumObserved.updateAndGet { previous -> maxOf(previous, currentSize) }
            }
            .run()

        maximumObserved.get() shouldBeEqualTo 3
        store.size shouldBeEqualTo 3
        sequence.get() shouldBeEqualTo Runtimex.availableProcessors * 20
    }

    @Test
    fun `in-memory store keeps hard max under virtual threads saves`() {
        val clock = MutableClock(Instant.parse("2026-05-24T00:00:00Z"))
        val store = InMemoryCaptchaChallengeStore(clock = clock, maxEntries = 3)
        val sequence = AtomicInteger()
        val maximumObserved = AtomicInteger()

        StructuredTaskScopeTester()
            .rounds(Runtimex.availableProcessors * 20)
            .add {
                val number = sequence.incrementAndGet()
                store.save(issued("concurrent-$number", "ANSWER", clock.instant() + 60.seconds()))
                val currentSize = store.size
                maximumObserved.updateAndGet { previous -> maxOf(previous, currentSize) }
            }
            .run()

        maximumObserved.get() shouldBeEqualTo 3
        store.size shouldBeEqualTo 3
        sequence.get() shouldBeEqualTo Runtimex.availableProcessors * 20
    }

    private fun newChallenge(clock: Clock): CaptchaChallenge {
        val generator = captchaGenerator(clock) {
            length(6)
            charSet("ABC123")
            expiresAfter(1.minutes)
        }

        return generator.generate()
    }

    private fun issued(id: String, answer: String, expiresAt: Instant): IssuedCaptchaChallenge =
        IssuedCaptchaChallenge(
            id = CaptchaChallengeId(id),
            answer = answer,
            expiresAt = expiresAt,
        )

    private class MutableClock(
        var instant: Instant,
        private val zone: ZoneId = ZoneOffset.UTC,
    ): Clock() {

        override fun getZone(): ZoneId =
            zone

        override fun withZone(zone: ZoneId): Clock =
            MutableClock(instant, zone)

        override fun instant(): Instant =
            instant
    }
}
