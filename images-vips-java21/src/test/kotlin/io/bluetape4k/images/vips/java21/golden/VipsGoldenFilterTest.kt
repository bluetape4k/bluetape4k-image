package io.bluetape4k.images.vips.java21.golden

import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.java21.AbstractJVipsTest
import io.bluetape4k.images.vips.java21.vipsImageOf
import io.bluetape4k.images.vips.testfixtures.VipsGoldenAssert
import io.bluetape4k.images.vips.testfixtures.VipsTestFixtures
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE

/**
 * vips 인코딩 포맷 및 thumbnail + 인코딩 조합의 골든 이미지 비교 테스트.
 *
 * 골든 이미지가 없으면 테스트를 실패 처리합니다.
 * 갱신 모드는 Java25 FFM 테스트에서만 허용합니다.
 * Java21 JNI test task는 Gradle fail-fast guard로 canonical fixture 덮어쓰기를 거부합니다.
 */
class VipsGoldenFilterTest : AbstractJVipsTest() {

    companion object : KLogging() {
        /** CI와 macOS에서 확인된 libvips/native codec 버전별 손실 thumbnail 출력 편차를 제한하는 경계입니다. */
        private const val THUMBNAIL_JPEG_TOLERANCE = 6
    }

    // ─── 비교 테스트 ───────────────────────────────────────────────────────────

    /**
     * PNG 원본 이미지를 JPEG로 인코딩한 결과를 골든 이미지와 비교합니다.
     * PNG → JPEG 변환 시 JPEG 품질(85)이 일관되게 적용되는지 검증합니다.
     */
    @Test
    fun `PNG 원본을 JPEG 인코딩한 결과가 골든 이미지와 일치한다`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        vipsImageOf(bytes).use { img ->
            val resultBytes = img.toBytes(VipsImageFormat.JPEG, VipsEncodeOptions.Default)
            VipsGoldenAssert.assertSimilarToGolden(resultBytes, "vips-encode-jpeg")
        }
    }

    /**
     * thumbnail(128) 후 JPEG 인코딩 결과를 골든 이미지와 비교합니다.
     */
    @Test
    fun `thumbnail 후 JPEG 인코딩 결과가 골든 이미지와 일치한다`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.thumbnail(128).use { thumb ->
                val resultBytes = thumb.toBytes(VipsImageFormat.JPEG, VipsEncodeOptions.Default)
                VipsGoldenAssert.assertSimilarToGolden(
                    resultBytes,
                    "vips-thumbnail-jpeg",
                    tolerance = THUMBNAIL_JPEG_TOLERANCE,
                )
            }
        }
    }

    /**
     * resize(320, 240) 후 WebP 인코딩 결과를 골든 이미지와 비교합니다.
     */
    @Test
    fun `resize 후 WebP 인코딩 결과가 골든 이미지와 일치한다`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.resize(320, 240).use { resized ->
                val resultBytes = resized.toBytes(VipsImageFormat.WEBP, VipsEncodeOptions.Default)
                VipsGoldenAssert.assertSimilarToGolden(resultBytes, "vips-resize-webp")
            }
        }
    }

    // ─── 갱신 모드 (Java 25 전용) ──────────────────────────────────────────────

    @Test
    @EnabledForJreRange(min = JRE.JAVA_25)
    fun `골든 이미지 갱신 - encode jpeg`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        vipsImageOf(bytes).use { img ->
            val resultBytes = img.toBytes(VipsImageFormat.JPEG, VipsEncodeOptions.Default)
            VipsGoldenAssert.assertSimilarToGolden(resultBytes, "vips-encode-jpeg")
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_25)
    fun `골든 이미지 갱신 - thumbnail jpeg`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.thumbnail(128).use { thumb ->
                val resultBytes = thumb.toBytes(VipsImageFormat.JPEG, VipsEncodeOptions.Default)
                VipsGoldenAssert.assertSimilarToGolden(
                    resultBytes,
                    "vips-thumbnail-jpeg",
                    tolerance = THUMBNAIL_JPEG_TOLERANCE,
                )
            }
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_25)
    fun `골든 이미지 갱신 - resize webp`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.resize(320, 240).use { resized ->
                val resultBytes = resized.toBytes(VipsImageFormat.WEBP, VipsEncodeOptions.Default)
                VipsGoldenAssert.assertSimilarToGolden(resultBytes, "vips-resize-webp")
            }
        }
    }
}
