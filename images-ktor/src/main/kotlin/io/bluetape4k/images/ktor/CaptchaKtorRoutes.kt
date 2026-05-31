package io.bluetape4k.images.ktor

import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.codec.Base58
import io.bluetape4k.images.captcha.CaptchaChallengeId
import io.bluetape4k.images.captcha.CaptchaGenerator
import io.bluetape4k.images.captcha.CaptchaVerificationResult
import io.bluetape4k.images.captcha.CaptchaVerificationService
import io.bluetape4k.images.captcha.captchaGenerator
import io.bluetape4k.ktor.core.ApiErrorResponse
import io.bluetape4k.ktor.core.intQueryParameter
import io.bluetape4k.ktor.core.requiredPathParameter
import io.bluetape4k.ktor.core.respondApiError
import io.bluetape4k.support.requireNotBlank
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Base64

private const val DEFAULT_CAPTCHA_ROUTE = "/captcha"
private const val DEFAULT_CONTENT_TYPE = "image/png"
private const val DEFAULT_ID_LENGTH = 16

/**
 * Ktor route configuration for issuing and verifying bluetape4k CAPTCHA challenges.
 *
 * The route helper reuses bluetape4k Ktor core request-parameter and error
 * response helpers while leaving JSON plugin installation to the application.
 *
 * ```kotlin
 * routing {
 *     bluetape4kCaptchaRoutes()
 * }
 * ```
 */
class CaptchaKtorRoutesConfig(
    val routePath: String = DEFAULT_CAPTCHA_ROUTE,
    val generator: CaptchaGenerator = captchaGenerator(),
    val verificationService: CaptchaVerificationService = CaptchaVerificationService(),
    val idFactory: () -> CaptchaChallengeId = { CaptchaChallengeId(Base58.randomString(DEFAULT_ID_LENGTH)) },
) {

    init {
        routePath.requireNotBlank("routePath")
    }
}

@Serializable
data class CaptchaIssueResponse(
    val id: String,
    val imageBase64: String,
    val contentType: String,
    val expiresAt: String,
)

@Serializable
data class CaptchaVerifyRequest(
    val answer: String,
)

@Serializable
data class CaptchaVerifyResponse(
    val id: String,
    val status: CaptchaVerificationStatus,
    val verified: Boolean,
    val expiredAt: String? = null,
    val checkedAt: String? = null,
)

@Serializable
enum class CaptchaVerificationStatus {
    SUCCESS,
    WRONG_ANSWER,
    EXPIRED,
    NOT_FOUND,
}

/**
 * Source-compatible alias for the shared bluetape4k Ktor error payload.
 */
@Deprecated(
    message = "Use io.bluetape4k.ktor.core.ApiErrorResponse.",
    replaceWith = ReplaceWith("ApiErrorResponse", "io.bluetape4k.ktor.core.ApiErrorResponse")
)
typealias CaptchaRouteErrorResponse = ApiErrorResponse

/**
 * Registers CAPTCHA issue and one-shot verification endpoints.
 *
 * Routes:
 * - `GET {routePath}?length=6` issues a challenge and returns base64 PNG bytes.
 * - `POST {routePath}/{id}/verify` consumes the challenge and verifies the submitted answer.
 */
fun Route.bluetape4kCaptchaRoutes(
    config: CaptchaKtorRoutesConfig = CaptchaKtorRoutesConfig(),
) {
    route(config.routePath) {
        get {
            call.respondOrBadRequest {
                val length = call.optionalLengthQueryParameter()
                val challenge = if (length == null) {
                    config.generator.generate()
                } else {
                    config.generator.generate(length)
                }
                val issued = config.verificationService.issue(config.idFactory(), challenge)
                val imageBytes = withContext(Dispatchers.IO) {
                    challenge.image.forWriter(PngWriter.MaxCompression).bytes()
                }

                call.respond(
                    CaptchaIssueResponse(
                        id = issued.id.value,
                        imageBase64 = Base64.getEncoder().encodeToString(imageBytes),
                        contentType = DEFAULT_CONTENT_TYPE,
                        expiresAt = issued.expiresAt.toString(),
                    )
                )
            }
        }

        post("/{id}/verify") {
            call.respondOrBadRequest {
                val id = CaptchaChallengeId(call.requiredPathParameter("id"))
                val request = call.receive<CaptchaVerifyRequest>()
                request.answer.requireNotBlank("answer")

                val result = config.verificationService.verify(id, request.answer)
                val (statusCode, response) = result.toHttpResponse()
                call.respond(statusCode, response)
            }
        }
    }
}

private suspend fun ApplicationCall.respondOrBadRequest(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: IllegalArgumentException) {
        respondApiError(
            status = HttpStatusCode.BadRequest,
            error = "bad_request",
            message = e.message ?: "Invalid CAPTCHA request."
        )
    }
}

private fun ApplicationCall.optionalLengthQueryParameter(): Int? {
    val length = intQueryParameter("length") ?: return null
    length.requirePositiveCaptchaLength()
    return length
}

private fun Int.requirePositiveCaptchaLength(): Int =
    apply {
        require(this > 0) { "Query parameter 'length' must be positive." }
    }

private fun CaptchaVerificationResult.toHttpResponse(): Pair<HttpStatusCode, CaptchaVerifyResponse> =
    when (this) {
        is CaptchaVerificationResult.Success ->
            HttpStatusCode.OK to response(CaptchaVerificationStatus.SUCCESS)

        is CaptchaVerificationResult.WrongAnswer ->
            HttpStatusCode.BadRequest to response(CaptchaVerificationStatus.WRONG_ANSWER)

        is CaptchaVerificationResult.Expired ->
            HttpStatusCode.Gone to response(CaptchaVerificationStatus.EXPIRED, expiredAt, checkedAt)

        is CaptchaVerificationResult.NotFound ->
            HttpStatusCode.NotFound to response(CaptchaVerificationStatus.NOT_FOUND)
    }

private fun CaptchaVerificationResult.response(
    status: CaptchaVerificationStatus,
    expiredAt: Instant? = null,
    checkedAt: Instant? = null,
): CaptchaVerifyResponse =
    CaptchaVerifyResponse(
        id = id.value,
        status = status,
        verified = verified,
        expiredAt = expiredAt?.toString(),
        checkedAt = checkedAt?.toString(),
    )
