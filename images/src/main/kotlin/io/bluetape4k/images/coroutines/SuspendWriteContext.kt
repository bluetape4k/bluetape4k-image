package io.bluetape4k.images.coroutines

import com.sksamuel.scrimage.AwtImage
import com.sksamuel.scrimage.metadata.ImageMetadata
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.BufferedSuspendedSink
import io.bluetape4k.okio.coroutines.SuspendedSink
import io.bluetape4k.okio.coroutines.asBlocking
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.Sink
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Coroutines 방식으로 쓰기 작업 시 사용할 Context 입니다.
 *
 * ```
 * val writer = SuspendJpegWriter()
 * val image = immutableImageOf(File("image.jpg"))
 * val context = SuspendWriteContext(writer, image, metadata)
 * context.write("output.jpg")
 * ```
 *
 * @property writer 이미지 쓰기 작업을 수행할 [SuspendImageWriter]
 * @property image 이미지 데이터
 * @property metadata 이미지 메타데이터
 */
class SuspendWriteContext(
    val writer: SuspendImageWriter,
    private val image: AwtImage,
    private val metadata: ImageMetadata,
) {

    companion object: KLoggingChannel()

    /**
     * 이미지를 인코딩하여 [ByteArray]로 반환합니다.
     *
     * ```kotlin
     * val writer = SuspendJpegWriter.Default
     * val image = immutableImageOf(File("photo.jpg"))
     * val context = SuspendWriteContext(writer, image, image.metadata)
     * val bytes = context.bytes()
     * // bytes.isNotEmpty() == true
     * ```
     *
     * @return 인코딩된 이미지 데이터
     */
    suspend fun bytes(): ByteArray {
        return ByteArrayOutputStream().use { bos ->
            writer.suspendWrite(image, metadata, bos)
            bos.toByteArray()
        }
    }

    /**
     * 이미지를 인코딩하여 [ByteArrayInputStream]으로 반환합니다.
     *
     * ```kotlin
     * val writer = SuspendPngWriter.MaxCompression
     * val image = immutableImageOf(File("photo.png"))
     * val context = SuspendWriteContext(writer, image, image.metadata)
     * val stream = context.stream()
     * // stream.available() > 0
     * ```
     *
     * @return 인코딩된 이미지 데이터를 담은 [ByteArrayInputStream]
     */
    suspend fun stream(): ByteArrayInputStream {
        return ByteArrayInputStream(bytes())
    }

    /**
     * 이미지를 인코딩하여 [path] 경로 문자열에 저장합니다.
     *
     * ```kotlin
     * val writer = SuspendJpegWriter.Default
     * val image = immutableImageOf(File("photo.jpg"))
     * val context = SuspendWriteContext(writer, image, image.metadata)
     * val saved = context.write("/tmp/output.jpg")
     * // saved.toFile().exists() == true
     * ```
     *
     * @param path 저장할 파일 경로 문자열
     * @return 저장된 파일의 [Path]
     */
    suspend fun write(path: String): Path {
        return write(Paths.get(path))
    }

    /**
     * 이미지를 인코딩하여 [file]에 저장합니다.
     *
     * ```kotlin
     * val writer = SuspendPngWriter.MaxCompression
     * val image = immutableImageOf(File("photo.png"))
     * val context = SuspendWriteContext(writer, image, image.metadata)
     * val saved = context.write(File("/tmp/output.png"))
     * // saved.exists() == true
     * ```
     *
     * @param file 저장할 대상 [File]
     * @return 저장된 [File]
     */
    suspend fun write(file: File): File {
        write(file.toPath())
        return file
    }

    /**
     * 이미지를 인코딩하여 [path]에 저장합니다.
     *
     * ```kotlin
     * val writer = SuspendJpegWriter.Default
     * val image = immutableImageOf(File("photo.jpg"))
     * val context = SuspendWriteContext(writer, image, image.metadata)
     * val saved = context.write(Path.of("/tmp/output.jpg"))
     * // saved.toFile().exists() == true
     * ```
     *
     * @param path 저장할 파일 [Path]
     * @return 저장된 파일의 [Path]
     */
    suspend fun write(path: Path): Path {
        withContext(Dispatchers.IO) {
            Files.newOutputStream(path).use { out ->
                writer.suspendWrite(image, metadata, out)
            }
        }
        return path
    }

    /**
     * 이미지를 인코딩하여 [out]에 씁니다.
     *
     * ```kotlin
     * val writer = SuspendPngWriter.NoCompression
     * val image = immutableImageOf(File("photo.png"))
     * val context = SuspendWriteContext(writer, image, image.metadata)
     * val bos = ByteArrayOutputStream()
     * context.write(bos)
     * // bos.size() > 0
     * ```
     *
     * @param out 쓰기 대상 [OutputStream]
     */
    suspend fun write(out: OutputStream) {
        writer.suspendWrite(image, metadata, out)
    }

    /**
     * 인코딩된 이미지를 호출자가 소유한 [BufferedSink]에 씁니다.
     *
     * ```
     * val writer = SuspendPngWriter.NoCompression
     * val image = immutableImageOf(File("photo.png"))
     * val context = SuspendWriteContext(writer, image, image.metadata)
     * val buffer = Buffer()
     * context.write(buffer)
     * // buffer.size > 0
     * ```
     *
     * @param sink 출력 sink입니다. 이 함수는 flush만 수행하고 닫지는 않습니다.
     */
    suspend fun write(sink: BufferedSink) {
        writer.suspendWrite(image, metadata, sink.outputStream())
        sink.flush()
    }

    /**
     * 인코딩된 이미지를 Okio [Sink]에 씁니다.
     *
     * 이 overload는 [sink]를 buffer하고 닫습니다. 호출자가 sink lifecycle을 계속
     * 소유해야 한다면 [BufferedSink]를 받는 [write]를 사용합니다.
     *
     * @param sink 출력 sink입니다.
     */
    suspend fun write(sink: Sink) {
        sink.buffered().use { bufferedSink ->
            write(bufferedSink)
        }
    }

    /**
     * 인코딩된 이미지를 호출자가 소유한 [BufferedSuspendedSink]에 씁니다.
     *
     * Scrimage encoder는 blocking 방식이므로, 이 overload는 호출자가 sink lifecycle을
     * 소유한 상태를 보존하면서 suspended sink를 blocking Okio sink로 연결합니다.
     *
     * @param sink 출력 sink입니다. 이 함수는 flush만 수행하고 닫지는 않습니다.
     */
    suspend fun write(sink: BufferedSuspendedSink) {
        val blockingSink = sink.asBlocking().buffered()
        writer.suspendWrite(image, metadata, blockingSink.outputStream())
        blockingSink.flush()
    }

    /**
     * 인코딩된 이미지를 [SuspendedSink]에 씁니다.
     *
     * 이 overload는 [sink]를 buffer하고 닫습니다. 호출자가 sink lifecycle을 계속
     * 소유해야 한다면 [BufferedSuspendedSink]를 받는 [write]를 사용합니다.
     *
     * @param sink 출력 sink입니다.
     */
    suspend fun write(sink: SuspendedSink) {
        val bufferedSink = sink.bufferedSuspended()
        try {
            write(bufferedSink)
        } finally {
            bufferedSink.close()
        }
    }
}
