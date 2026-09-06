package io.bluetape4k.images.splitter

import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.images.ImageFormat
import io.bluetape4k.images.coroutines.SuspendJpegWriter
import io.bluetape4k.io.toInputStream
import io.bluetape4k.io.writeSuspending
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.junit5.tempfolder.TempFolder
import io.bluetape4k.junit5.tempfolder.TempFolderTest
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import java.util.stream.Stream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeLessOrEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import javax.imageio.ImageIO

@TempFolderTest
class ImageSplitterTest: AbstractImageTest() {

    companion object: KLoggingChannel() {
        private const val AQUA_JPG = "images/splitter/aqua.jpg"
        private const val EVERLAND_JPG = "images/splitter/everland.jpg"

        @JvmStatic
        fun invalidImageInputs(): Stream<Arguments> = Stream.of(
            Arguments.of("empty", byteArrayOf()),
            Arguments.of("unknown", "not an image".toByteArray()),
            Arguments.of(
                "truncated png",
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            ),
        )
    }

    private val splitter = ImageSplitter(1024)

    @Test
    fun `split 0 height`() = runTest {
        getImage(AQUA_JPG).use { input ->
            assertFailsWith<IllegalArgumentException> {
                splitter.split(input, ImageFormat.JPG, 0)
            }
        }
    }

    @ParameterizedTest(name = "invalid input {0}")
    @MethodSource("invalidImageInputs")
    fun `split rejects invalid truncated and unknown inputs`(description: String, input: ByteArray) = runTest {
        val splitError = assertFailsWith<IllegalArgumentException> {
            splitter.split(input.inputStream()).toList()
        }
        splitError.message shouldContain "지원하지 않는 이미지 포맷이거나 손상된 스트림"

        val compressError = assertFailsWith<IllegalArgumentException> {
            splitter.splitAndCompress(input.inputStream()).toList()
        }
        compressError.message shouldContain "지원하지 않는 이미지 포맷이거나 손상된 스트림"
    }

    @ParameterizedTest(name = "unsupported output {0}")
    @EnumSource(value = ImageFormat::class, names = ["AVIF", "HEIC", "SVG"])
    fun `split and splitAndCompress reject formats without ImageIO writers`(format: ImageFormat) = runTest {
        getImage(AQUA_JPG).use { input ->
            val error = assertFailsWith<IllegalArgumentException> {
                splitter.split(input, format).toList()
            }
            error.message shouldContain "전용 writer를 사용하세요"
        }

        getImage(AQUA_JPG).use { input ->
            val error = assertFailsWith<IllegalArgumentException> {
                splitter.splitAndCompress(input, format).toList()
            }
            error.message shouldContain "전용 writer를 사용하세요"
        }
    }

    @Test
    fun `split very small image but use min split height`() = runTest {
        getImage(AQUA_JPG).use { input ->
            val items = splitter.split(input, ImageFormat.JPG, 5).toList()
            log.debug { "items size=${items.size}" }
            items.shouldNotBeEmpty()
            items.all { it.isNotEmpty() }.shouldBeTrue()

            // 아주 작은 Height 를 지정하면, ImageSplitter.DEFAULT_MIN_HEIGHT 를 사용합니다.
            val firstImage = ImageIO.read(items[0].toInputStream())
            firstImage.height shouldBeEqualTo ImageSplitter.DEFAULT_MIN_HEIGHT
        }
    }

    @Test
    fun `split에서 이미지 높이가 분할 높이보다 작으면 원본을 그대로 반환한다`() = runTest {
        getImage(CAFE_JPG).use { input ->
            // CAFE_JPG의 높이보다 큰 splitHeight를 지정하면 분할 없이 1개의 이미지 반환
            val largeSplitter = ImageSplitter(10000)
            val items = largeSplitter.split(input, ImageFormat.JPG, 10000).toList()

            items.size shouldBeEqualTo 1
            items[0].shouldNotBeEmpty()

            // 반환된 바이트를 읽으면 유효한 이미지여야 한다
            val restored = ImageIO.read(items[0].toInputStream())
            (restored.width > 0).shouldBeTrue()
            (restored.height > 0).shouldBeTrue()
        }
    }

    @ParameterizedTest(name = "split {0}")
    @ValueSource(strings = [AQUA_JPG, EVERLAND_JPG])
    fun `split jpg image with default height`(path: String, tempFolder: TempFolder) = runTest {
        getImage(path).use { input ->
            val items: List<ByteArray> = splitter.split(input, ImageFormat.JPG).toList()
            log.debug { "items size=${items.size}" }

            items.forEach {
                val image = ImageIO.read(it.toInputStream())
                image.height shouldBeLessOrEqualTo splitter.defaultMaxHeight
            }
            items.forEach { bytes ->
                tempFolder.createFile().writeSuspending(bytes)
            }
        }
    }

    @ParameterizedTest(name = "split and compress {0}")
    @ValueSource(strings = [AQUA_JPG, EVERLAND_JPG])
    fun `split and compress image`(path: String, tempFolder: TempFolder) = runSuspendIO {
        getImage(path).use { input ->
            val items: Flow<ByteArray> = splitter
                .splitAndCompress(
                    input,
                    ImageFormat.JPG,
                    writer = SuspendJpegWriter.Default
                )

            items.buffer().collect { bytes ->
                bytes.shouldNotBeEmpty()
                val image = ImageIO.read(bytes.toInputStream())
                (image.width > 0).shouldBeTrue()
                (image.height > 0).shouldBeTrue()
                tempFolder.createFile().writeSuspending(bytes)
            }
        }
    }
}
