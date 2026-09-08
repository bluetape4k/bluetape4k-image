package io.bluetape4k.images.vips.testfixtures

import io.bluetape4k.images.vips.VipsInitializationException

/**
 * JVips와 vips-ffm 런타임이 공유하는 초기화 대기 실패 계약입니다.
 *
 * 이 fixture는 공개 런타임 프레임워크를 추가하지 않고, 두 backend의
 * timeout/interrupt 결과가 같은 사용자-facing 의미를 유지하도록 검증 표면만 제공합니다.
 */
object VipsInitializationWaitContract {

    /** 운영 환경에서 경쟁 대기자가 반환해야 하는 최대 시간(초)입니다. */
    const val DEFAULT_TIMEOUT_SECONDS: Long = 60

    /** timeout 예외가 공통적으로 포함해야 하는 식별 문구입니다. */
    const val TIMEOUT_MARKER = "timed out"

    /** interrupt 예외가 공통적으로 포함해야 하는 식별 문구입니다. */
    const val INTERRUPTED_MARKER = "interrupted"

    /** timeout 정책의 공통 메시지 계약을 검증합니다. */
    fun assertTimedOut(error: VipsInitializationException) {
        val message = requireNotNull(error.message) { "timeout error must have a message" }
        check(TIMEOUT_MARKER in message) { "timeout error must contain '$TIMEOUT_MARKER': $message" }
        check("$DEFAULT_TIMEOUT_SECONDS seconds" in message) {
            "timeout error must document the ${DEFAULT_TIMEOUT_SECONDS}s cap: $message"
        }
    }

    /** interrupt 정책의 공통 메시지 계약을 검증합니다. */
    fun assertInterrupted(error: VipsInitializationException) {
        val message = requireNotNull(error.message) { "interrupt error must have a message" }
        check(INTERRUPTED_MARKER in message) {
            "interrupt error must contain '$INTERRUPTED_MARKER': $message"
        }
    }
}
