package io.bluetape4k.images

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

class ImageFormatExtensionsTest {

    companion object: KLogging()

    @Test
    fun `isWritableByImageIO - 기본 포맷은 true 반환`() {
        ImageFormat.GIF.isWritableByImageIO().shouldBeTrue()
        ImageFormat.JPG.isWritableByImageIO().shouldBeTrue()
        ImageFormat.PNG.isWritableByImageIO().shouldBeTrue()
        ImageFormat.WEBP.isWritableByImageIO().shouldBeTrue()
        ImageFormat.TIFF.isWritableByImageIO().shouldBeTrue()
    }

    @Test
    fun `isWritableByImageIO - incubating 포맷은 false 반환`() {
        ImageFormat.SVG.isWritableByImageIO().shouldBeFalse()
        ImageFormat.AVIF.isWritableByImageIO().shouldBeFalse()
        ImageFormat.HEIC.isWritableByImageIO().shouldBeFalse()
    }

    @Test
    fun `requireWritable - 쓰기 가능 포맷은 예외 없음`() {
        ImageFormat.PNG.requireWritable()
        ImageFormat.TIFF.requireWritable()
    }

    @Test
    fun `requireWritable - SVG 포맷은 예외 발생`() {
        assertFailsWith<IllegalArgumentException> {
            ImageFormat.SVG.requireWritable()
        }
    }

    @Test
    fun `requireWritable - AVIF 포맷은 예외 발생`() {
        assertFailsWith<IllegalArgumentException> {
            ImageFormat.AVIF.requireWritable()
        }
    }

    @Test
    fun `parse - TIFF 파싱`() {
        val result = ImageFormat.parse("TIFF")
        result shouldBeEqualTo ImageFormat.TIFF
    }

    @Test
    fun `parse - SVG 파싱`() {
        val result = ImageFormat.parse("svg")
        result shouldBeEqualTo ImageFormat.SVG
    }

    @Test
    fun `parse - AVIF 파싱`() {
        val result = ImageFormat.parse("avif")
        result shouldBeEqualTo ImageFormat.AVIF
    }

    @Test
    fun `parse - HEIC 파싱`() {
        val result = ImageFormat.parse("heic")
        result shouldBeEqualTo ImageFormat.HEIC
    }

    @Test
    fun `parse - 빈 문자열 null 반환`() {
        ImageFormat.parse("").shouldBeNull()
        ImageFormat.parse("   ").shouldBeNull()
        ImageFormat.parse("\t").shouldBeNull()
    }
}
