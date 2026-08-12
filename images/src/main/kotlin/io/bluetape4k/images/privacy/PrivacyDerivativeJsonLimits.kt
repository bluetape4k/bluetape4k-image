package io.bluetape4k.images.privacy

/**
 * privacy snapshot JSON decode에 적용하는 caller-side 제한입니다.
 *
 * 모든 값은 고정된 hard cap 이하만 허용합니다. 큰 payload는 String 편의 API보다
 * `PrivacyDerivativeJackson.decodePayload(InputStream)`을 사용해야 합니다.
 */
data class PrivacyDerivativeJsonLimits(
    val maxJsonBytes: Int = DEFAULT_MAX_JSON_BYTES,
    val maxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    val maxRedactions: Int = DEFAULT_MAX_REDACTIONS,
    val maxActions: Int = DEFAULT_MAX_ACTIONS,
    val maxFailures: Int = DEFAULT_MAX_FAILURES,
    val maxMetadataEntries: Int = DEFAULT_MAX_METADATA_ENTRIES,
    val maxSourceIdLength: Int = DEFAULT_MAX_SOURCE_ID_LENGTH,
    val maxCodeLength: Int = DEFAULT_MAX_CODE_LENGTH,
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    val maxPixels: Long = DEFAULT_MAX_PIXELS,
    val maxSide: Int = DEFAULT_MAX_SIDE,
    val maxThumbnailSide: Int = DEFAULT_MAX_THUMBNAIL_SIDE,
) {
    init {
        require(maxJsonBytes in 1..DEFAULT_MAX_JSON_BYTES)
        require(maxPayloadBytes in 1..DEFAULT_MAX_PAYLOAD_BYTES)
        require(maxRedactions in 1..DEFAULT_MAX_REDACTIONS)
        require(maxActions in 1..DEFAULT_MAX_ACTIONS)
        require(maxFailures in 1..DEFAULT_MAX_FAILURES)
        require(maxMetadataEntries in 1..DEFAULT_MAX_METADATA_ENTRIES)
        require(maxSourceIdLength in 1..DEFAULT_MAX_SOURCE_ID_LENGTH)
        require(maxCodeLength in 1..DEFAULT_MAX_CODE_LENGTH)
        require(maxDepth in 1..DEFAULT_MAX_DEPTH)
        require(maxPixels in 1..DEFAULT_MAX_PIXELS)
        require(maxSide in 1..DEFAULT_MAX_SIDE)
        require(maxThumbnailSide in 1..DEFAULT_MAX_THUMBNAIL_SIDE)
    }

    companion object {
        const val DEFAULT_MAX_JSON_BYTES: Int = 96 * 1024 * 1024
        const val DEFAULT_MAX_PAYLOAD_BYTES: Int = 64 * 1024 * 1024
        const val DEFAULT_MAX_REDACTIONS: Int = 1_024
        const val DEFAULT_MAX_ACTIONS: Int = 256
        const val DEFAULT_MAX_FAILURES: Int = 256
        const val DEFAULT_MAX_METADATA_ENTRIES: Int = 256
        const val DEFAULT_MAX_SOURCE_ID_LENGTH: Int = 4 * 1024
        const val DEFAULT_MAX_CODE_LENGTH: Int = 4 * 1024
        const val DEFAULT_MAX_DEPTH: Int = 32
        const val DEFAULT_MAX_PIXELS: Long = 100_000_000L
        const val DEFAULT_MAX_SIDE: Int = 65_536
        const val DEFAULT_MAX_THUMBNAIL_SIDE: Int = 16_384
    }
}
