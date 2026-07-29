package io.bluetape4k.images.spring.cdn

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.autoconfigure.CdnProperties
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest
import java.io.Serializable
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Arrays
import java.util.Base64

/**
 * [CdnReadSigner]만 구현하는 CloudFront signed-URL generator입니다.
 *
 * ## 동작 / 계약
 * - [CloudFrontUtilities.getSignedUrlWithCannedPolicy]를 통해 AWS CloudFront canned policy로 URL에 서명합니다.
 * - constructor argument인 [properties]에는 blank가 아닌 bare-hostname `distributionDomain`(`http(s)` prefix와
 *   path segment 없음), blank가 아닌 `keyPairId`, 양수 `maxExpiry`, 그리고 `privateKeyPath`(권장) 또는
 *   `privateKeyPem`(비권장) 중 정확히 하나가 있어야 합니다.
 * - `privateKeyPath`와 `privateKeyPem`이 모두 제공되면 construction은 [IllegalArgumentException]으로 실패합니다.
 * - PEM private key는 construction 시점에 한 번 parsing합니다. [CdnProperties.CloudFront.privateKeyPath]에서
 *   load한 경우 parsing 후 읽은 byte buffer를 zero-fill합니다. [CdnProperties.CloudFront.privateKeyPem]로 제공하면
 *   값이 JVM heap에 남아 안정적으로 zero-fill할 수 없으므로 inline 사용을 줄이도록 WARN log를 남깁니다.
 * - [signGet]은 `expiresIn`이 양수이고 [CdnProperties.CloudFront.maxExpiry]보다 크지 않은지 검증합니다. 내부 SDK가
 *   `SecureRandom`을 사용하고 entropy가 낮은 container host에서 block될 수 있으므로 signing call은 [Dispatchers.IO]에서 실행합니다.
 * - [signGet]은 broad catch보다 먼저 [CancellationException]을 다시 던집니다.
 *
 * 의도적으로 read-only입니다. CloudFront는 PUT URL에 서명할 수 없으므로 이 class는 [CdnWriteSigner]를 구현하지 않습니다.
 */
class CloudFrontUrlSigner(properties: CdnProperties.CloudFront) : CdnReadSigner, Serializable {

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L

        private const val PEM_BEGIN = "-----BEGIN"
        private const val PEM_END = "-----END"

