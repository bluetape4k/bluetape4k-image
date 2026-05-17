package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.retries.DefaultRetryStrategy
import software.amazon.awssdk.retries.StandardRetryStrategy

/**
 * Converts [ImageStorageProperties.S3] timeout/retry knobs to AWS SDK override configuration.
 *
 * ## Behavior / Contract
 * - [toClientOverrideConfig] wires `callTimeout`, `attemptTimeout`, and a Standard retry strategy
 *   derived from [ImageStorageProperties.S3.maxRetries] into a [ClientOverrideConfiguration].
 * - [toStandardRetryStrategy] returns a [StandardRetryStrategy] built from
 *   [DefaultRetryStrategy.standardStrategyBuilder] with `maxAttempts = maxRetries + 1`. The total
 *   attempts (initial + retries) follows AWS SDK convention.
 * - These configurations are intended to be applied at SDK client construction time. The
 *   `S3Operations` API in `bluetape4k-aws-spring-boot` does not currently expose a per-request
 *   override hook, so the helper is wired by direct-client consumers and acts as documentation for
 *   the storage-side timeout/retry contract.
 */
internal fun ImageStorageProperties.S3.toClientOverrideConfig(): ClientOverrideConfiguration =
    ClientOverrideConfiguration.builder()
        .apiCallTimeout(callTimeout)
        .apiCallAttemptTimeout(attemptTimeout)
        .retryStrategy(toStandardRetryStrategy())
        .build()

/**
 * Builds a Standard [software.amazon.awssdk.retries.StandardRetryStrategy] with `maxAttempts`
 * equal to `maxRetries + 1` (initial attempt + retries).
 */
internal fun ImageStorageProperties.S3.toStandardRetryStrategy(): StandardRetryStrategy =
    DefaultRetryStrategy.standardStrategyBuilder()
        .maxAttempts(maxRetries + 1)
        .build()
