package io.bluetape4k.images.coroutines.animated

import com.sksamuel.scrimage.nio.AnimatedGifReader
import com.sksamuel.scrimage.nio.ImageSource
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.io.readAllBytesSuspending
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.junit5.tempfolder.TempFolder
import io.bluetape4k.junit5.tempfolder.TempFolderTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Path

@TempFolderTest
class AnimatedGifExtensionsTest : AbstractImageTest() {

    private val gifPath = Path.of("$BASE_PATH/animated.gif")

    private suspend fun loadTestGif() = AnimatedGifReader.read(
        ImageSource.of(gifPath.readAllBytesSuspending())
    )

    @Test
    fun `suspendBytes returns non-empty byte array`() = runSuspendIO {
        val gif = loadTestGif()
        val writer = SuspendGif2WebpWriter.Default

        val bytes = gif.suspendBytes(writer)

        bytes.shouldNotBeNull()
        bytes.size shouldBeGreaterThan 0
    }

    @Test
    fun `suspendWrite to ByteArrayOutputStream writes data`() = runSuspendIO {
        val gif = loadTestGif()
        val writer = SuspendGif2WebpWriter.Default
        val bos = ByteArrayOutputStream()

        gif.suspendWrite(writer, bos)

        bos.size() shouldBeGreaterThan 0
    }

    @Test
    fun `suspendWrite to Path creates file`(tempFolder: TempFolder) = runSuspendIO {
        val gif = loadTestGif()
        val writer = SuspendGif2WebpWriter.Default
        val output = tempFolder.createFile("out.webp").toPath()

        val saved = gif.suspendWrite(writer, output)

        saved.toFile().exists().shouldBeTrue()
        saved.toFile().length() shouldBeGreaterThan 0L
    }

    @Test
    fun `suspendOutput to File creates file`(tempFolder: TempFolder) = runSuspendIO {
        val gif = loadTestGif()
        val writer = SuspendGif2WebpWriter.Default
        val outputFile = tempFolder.createFile("out2.webp")

        val saved = gif.suspendOutput(writer, outputFile)

        saved.exists().shouldBeTrue()
        saved.length() shouldBeGreaterThan 0L
    }

    @Test
    fun `suspendOutput to Path creates file`(tempFolder: TempFolder) = runSuspendIO {
        val gif = loadTestGif()
        val writer = SuspendGif2WebpWriter.Default
        val outputPath = tempFolder.createFile("out3.webp").toPath()

        val saved = gif.suspendOutput(writer, outputPath)

        saved.toFile().exists().shouldBeTrue()
        saved.toFile().length() shouldBeGreaterThan 0L
    }

    @Test
    fun `forSuspendWriter returns SuspendAnimatedWriteContext`() = runSuspendIO {
        val gif = loadTestGif()
        val writer = SuspendGif2WebpWriter.Default

        val context = gif.forSuspendWriter(writer)

        context.shouldNotBeNull()
        context.gif.shouldNotBeNull()
    }
}
