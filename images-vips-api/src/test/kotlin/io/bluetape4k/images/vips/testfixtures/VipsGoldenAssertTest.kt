package io.bluetape4k.images.vips.testfixtures

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test

class VipsGoldenAssertTest {

    @Test
    fun `missing golden resource fails closed instead of being skipped`() {
        val failure = assertFailsWith<AssertionError> {
            VipsGoldenAssert.assertSimilarToGolden(
                actualBytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG),
                key = "missing-golden-resource",
            )
        }

        failure.message?.contains("missing-golden-resource") shouldBeEqualTo true
        failure.message?.contains("golden/vips/missing-golden-resource.png") shouldBeEqualTo true
    }

    @Test
    fun `canonical golden resources are complete valid pngs with expected dimensions`() {
        canonicalGoldens.forEach { (key, expected) ->
            val resourcePath = "/golden/vips/$key.png"
            val bytes = javaClass.getResourceAsStream(resourcePath)?.use { it.readBytes() }
                ?: error("Missing canonical golden resource: $resourcePath")

            bytes.isNotEmpty().shouldBeTrue()
            bytes.copyOf(PNG_SIGNATURE.size).toList() shouldBeEqualTo PNG_SIGNATURE.toList()

            val image = ImageIO.read(ByteArrayInputStream(bytes))
                ?: error("Canonical golden resource is not a decodable PNG: $resourcePath")
            image.width shouldBeEqualTo expected.width
            image.height shouldBeEqualTo expected.height

            val digest = sha256(bytes)
            digest shouldBeEqualTo expected.sha256
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private data class GoldenExpectation(
        val width: Int,
        val height: Int,
        val sha256: String,
    )

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )

        val canonicalGoldens = mapOf(
            "vips-resize-320x240" to
                GoldenExpectation(320, 240, "dba081ff178517e57403eecc13af56382f94e3c6161db657e0e39b0eaf62338c"),
            "vips-thumbnail-128" to
                GoldenExpectation(128, 96, "bd9fd8b96bb6e778fd6d7cbc1c8689a25e95aadac0b93a13329c2c39a4ff3412"),
            "vips-resize-fit-400x300" to
                GoldenExpectation(400, 300, "dc53146492fff7bfa9674fd3d3b9ed62aa86f4aba27b47feb6a24b71d06fd670"),
            "vips-encode-jpeg" to
                GoldenExpectation(640, 480, "044ad00867fd852ca84c6b8e21bc9276feaeead3bf92604b0e26234675815509"),
            "vips-thumbnail-jpeg" to
                GoldenExpectation(128, 96, "d1e20fe33d869ae15fa4e8cf911377b17185b8896ef1f6248c4747572f24be6b"),
            "vips-resize-webp" to
                GoldenExpectation(320, 240, "5eff65bd7f3d6ff4728cc0c2e5579ceaadc39003b8caead7554d9b326dc029bd"),
        )
    }
}
