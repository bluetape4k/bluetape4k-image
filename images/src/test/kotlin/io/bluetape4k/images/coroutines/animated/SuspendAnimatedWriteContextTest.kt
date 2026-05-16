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
class SuspendAnimatedWriteContextTest : AbstractImageTest() {

    private val gifPath = Path.of("$BASE_PATH/animated.gif")

    private suspend fun buildContext(): SuspendAnimatedWriteContext {
        val bytes = gifPath.readAllBytesSuspending()
        val gif = AnimatedGifReader.read(ImageSource.of(bytes))
        return SuspendAnimatedWriteContext(SuspendGif2WebpWriter.Default, gif)
    }

    @Test
    fun `bytes returns non-empty byte array`() = runSuspendIO {
        val ctx = buildContext()

        val bytes = ctx.bytes()

        bytes.shouldNotBeNull()
        bytes.size shouldBeGreaterThan 0
    }

    @Test
    fun `stream returns non-empty ByteArrayInputStream`() = runSuspendIO {
        val ctx = buildContext()

        val stream = ctx.stream()

        stream.shouldNotBeNull()
        stream.available() shouldBeGreaterThan 0
    }

    @Test
    fun `write to Path creates file with content`(tempFolder: TempFolder) = runSuspendIO {
        val ctx = buildContext()
        val output = tempFolder.createFile("ctx_out.webp").toPath()

        val saved = ctx.write(output)

        saved.toFile().exists().shouldBeTrue()
        saved.toFile().length() shouldBeGreaterThan 0L
    }

    @Test
    fun `write to path string creates file with content`(tempFolder: TempFolder) = runSuspendIO {
        val ctx = buildContext()
        val filePath = tempFolder.createFile("ctx_str.webp").absolutePath

        val saved = ctx.write(filePath)

        saved.toFile().exists().shouldBeTrue()
        saved.toFile().length() shouldBeGreaterThan 0L
    }

    @Test
    fun `write to File creates file with content`(tempFolder: TempFolder) = runSuspendIO {
        val ctx = buildContext()
        val file = tempFolder.createFile("ctx_file.webp")

        val saved = ctx.write(file)

        saved.exists().shouldBeTrue()
        saved.length() shouldBeGreaterThan 0L
    }

    @Test
    fun `write to OutputStream writes data`() = runSuspendIO {
        val ctx = buildContext()
        val bos = ByteArrayOutputStream()

        ctx.write(bos)

        bos.size() shouldBeGreaterThan 0
    }
}
