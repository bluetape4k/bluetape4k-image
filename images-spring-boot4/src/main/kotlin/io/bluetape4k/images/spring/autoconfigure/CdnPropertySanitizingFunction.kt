package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.actuate.endpoint.SanitizableData
import org.springframework.boot.actuate.endpoint.SanitizingFunction
import java.io.Serializable

/**
 * Redacts CloudFront private-key material in Actuator endpoints.
 *
 * ## Behavior / Contract
 * - Returns a [SanitizableData] whose value is replaced with [SanitizableData.SANITIZED_VALUE]
 *   when the property key references CloudFront private-key material.
 * - Matches any key whose lower-case form contains `private-key-pem`, `private-key-path`, or
 *   `privatekey` to defend against both kebab-case (`...cloudfront.private-key-pem`) and
 *   camelCase (`...cloudfront.privateKeyPem`) bindings emitted by `/actuator/configprops` and
 *   `/actuator/env`.
 * - All other entries are returned unchanged.
 *
 * Registered as a `@Bean` by `ImagesCdnAutoConfiguration` when `spring-boot-actuator` is on the
 * classpath. The bean acts as an extra defensive layer on top of [CdnProperties.CloudFront]'s
 * own `toString()` redaction.
 */
class CdnPropertySanitizingFunction : SanitizingFunction, Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L

        /** Lower-case key fragments that should always be redacted. */
        private val SENSITIVE_FRAGMENTS: List<String> = listOf(
            "private-key-pem",
            "private-key-path",
            "privatekey",
        )
    }

    override fun apply(data: SanitizableData): SanitizableData {
        val lowerKey = data.lowerCaseKey
        return if (SENSITIVE_FRAGMENTS.any { it in lowerKey }) {
            data.withSanitizedValue()
        } else {
            data
        }
    }
}
