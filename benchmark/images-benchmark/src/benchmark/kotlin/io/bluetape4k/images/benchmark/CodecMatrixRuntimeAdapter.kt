package io.bluetape4k.images.benchmark

import io.bluetape4k.images.vips.VipsCodecDirection
import io.bluetape4k.images.vips.VipsCodecOperationCapability
import io.bluetape4k.images.vips.VipsCodecSupport
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.images.vips.VipsRuntime
import java.lang.reflect.InvocationTargetException

@OptIn(VipsIncubatingApi::class)
internal class CodecMatrixRuntimeAdapter private constructor(
    private val backend: CodecMatrixBackend,
    private val runtime: VipsRuntime,
    private val imageFactory: (ByteArray) -> VipsImage,
) : CodecMatrixCodecOps {

    val backendName: String
    val libvipsVersion: String?

    init {
        runtime.init(concurrency = 4)
        val report = runtime.codecCapabilityReport()
        require(report.backendName == backend.expectedRuntimeName()) {
            "requested backend and reported runtime identity differ"
        }
        backendName = report.backendName
        libvipsVersion = report.libvipsVersion?.let(::sanitizeCodecMatrixText)
    }

    override fun open(bytes: ByteArray): CodecMatrixCodecHandle {
        val image = imageFactory(bytes)
        return object : CodecMatrixCodecHandle {
            override val width: Int get() = image.width
            override val height: Int get() = image.height

            override fun toBytes(format: CodecMatrixFormat): ByteArray =
                image.toBytes(format.toVipsFormat(), VipsEncodeOptions.Default)

            override fun close() {
                image.close()
            }
        }
    }

    fun capabilities(format: CodecMatrixFormat): List<CodecMatrixDirectionalCapability> {
        val report = runtime.codecCapabilityReport()
        val vipsFormat = format.toVipsFormat()
        if (report.isStableFormat(vipsFormat)) {
            return CodecMatrixDirection.entries.map { direction ->
                CodecMatrixDirectionalCapability(format, direction, CodecMatrixCapabilitySupport.AVAILABLE)
            }
        }
        val capability = report.codec(vipsFormat)
        return listOf(
            capability.decode.toMatrix(format),
            capability.encode.toMatrix(format),
        )
    }

    companion object {
        private const val FFM_RUNTIME_CLASS = "io.bluetape4k.images.vips.java25.FfmVipsRuntime"
        private const val JNI_RUNTIME_CLASS = "io.bluetape4k.images.vips.java21.JVipsRuntime"
        private const val FFM_IMAGE_SUPPORT_CLASS = "io.bluetape4k.images.vips.java25.FfmVipsImageSupportKt"
        private const val JNI_IMAGE_SUPPORT_CLASS = "io.bluetape4k.images.vips.java21.JVipsImageSupportKt"

        fun create(backend: CodecMatrixBackend): CodecMatrixRuntimeAdapter {
            val (runtimeClass, imageSupportClass, factoryMethod) = when (backend) {
                CodecMatrixBackend.JAVA21 -> Triple(JNI_RUNTIME_CLASS, JNI_IMAGE_SUPPORT_CLASS, "vipsImageOf")
                CodecMatrixBackend.JAVA25 -> Triple(FFM_RUNTIME_CLASS, FFM_IMAGE_SUPPORT_CLASS, "ffmVipsImageOf")
            }
            val runtime = Class.forName(runtimeClass).getField("INSTANCE").get(null) as VipsRuntime
            val method = Class.forName(imageSupportClass).getMethod(factoryMethod, ByteArray::class.java)
            val factory: (ByteArray) -> VipsImage = { bytes ->
                try {
                    method.invoke(null, bytes) as VipsImage
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
            return CodecMatrixRuntimeAdapter(backend, runtime, factory)
        }
    }
}

private fun CodecMatrixBackend.expectedRuntimeName(): String = when (this) {
    CodecMatrixBackend.JAVA21 -> "JVips/JNI"
    CodecMatrixBackend.JAVA25 -> "vips-ffm"
}

@OptIn(VipsIncubatingApi::class)
private fun CodecMatrixFormat.toVipsFormat(): VipsImageFormat = when (this) {
    CodecMatrixFormat.JPEG -> VipsImageFormat.JPEG
    CodecMatrixFormat.PNG -> VipsImageFormat.PNG
    CodecMatrixFormat.WEBP -> VipsImageFormat.WEBP
    CodecMatrixFormat.AVIF -> VipsImageFormat.AVIF
    CodecMatrixFormat.HEIC -> VipsImageFormat.HEIC
}

private fun VipsCodecOperationCapability.toMatrix(
    format: CodecMatrixFormat,
): CodecMatrixDirectionalCapability = CodecMatrixDirectionalCapability(
    format = format,
    direction = when (direction) {
        VipsCodecDirection.ENCODE -> CodecMatrixDirection.ENCODE
        VipsCodecDirection.DECODE -> CodecMatrixDirection.DECODE
    },
    support = when (support) {
        VipsCodecSupport.AVAILABLE -> CodecMatrixCapabilitySupport.AVAILABLE
        VipsCodecSupport.UNAVAILABLE -> CodecMatrixCapabilitySupport.UNAVAILABLE
        VipsCodecSupport.UNKNOWN -> CodecMatrixCapabilitySupport.UNKNOWN
    },
    reason = reason?.let(::sanitizeCodecMatrixText),
)
