package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.retries.DefaultRetryStrategy
import software.amazon.awssdk.retries.StandardRetryStrategy

/**
 * [ImageStorageProperties.S3]의 timeout/retry knob을 AWS SDK override configuration으로 변환합니다.
 *
 * ## 동작 / 계약
 * - [toClientOverrideConfig]는 `callTimeout`, `attemptTimeout`, 그리고 [ImageStorageProperties.S3.maxRetries]에서
 *   파생한 Standard retry strategy를 [ClientOverrideConfiguration]에 연결합니다.
 * - [toStandardRetryStrategy]는 [DefaultRetryStrategy.standardStrategyBuilder]로 만든 [StandardRetryStrategy]를
 *   반환하며 `maxAttempts = maxRetries + 1`을 사용합니다. 총 attempt 수(initial + retries)는 AWS SDK 관례를 따릅니다.
 * - 이 configuration은 SDK client 생성 시점에 적용하는 용도입니다. `bluetape4k-aws-spring-boot`의
 *   `S3Operations` API는 현재 per-request override hook을 노출하지 않으므로, 이 helper는 direct-client consumer가
 *   연결하고 storage 측 timeout/retry contract를 문서화하는 역할도 합니다.
 */
internal fun ImageStorageProperties.S3.toClientOverrideConfig(): ClientOverrideConfiguration =
    ClientOverrideConfiguration.builder()
        .apiCallTimeout(callTimeout)
        .apiCallAttemptTimeout(attemptTimeout)
        .retryStrategy(toStandardRetryStrategy())
        .build()

/**
 * `maxAttempts = maxRetries + 1`(initial attempt + retries)인 Standard
 * [software.amazon.awssdk.retries.StandardRetryStrategy]를 생성합니다.
 */
internal fun ImageStorageProperties.S3.toStandardRetryStrategy(): StandardRetryStrategy =
    DefaultRetryStrategy.standardStrategyBuilder()
        .maxAttempts(maxRetries + 1)
        .build()
