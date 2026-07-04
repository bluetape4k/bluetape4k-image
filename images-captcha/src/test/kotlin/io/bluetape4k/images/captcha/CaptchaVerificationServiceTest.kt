package io.bluetape4k.images.captcha

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class CaptchaVerificationServiceTest {

    @Test
    fun `verify succeeds and consumes issued challenge`() {
        val clock = MutableClock(Instant.parse("2026-05-24T00:00:00Z"))
        val store = InMemoryCaptchaChallengeStore()
        val service = CaptchaVerificationService(store = store, clock = clock)
        val issued = service.issue(CaptchaChallengeId("challenge-1"), newChallenge(clock))

        val result = service.verify(issued.id, issued.answer)
        val replay = service.verify(issued.id, issued.answer)

        result shouldBeInstanceOf CaptchaVerificationResult.Success::class
        result.verified shouldBeEqualTo true
        replay shouldBeInstanceOf CaptchaVerificationResult.NotFound::class
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

        result shouldBeInstanceOf CaptchaVerificationResult.WrongAnswer::class
        result.verified shouldBeEqualTo false
        retry shouldBeInstanceOf CaptchaVerificationResult.NotFound::class
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

        result shouldBeInstanceOf CaptchaVerificationResult.Expired::class
        (result as CaptchaVerificationResult.Expired).expiredAt shouldBeEqualTo Instant.parse("2026-05-24T00:01:00Z")
        result.checkedAt shouldBeEqualTo Instant.parse("2026-05-24T00:02:00Z")
        replay shouldBeInstanceOf CaptchaVerificationResult.NotFound::class
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

        val result = service.verify(issued.id, " ${issued.answer.lowercase()} ")

        result shouldBeInstanceOf CaptchaVerificationResult.Success::class
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
        store.consume(expired.id) shouldBeEqualTo null
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
        store.consume(first.id) shouldBeEqualTo null
        store.consume(second.id) shouldBeEqualTo second
        store.consume(third.id) shouldBeEqualTo third
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
