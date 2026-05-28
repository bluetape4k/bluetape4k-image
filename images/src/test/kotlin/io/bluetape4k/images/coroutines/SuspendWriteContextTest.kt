package io.bluetape4k.images.coroutines

import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.images.forSuspendWriter
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.junit5.tempfolder.TempFolder
import io.bluetape4k.junit5.tempfolder.TempFolderTest
import io.bluetape4k.okio.coroutines.asSuspendedSink
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import okio.Buffer
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

@TempFolderTest
class SuspendWriteContextTest : AbstractImageTest() {

    private val writer = SuspendJpegWriter.Default

    @Test
    fun `bytes returns non-empty byte array`() = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val ctx = image.forSuspendWriter(writer)

        val bytes = ctx.bytes()

        bytes.shouldNotBeNull()
        bytes.size shouldBeGreaterThan 0
    }

    @Test
    fun `stream returns non-empty ByteArrayInputStream`() = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val ctx = image.forSuspendWriter(writer)

        val stream = ctx.stream()

        stream.shouldNotBeNull()
        stream.available() shouldBeGreaterThan 0
    }

    @Test
    fun `write to Path creates file with content`(tempFolder: TempFolder) = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val ctx = image.forSuspendWriter(writer)
        val output = tempFolder.createFile("out.jpg").toPath()

        val saved = ctx.write(output)

        saved.toFile().exists().shouldBeTrue()
        saved.toFile().length() shouldBeGreaterThan 0L
    }

    @Test
    fun `write to File creates file with content`(tempFolder: TempFolder) = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val ctx = image.forSuspendWriter(writer)
        val file = tempFolder.createFile("out2.jpg")

        val saved = ctx.write(file)

        saved.exists().shouldBeTrue()
        saved.length() shouldBeGreaterThan 0L
    }

    @Test
    fun `write to OutputStream writes data`() = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val ctx = image.forSuspendWriter(writer)
        val bos = ByteArrayOutputStream()

        ctx.write(bos)

        bos.size() shouldBeGreaterThan 0
    }

    @Test
    fun `write to Okio BufferedSink writes data`() = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val ctx = image.forSuspendWriter(writer)
        val buffer = Buffer()

        ctx.write(buffer)

        buffer.size shouldBeGreaterThan 0L
    }

    @Test
    fun `write to suspended file sink writes data`(tempFolder: TempFolder) = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val ctx = image.forSuspendWriter(writer)
        val output = tempFolder.createFile("suspended-out.jpg").toPath()
        val channel = AsynchronousFileChannel.open(output, WRITE, CREATE, TRUNCATE_EXISTING)

        ctx.write(channel.asSuspendedSink())

        output.toFile().length() shouldBeGreaterThan 0L
    }

    @Test
    fun `write to buffered suspended file sink keeps caller ownership`(tempFolder: TempFolder) = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val ctx = image.forSuspendWriter(writer)
        val output = tempFolder.createFile("buffered-suspended-out.jpg").toPath()
        val channel = AsynchronousFileChannel.open(output, WRITE, CREATE, TRUNCATE_EXISTING)
        val sink = channel.asSuspendedSink().bufferedSuspended()

        ctx.write(sink)

        output.toFile().length() shouldBeGreaterThan 0L
        channel.isOpen.shouldBeTrue()
        sink.close()
    }

    @Test
    fun `write to path string creates file`(tempFolder: TempFolder) = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val ctx = image.forSuspendWriter(writer)
        val filePath = tempFolder.createFile("out3.jpg").absolutePath

        val saved = ctx.write(filePath)

        saved.toFile().exists().shouldBeTrue()
        saved.toFile().length() shouldBeGreaterThan 0L
    }
}
