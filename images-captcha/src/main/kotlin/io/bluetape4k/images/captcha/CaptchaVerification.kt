package io.bluetape4k.images.captcha

import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 발급된 CAPTCHA challenge의 application-visible identifier입니다.
 */
@JvmInline
value class CaptchaChallengeId(
    val value: String,
): Serializable {

    init {
        value.requireNotBlank("value")
    }

    override fun toString(): String =
        value
}

/**
 * rendering된 CAPTCHA challenge가 verification을 기다리는 동안 저장되는 serializable metadata입니다.
 *
 * 이 metadata를 인코딩 image byte와 함께 저장합니다. [CaptchaChallenge]는 scrimage
 * [com.sksamuel.scrimage.ImmutableImage]를 포함하므로 직접 serialize하지 않습니다.
 */
data class IssuedCaptchaChallenge(
    val id: CaptchaChallengeId,
    val answer: String,
    val expiresAt: Instant,
): Serializable {

    init {
        answer.requireNotBlank("answer")
    }

    companion object {
        private const val serialVersionUID: Long = -4817890773200146887L

        /**
         * 생성된 image challenge에서 persistence용 verification metadata를 만듭니다.
         */
        fun from(id: CaptchaChallengeId, challenge: CaptchaChallenge): IssuedCaptchaChallenge =
            IssuedCaptchaChallenge(
                id = id,
                answer = challenge.text,
                expiresAt = challenge.expiresAt,
            )
    }
}

/**
 * 발급된 CAPTCHA metadata의 storage boundary입니다.
 *
 * 구현체는 [consume]을 single-use로 만들어야 합니다. [consume]이 challenge를 한 번
 * 반환하면 같은 id의 이후 호출은 `null`을 반환해야 합니다.
 */
interface CaptchaChallengeStore {

    /**
     * 발급된 challenge metadata를 저장하거나 교체합니다.
     */
    fun save(challenge: IssuedCaptchaChallenge): IssuedCaptchaChallenge

    /**
     * 단일 verification attempt를 위해 metadata를 원자적으로 제거하고 반환합니다.
     */
    fun consume(id: CaptchaChallengeId): IssuedCaptchaChallenge?
}

/**
 * JVM-local in-memory CAPTCHA metadata store입니다.
 *
 * 이 구현체는 test, demo, single-node application 용도입니다. [save]는 새 challenge를
 * 삽입하기 전에 stale entry를 제거한 뒤, 가장 이른 expiration을 가진 entry부터 evict해
 * [maxEntries]를 강제합니다. 저장·만료 정리·eviction은 하나의 critical section에서
 * 수행되므로 [save]가 반환될 때 저장소 크기는 항상 [maxEntries] 이하입니다. expiration이
 * 같으면 challenge id가 사전순으로 앞선 항목을 먼저 제거합니다.
 *
 * application instance들이 발급 challenge를 공유해야 하거나 cleanup이 challenge issue
 * traffic과 독립적으로 실행되어야 한다면 distributed store 구현체를 사용합니다.
 */
class InMemoryCaptchaChallengeStore @JvmOverloads constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
): CaptchaChallengeStore {

    init {
        maxEntries.requirePositiveNumber("maxEntries")
    }

    private val challenges = ConcurrentHashMap<CaptchaChallengeId, IssuedCaptchaChallenge>()
    private val lock = Any()

    override fun save(challenge: IssuedCaptchaChallenge): IssuedCaptchaChallenge =
        synchronized(lock) {
            removeExpiredLocked(clock.instant())
            challenges[challenge.id] = challenge
            trimToMaxEntriesLocked()
            challenge
        }

    override fun consume(id: CaptchaChallengeId): IssuedCaptchaChallenge? =
        synchronized(lock) { challenges.remove(id) }

    /**
     * 현재 저장된 challenge 수입니다. test와 diagnostic을 위해 노출합니다.
     */
    val size: Int
        get() = synchronized(lock) { challenges.size }

    /**
     * 만료된 challenge를 제거하고 제거된 entry 수를 반환합니다.
     */
    fun removeExpired(now: Instant = clock.instant()): Int =
        synchronized(lock) { removeExpiredLocked(now) }

    private fun removeExpiredLocked(now: Instant): Int {
        var removed = 0
        challenges.forEach { (id, challenge) ->
            if (!challenge.expiresAt.isAfter(now) && challenges.remove(id, challenge)) {
                removed++
            }
        }
        return removed
    }

    private fun trimToMaxEntriesLocked() {
        val overflow = challenges.size - maxEntries
        if (overflow <= 0) return

        challenges.entries
            .sortedWith(compareBy<Map.Entry<CaptchaChallengeId, IssuedCaptchaChallenge>> { it.value.expiresAt }
                .thenBy { it.key.value })
            .take(overflow)
            .forEach { (id, challenge) ->
                challenges.remove(id, challenge)
            }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES: Int = 10_000
    }
}

