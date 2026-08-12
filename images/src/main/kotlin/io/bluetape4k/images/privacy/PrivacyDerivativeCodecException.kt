package io.bluetape4k.images.privacy

/** codec 입력을 호출자가 안정적으로 분류할 수 있는 reason code입니다. */
enum class PrivacyDerivativeCodecReason {
    MALFORMED_JSON,
    UNSUPPORTED_SCHEMA_VERSION,
    TYPE_MISMATCH,
    UNKNOWN_FIELD,
    NULL_VALUE,
    LIMIT_EXCEEDED,
    INVALID_VALUE,
    TRAILING_DATA,
    IO_FAILURE,
}

/** 내부 Jackson 예외·경로·stack trace를 노출하지 않는 공개 codec 예외입니다. */
class PrivacyDerivativeCodecException(
    val reason: PrivacyDerivativeCodecReason,
    message: String = reason.name,
) : IllegalArgumentException(message)
