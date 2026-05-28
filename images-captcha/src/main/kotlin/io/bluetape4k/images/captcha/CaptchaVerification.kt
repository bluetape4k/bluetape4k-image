package io.bluetape4k.images.captcha

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-visible identifier for an issued CAPTCHA challenge.
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
 * Serializable metadata stored while a rendered CAPTCHA challenge is waiting
 * for verification.
 *
 * Store this metadata alongside the encoded image bytes. Do not serialize
 * [CaptchaChallenge] directly because it contains scrimage [com.sksamuel.scrimage.ImmutableImage].
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
         * Creates persisted verification metadata from a generated image challenge.
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
 * Storage boundary for issued CAPTCHA metadata.
 *
 * Implementations should make [consume] single-use: once a challenge is returned
 * by [consume], subsequent calls for the same id should return `null`.
 */
interface CaptchaChallengeStore {

    /**
     * Stores or replaces metadata for an issued challenge.
     */
    fun save(challenge: IssuedCaptchaChallenge): IssuedCaptchaChallenge

    /**
     * Atomically removes and returns metadata for a single verification attempt.
     */
    fun consume(id: CaptchaChallengeId): IssuedCaptchaChallenge?
}

/**
 * JVM-local in-memory CAPTCHA metadata store.
 *
 * This implementation is intended for tests, demos, and single-node
 * applications. Use a distributed store implementation when application
 * instances must share issued challenges.
 */
class InMemoryCaptchaChallengeStore: CaptchaChallengeStore {

    private val challenges = ConcurrentHashMap<CaptchaChallengeId, IssuedCaptchaChallenge>()

    override fun save(challenge: IssuedCaptchaChallenge): IssuedCaptchaChallenge {
        challenges[challenge.id] = challenge
        return challenge
    }

    override fun consume(id: CaptchaChallengeId): IssuedCaptchaChallenge? =
        challenges.remove(id)

    /**
     * Number of currently stored challenges. Exposed for tests and diagnostics.
     */
    val size: Int
        get() = challenges.size
}

/**
 * Strategy used to compare the expected CAPTCHA answer with user input.
 */
fun interface CaptchaAnswerMatcher {

    fun matches(expected: String, actual: String): Boolean

    companion object {

        /**
         * Exact comparison after trimming leading and trailing user-input spaces.
         */
        fun exact(): CaptchaAnswerMatcher =
            CaptchaAnswerMatcher { expected, actual -> expected == actual.trim() }

        /**
         * Case-insensitive comparison after trimming leading and trailing user-input spaces.
         */
        fun caseInsensitive(): CaptchaAnswerMatcher =
            CaptchaAnswerMatcher { expected, actual -> expected.equals(actual.trim(), ignoreCase = true) }
    }
}

/**
 * Result of a one-shot CAPTCHA verification attempt.
 */
sealed interface CaptchaVerificationResult: Serializable {

    val id: CaptchaChallengeId

    /**
     * Returns `true` only for [Success].
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
 * Issues and verifies CAPTCHA challenge metadata.
 *
 * Verification is one-shot: every call to [verify] consumes the stored
 * challenge before comparing the answer. This prevents replay and brute-force
 * retries against the same challenge id.
 *
 * Example:
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
     * Stores verification metadata for a generated challenge.
     */
    fun issue(id: CaptchaChallengeId, challenge: CaptchaChallenge): IssuedCaptchaChallenge =
        store.save(IssuedCaptchaChallenge.from(id, challenge))

    /**
     * Verifies a user answer and consumes the stored challenge.
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