/**
 * 기대 CAPTCHA answer와 사용자 입력을 비교하는 strategy입니다.
 */
fun interface CaptchaAnswerMatcher {

    fun matches(expected: String, actual: String): Boolean

    companion object {

        /**
         * 사용자 입력의 앞뒤 space를 trim한 뒤 exact 비교를 수행합니다.
         */
        fun exact(): CaptchaAnswerMatcher =
            CaptchaAnswerMatcher { expected, actual -> expected == actual.trim() }

        /**
         * 사용자 입력의 앞뒤 space를 trim한 뒤 case-insensitive 비교를 수행합니다.
         */
        fun caseInsensitive(): CaptchaAnswerMatcher =
            CaptchaAnswerMatcher { expected, actual -> expected.equals(actual.trim(), ignoreCase = true) }
    }
}

/**
 * one-shot CAPTCHA verification attempt의 result입니다.
 */
sealed interface CaptchaVerificationResult: Serializable {

    val id: CaptchaChallengeId

    /**
     * [Success]인 경우에만 `true`를 반환합니다.
     */
    val verified: Boolean
        get() = this is Success

    data class Success(
        override val id: CaptchaChallengeId,
    ): CaptchaVerificationResult

    data class WrongAnswer(
        override val id: CaptchaChallengeId,
    ): CaptchaVerificationResult

    data class Expired(
        override val id: CaptchaChallengeId,
        val expiredAt: Instant,
        val checkedAt: Instant,
    ): CaptchaVerificationResult

    data class NotFound(
        override val id: CaptchaChallengeId,
    ): CaptchaVerificationResult
}

/**
 * CAPTCHA challenge metadata를 발급하고 검증합니다.
 *
 * verification은 one-shot입니다. [verify] 호출은 answer 비교 전에 저장된 challenge를
 * consume합니다. 이 방식은 같은 challenge id에 대한 replay와 brute-force retry를 막습니다.
 * wrong answer나 expired challenge도 id를 consume하므로 같은 id로 retry하면
 * [CaptchaVerificationResult.NotFound]가 반환됩니다.
 *
 * 예:
 *
 * ```kotlin
 * val store = InMemoryCaptchaChallengeStore()
 * val verifier = CaptchaVerificationService(store)
 * val issued = verifier.issue(CaptchaChallengeId("login-1"), generator.generate())
 * val result = verifier.verify(issued.id, "A7K9QZ")
 * ```
 */
class CaptchaVerificationService(
    private val store: CaptchaChallengeStore = InMemoryCaptchaChallengeStore(),
    private val clock: Clock = Clock.systemUTC(),
    private val answerMatcher: CaptchaAnswerMatcher = CaptchaAnswerMatcher.exact(),
) {

    /**
     * 생성된 challenge의 verification metadata를 저장합니다.
     */
    fun issue(id: CaptchaChallengeId, challenge: CaptchaChallenge): IssuedCaptchaChallenge =
        store.save(IssuedCaptchaChallenge.from(id, challenge))

    /**
     * 사용자 answer를 검증하고 저장된 challenge를 consume합니다.
     */
    fun verify(id: CaptchaChallengeId, answer: String): CaptchaVerificationResult {
        val issued = store.consume(id) ?: return CaptchaVerificationResult.NotFound(id)
        val checkedAt = clock.instant()

        if (!issued.expiresAt.isAfter(checkedAt)) {
            return CaptchaVerificationResult.Expired(
                id = id,
                expiredAt = issued.expiresAt,
                checkedAt = checkedAt,
            )
        }

        return if (answerMatcher.matches(issued.answer, answer)) {
            CaptchaVerificationResult.Success(id)
        } else {
            CaptchaVerificationResult.WrongAnswer(id)
        }
    }
}