        /** [CloudFrontUtilities]는 stateless/reusable로 문서화되어 있으므로 singleton으로 cache합니다. */
        private val UTILITIES: CloudFrontUtilities = CloudFrontUtilities.create()
    }

    private val distributionDomain: String
    private val keyPairId: String
    private val maxExpiry: Duration
    private val privateKey: PrivateKey

    init {
        val rawDomain = requireNotNull(properties.distributionDomain) {
            "distributionDomain is required"
        }
        rawDomain.requireNotBlank("distributionDomain")
        require(!rawDomain.startsWith("http") && !rawDomain.contains("/")) {
            "distributionDomain must be a bare hostname (e.g., d123.cloudfront.net), not a full URL"
        }
        distributionDomain = rawDomain

        val rawKeyPairId = requireNotNull(properties.keyPairId) { "keyPairId is required" }
        rawKeyPairId.requireNotBlank("keyPairId")
        keyPairId = rawKeyPairId

        require(properties.maxExpiry.isPositive() && !properties.maxExpiry.isZero) {
            "maxExpiry must be positive: ${properties.maxExpiry}"
        }
        maxExpiry = properties.maxExpiry

        val pemPath = properties.privateKeyPath
        val pemInline = properties.privateKeyPem
        require(!(pemPath != null && pemInline != null)) {
            "Specify either private-key-path or private-key-pem, not both."
        }
        require(pemPath != null || pemInline != null) {
            "Either private-key-path or private-key-pem must be provided."
        }

        privateKey = if (pemPath != null) {
            loadPrivateKeyFromPath(pemPath)
        } else {
            // 위 검증으로 pemInline이 non-null임이 보장됩니다.
            val pem = requireNotNull(pemInline) { "private-key-pem is required" }
            log.warn {
                "Loading CloudFront private key from inline 'private-key-pem'. " +
                    "Prefer 'private-key-path' so the key bytes can be zeroed out after parsing."
            }
            try {
                parsePkcs8PrivateKey(pem)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ImageStorageException.ValidationException(
                    message = "Failed to parse CloudFront private-key-pem (inline value redacted)",
                    cause = e,
                )
            }
        }
    }

    override suspend fun signGet(key: ImageObjectKey, expiresIn: Duration): URI {
        require(expiresIn.isPositive() && !expiresIn.isZero) {
            "expiresIn must be positive: $expiresIn"
        }
        require(expiresIn <= maxExpiry) {
            "expiresIn ($expiresIn) must be <= maxExpiry ($maxExpiry)"
        }

        return withContext(Dispatchers.IO) {
            try {
                val resourceUrl = "https://$distributionDomain/${key.fullKey}"
                val expirationDate = Instant.now().plus(expiresIn)
                val request = CannedSignerRequest.builder()
                    .resourceUrl(resourceUrl)
                    .privateKey(privateKey)
                    .keyPairId(keyPairId)
                    .expirationDate(expirationDate)
                    .build()
                val signed = UTILITIES.getSignedUrlWithCannedPolicy(request)
                try {
                    URI(signed.url())
                } catch (e: URISyntaxException) {
                    throw ImageStorageException.TransientException(
                        key = key,
                        message = "Failed to parse signed CloudFront URL: ${key.fullKey}",
                        cause = e,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageStorageException) {
                throw e
            } catch (e: Throwable) {
                throw ImageStorageException.TransientException(
                    key = key,
                    message = "Failed to sign CloudFront URL: ${key.fullKey}",
                    cause = e,
                )
            }
        }
    }

    /**
     * [path]의 file을 읽어 PKCS#8 PEM private key로 parsing한 뒤, PEM content가 JVM heap에 남지 않도록 byte
     * buffer를 zero-fill합니다.
     */
    private fun loadPrivateKeyFromPath(path: String): PrivateKey {
        val keyPath: Path = Path.of(path)
        val bytes = try {
            Files.readAllBytes(keyPath)
        } catch (e: Exception) {
            throw ImageStorageException.ValidationException(
                message = "Failed to read CloudFront private-key-path: $path",
                cause = e,
            )
        }
        try {
            val pem = String(bytes, Charsets.US_ASCII)
            return parsePkcs8PrivateKey(pem)
        } catch (e: Exception) {
            throw ImageStorageException.ValidationException(
                message = "Failed to parse CloudFront private key at: $path",
                cause = e,
            )
        } finally {
            Arrays.fill(bytes, 0)
        }
    }

    /**
     * PKCS#8 PEM-encoded RSA private key를 [PrivateKey]로 parsing합니다.
     *
     * `-----BEGIN ... -----` / `-----END ... -----` marker를 제거하고 body를 Base64 decode합니다.
     * PEM marker가 없으면 exception을 던집니다.
     */
    private fun parsePkcs8PrivateKey(pem: String): PrivateKey {
        val beginIdx = pem.indexOf(PEM_BEGIN)
        val endIdx = pem.indexOf(PEM_END)
        require(beginIdx >= 0 && endIdx > beginIdx) {
            "PEM content does not contain BEGIN/END markers"
        }
        // BEGIN marker line 다음으로 이동합니다.
        val afterBegin = pem.indexOf('\n', beginIdx)
        require(afterBegin in 0 until endIdx) { "Malformed PEM header" }
        val base64Body = pem.substring(afterBegin + 1, endIdx)
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
        val der = Base64.getDecoder().decode(base64Body)
        try {
            val spec = PKCS8EncodedKeySpec(der)
            return KeyFactory.getInstance("RSA").generatePrivate(spec)
        } finally {
            Arrays.fill(der, 0)
        }
    }
}
