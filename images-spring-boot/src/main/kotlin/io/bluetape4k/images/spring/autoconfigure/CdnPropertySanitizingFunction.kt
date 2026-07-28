package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.actuate.endpoint.SanitizableData
import org.springframework.boot.actuate.endpoint.SanitizingFunction
import java.io.Serializable

/**
 * Actuator endpoint에서 CloudFront private-key material을 redaction합니다.
 *
 * ## 동작 / 계약
 * - property key가 CloudFront private-key material을 참조하면 value를 [SanitizableData.SANITIZED_VALUE]로 바꾼
 *   [SanitizableData]를 반환합니다.
 * - lower-case key에 `private-key-pem`, `private-key-path`, `privatekey` 중 하나가 포함되면 matching합니다.
 *   `/actuator/configprops`와 `/actuator/env`가 내보내는 kebab-case(`...cloudfront.private-key-pem`)와
 *   camelCase(`...cloudfront.privateKeyPem`) binding을 모두 방어하기 위해서입니다.
 * - 나머지 entry는 그대로 반환합니다.
 *
 * `spring-boot-actuator`가 classpath에 있을 때 `ImagesCdnAutoConfiguration`이 `@Bean`으로 등록합니다.
 * 이 bean은 [CdnProperties.CloudFront] 자체 `toString()` redaction 위에 추가 방어층으로 동작합니다.
 */
class CdnPropertySanitizingFunction : SanitizingFunction, Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L

        /** 항상 redaction해야 하는 lower-case key fragment입니다. */
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
