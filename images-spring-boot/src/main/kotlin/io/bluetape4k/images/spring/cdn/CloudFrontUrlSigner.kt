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
 * CloudFront signed-URL generator implementing [CdnReadSigner] only.
 *
 * ## Behavior / Contract
 * - Signs URLs using AWS CloudFront canned policy via
 *   [CloudFrontUtilities.getSignedUrlWithCannedPolicy].
 * - Constructor argument [properties] must contain a non-blank, bare-hostname
 *   `distributionDomain` (no `http(s)` prefix, no path segment), a non-blank `keyPairId`, a positive
 *   `maxExpiry`, and exactly one of `privateKeyPath` (preferred) or `privateKeyPem` (discouraged).
 * - When both `privateKeyPath` and `privateKeyPem` are supplied, construction fails with
 *   [IllegalArgumentException].
 * - The PEM private key is parsed once at construction time. When loaded from
 *   [CdnProperties.CloudFront.privateKeyPath], the read byte buffer is zero-filled after parsing.
 *   When supplied through [CdnProperties.CloudFront.privateKeyPem], the value lives on the JVM
 *   heap and cannot be reliably zeroed — a WARN is logged to discourage inline usage.
 * - [signGet] validates that `expiresIn` is positive and not greater than
 *   [CdnProperties.CloudFront.maxExpiry]. The signing call runs on [Dispatchers.IO] because the
 *   underlying SDK uses `SecureRandom`, which can block on container hosts with low entropy.
 * - [signGet] rethrows [CancellationException] before any broad catch.
 *
 * Read-only by design: CloudFront cannot sign PUT URLs, so this class deliberately does **not**
 * implement [CdnWriteSigner].
 */
class CloudFrontUrlSigner(properties: CdnProperties.CloudFront) : CdnReadSigner, Serializable {

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L

        private const val PEM_BEGIN = "-----BEGIN"
        private const val PEM_END = "-----END"

        /** Cached singleton — [CloudFrontUtilities] is documented as stateless and reusable. */
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
            // pemInline is guaranteed non-null by the check above.
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
     * Reads the file at [path], parses it as a PKCS#8 PEM private key, then zero-fills the byte
     * buffer to remove the PEM content from the JVM heap.
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
     * Parses a PKCS#8 PEM-encoded RSA private key into a [PrivateKey].
     *
     * Strips the `-----BEGIN ... -----` / `-----END ... -----` markers and Base64-decodes the body.
     * Throws if no PEM markers are present.
     */
    private fun parsePkcs8PrivateKey(pem: String): PrivateKey {
        val beginIdx = pem.indexOf(PEM_BEGIN)
        val endIdx = pem.indexOf(PEM_END)
        require(beginIdx >= 0 && endIdx > beginIdx) {
            "PEM content does not contain BEGIN/END markers"
        }
        // Skip past the BEGIN marker line.
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
