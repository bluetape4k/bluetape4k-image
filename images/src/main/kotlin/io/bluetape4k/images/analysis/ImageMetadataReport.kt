package io.bluetape4k.images.analysis

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.gif.GifHeaderDirectory
import com.drew.metadata.gif.GifImageDirectory
import com.drew.metadata.heif.HeifDirectory
import com.drew.metadata.icc.IccDirectory
import com.drew.metadata.iptc.IptcDirectory
import com.drew.metadata.jpeg.JpegDirectory
import com.drew.metadata.png.PngDirectory
import com.drew.metadata.webp.WebpDirectory
import com.drew.metadata.xmp.XmpDirectory
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.logging.KotlinLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * metadata 추출 API가 보고하는 헤더 기반 이미지 크기입니다.
 *
 * core [ImageDimensions] 값 객체를 재사용하므로, 호출자는 metadata report와 image probe에
 * 동일한 디코딩 크기 검증 helper를 적용할 수 있습니다.
 */
typealias ImageMetadataDimensions = ImageDimensions

/**
 * privacy-aware metadata report 추출 옵션입니다.
 *
 * 기본 report는 public API 응답에 안전하도록 GPS 필드를 제거하고 원시 diagnostic tag를
 * 생략합니다. [includeDiagnosticTags]는 제한된 tag 설명이 backend metadata 동작을
 * 설명하는 데 필요한 내부 로그나 운영자 도구에서만 켭니다.
 */
data class ImageMetadataReadOptions(
    val maxBytes: Int = DEFAULT_MAX_BYTES,
    val stripSensitiveMetadata: Boolean = true,
    val includeDiagnosticTags: Boolean = false,
    val maxDiagnosticValueLength: Int = DEFAULT_MAX_DIAGNOSTIC_VALUE_LENGTH,
) : Serializable {

    init {
        maxBytes.requirePositiveNumber("maxBytes")
        maxDiagnosticValueLength.requirePositiveNumber("maxDiagnosticValueLength")
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Int = 50 * 1024 * 1024
        const val DEFAULT_MAX_DIAGNOSTIC_VALUE_LENGTH: Int = 256

        private const val serialVersionUID: Long = 1L
    }
}

/**
 * strict metadata inspection이 실패한 이유를 나타내는 제한된 분류입니다.
 *
 * parser가 반환한 원시 예외와 메시지는 public contract에 노출하지 않습니다.
 */
enum class ImageMetadataReadFailureKind {
    SIZE_LIMIT,
    IO,
    PARSE,
}

/**
 * metadata reader의 성공과 검증 불가 상태를 구분하는 strict 결과입니다.
 *
 * 기존 [readImageMetadataReport]는 진단 도구 호환성을 위해 실패 시
 * [ImageMetadataReport.EMPTY]를 반환합니다. privacy enforcement 같은 fail-closed
 * 호출자는 이 결과를 사용해 `Failure`를 absence로 오인하지 않아야 합니다.
 * 이 결과는 bounded report 또는 제한된 실패 분류만 담으므로 직렬화할 수 있지만,
 * parser 예외·원시 payload·caller-owned stream은 보존하지 않습니다.
 */
sealed interface ImageMetadataReadResult : Serializable {
    /** metadata를 정상적으로 읽은 결과입니다. */
    data class Success(val report: ImageMetadataReport): ImageMetadataReadResult {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** metadata를 읽을 수 없어 검증을 진행할 수 없는 결과입니다. */
    data class Failure(val kind: ImageMetadataReadFailureKind): ImageMetadataReadResult {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }
}

/**
 * 정제된 ICC profile 요약입니다.
 *
 * report는 작은 scalar 사실만 노출합니다. 원시 ICC payload, native pointer,
 * source file path는 절대 담지 않습니다.
 */
data class ImageMetadataIccProfile(
    val byteCount: Int? = null,
    val colorSpace: String? = null,
    val profileVersion: String? = null,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * best-effort HDR 및 gain-map metadata hint입니다.
 *
 * 이 flag들은 보수적으로 설정됩니다. parsing된 metadata directory 이름, tag 이름,
 * description에 식별 가능한 HDR 또는 gain-map 용어가 있을 때만 `true`입니다.
 */
data class ImageMetadataHdrHints(
    val hasHdrHint: Boolean = false,
    val hasGainMapHint: Boolean = false,
) : Serializable {

    val isEmpty: Boolean
        get() = !hasHdrHint && !hasGainMapHint

    companion object {
        val EMPTY = ImageMetadataHdrHints()

        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 내부 observability를 위한 제한된 diagnostic metadata입니다.
 *
 * diagnostic 정보는 [ImageMetadataReadOptions.includeDiagnosticTags]로 명시적으로
 * opt-in해야 합니다. sensitive metadata 제거가 활성화되면 tag 값은 잘리고
 * GPS directory는 생략됩니다.
 */
data class ImageMetadataDirectoryReport(
    val name: String,
    val tags: Map<String, String> = emptyMap(),
) : Serializable {

    init {
        name.requireNotBlank("name")
        tags.forEach { (key, value) ->
            key.requireNotBlank("tags key")
            value.requireNotBlank("tags[$key]")
        }
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * privacy-aware 이미지 metadata 추출 report입니다.
 *
 * 기본 report는 public DTO에 적합합니다. 정규화된 EXIF, 크기, 방향, 존재 여부 flag,
 * 작은 ICC/HDR 요약만 포함하며 원시 metadata blob은 담지 않습니다.
 * [ImageMetadataReadOptions.stripSensitiveMetadata]가 `false`인 상태로 생성된 report를
 * 외부에 노출해야 한다면 먼저 [withoutSensitiveMetadata]를 적용합니다.
 */
data class ImageMetadataReport(
    val exif: ExifData = ExifData.EMPTY,
    val dimensions: ImageMetadataDimensions? = null,
    val orientation: Int? = null,
    val pageCount: Int? = null,
    val containsXmp: Boolean = false,
    val containsIptc: Boolean = false,
    val containsIccProfile: Boolean = false,
    val iccProfile: ImageMetadataIccProfile? = null,
    val hdrHints: ImageMetadataHdrHints = ImageMetadataHdrHints.EMPTY,
    val diagnostics: List<ImageMetadataDirectoryReport> = emptyList(),
    /** EXIF directory가 존재했는지 나타내며, 알 수 없는 tag도 포함합니다. */
    val containsExif: Boolean = false,
    /** GPS directory가 존재했는지 나타내며, 부분적으로만 채워진 GPS도 포함합니다. */
    val containsGps: Boolean = false,
) : Serializable {

    val hasAnyMetadata: Boolean
        get() = this != EMPTY

    val hasSensitiveMetadata: Boolean
        get() = containsGps || exif.hasGps

    fun withoutSensitiveMetadata(): ImageMetadataReport =
        copy(exif = exif.withoutGps(), containsGps = false)

    companion object {
        val EMPTY = ImageMetadataReport()

        private const val serialVersionUID: Long = 2L
    }
}

private val metadataLog = KotlinLogging.logger(ImageMetadataReport::class)

/**
 * 기존 metadata report에 정제된 backend header field를 추가합니다.
 *
 * libvips 기반 reader처럼 이 module을 native runtime에 결합하지 않고 작은 header 사실을
 * 노출할 수 있는 선택적 backend adapter를 위한 확장입니다. diagnostic directory를
 * 추가하기 전에 원시 path, native pointer, 위치 필드, 제한 없는 blob을 걸러냅니다.
 */
fun ImageMetadataReport.withBackendHeaderFields(
    sourceBackend: String,
    headerFields: Map<String, String>,
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport {
    sourceBackend.requireNotBlank("sourceBackend")
    if (headerFields.isEmpty()) {
        return this
    }

    val safeTags = headerFields.toSafeDiagnosticTags(options)
    if (safeTags.isEmpty()) {
        return this
    }

    val backendHints = safeTags.toHdrHints()
    return copy(
        hdrHints = ImageMetadataHdrHints(
            hasHdrHint = hdrHints.hasHdrHint || backendHints.hasHdrHint,
            hasGainMapHint = hdrHints.hasGainMapHint || backendHints.hasGainMapHint,
        ),
        diagnostics = if (options.includeDiagnosticTags) {
            diagnostics + ImageMetadataDirectoryReport(
                name = "$sourceBackend header fields",
                tags = safeTags,
            )
        } else {
            diagnostics
        },
    )
}

/**
 * 인코딩 이미지 바이트에서 privacy-aware metadata report를 읽습니다.
 */
fun readImageMetadataReport(
    bytes: ByteArray,
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport {
    require(bytes.size <= options.maxBytes) {
        "Image byte array exceeds ${options.maxBytes} bytes: ${bytes.size} bytes"
    }
    return readImageMetadataReport(ByteArrayInputStream(bytes), options, closeStream = true)
}

/**
 * 인코딩 이미지 바이트를 strict하게 검사합니다.
 *
 * parse/I/O 실패를 [ImageMetadataReport.EMPTY]로 축약하지 않고 [ImageMetadataReadResult.Failure]로
 * 반환합니다. 따라서 privacy-safe output 검증은 실패를 metadata 부재로 오인하지 않고
 * fail-closed 정책을 적용할 수 있습니다.
 */
fun readImageMetadataReportStrict(
    bytes: ByteArray,
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReadResult {
    if (bytes.size > options.maxBytes) {
        return ImageMetadataReadResult.Failure(ImageMetadataReadFailureKind.SIZE_LIMIT)
    }

    return ByteArrayInputStream(bytes).use { input ->
        readStrictMetadata(input, options)
    }
}

/**
 * [File]에서 privacy-aware metadata report를 읽습니다.
 */
fun File.readImageMetadataReport(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport =
    try {
        requireLengthWithin(options.maxBytes, "file")
        ImageMetadataReader.readMetadata(this).toImageMetadataReport(options)
    } catch (e: IOException) {
        metadataLog.warn(e) { "Metadata report I/O error (File)" }
        ImageMetadataReport.EMPTY
    } catch (e: Exception) {
        metadataLog.debug(e) { "Metadata report parse failure (File)" }
        ImageMetadataReport.EMPTY
    }

/**
 * [Path]에서 privacy-aware metadata report를 읽습니다.
 */
fun Path.readImageMetadataReport(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport =
    try {
        requireLengthWithin(options.maxBytes, "path")
        Files.newInputStream(this).use { input ->
            ImageMetadataReader.readMetadata(input).toImageMetadataReport(options)
        }
    } catch (e: IOException) {
        metadataLog.warn(e) { "Metadata report file open failure (Path)" }
        ImageMetadataReport.EMPTY
    } catch (e: Exception) {
        metadataLog.debug(e) { "Metadata report parse failure (Path)" }
        ImageMetadataReport.EMPTY
    }

/**
 * [Path]의 metadata를 strict하게 검사합니다.
 *
 * 파일 크기·I/O·parser 실패를 각각 제한된 [ImageMetadataReadFailureKind]로 보존합니다.
 */
fun Path.readImageMetadataReportStrict(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReadResult =
    try {
        if (Files.size(this) > options.maxBytes) {
            ImageMetadataReadResult.Failure(ImageMetadataReadFailureKind.SIZE_LIMIT)
        } else {
            Files.newInputStream(this).use { input ->
                readStrictMetadata(input, options)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ImageMetadataReadResult.Failure(ImageMetadataReadFailureKind.IO)
    } catch (e: Exception) {
        ImageMetadataReadResult.Failure(ImageMetadataReadFailureKind.PARSE)
    }

/**
 * [InputStream]에서 privacy-aware metadata report를 읽습니다.
 *
 * stream은 계속 호출자가 소유하며 이 함수는 stream을 닫지 않습니다.
 */
fun InputStream.readImageMetadataReport(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport =
    readImageMetadataReport(readBoundedBytes(options.maxBytes), options)

/**
 * caller-owned [InputStream]의 metadata를 strict하게 검사합니다.
 *
 * stream은 닫지 않으며, bounded read나 parser 실패는 [ImageMetadataReadResult.Failure]로
 * 반환합니다.
 */
fun InputStream.readImageMetadataReportStrict(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReadResult =
    try {
        readImageMetadataReportStrict(readBoundedBytes(options.maxBytes), options)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        ImageMetadataReadResult.Failure(ImageMetadataReadFailureKind.SIZE_LIMIT)
    } catch (e: IOException) {
        ImageMetadataReadResult.Failure(ImageMetadataReadFailureKind.IO)
    } catch (e: Exception) {
        ImageMetadataReadResult.Failure(ImageMetadataReadFailureKind.PARSE)
    }

/**
 * [Dispatchers.IO] 위에서 [File]의 metadata report를 읽습니다.
 */
suspend fun File.suspendReadImageMetadataReport(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport =
    withContext(Dispatchers.IO) { readImageMetadataReport(options) }

/**
 * [Dispatchers.IO] 위에서 [Path]의 metadata report를 읽습니다.
 */
suspend fun Path.suspendReadImageMetadataReport(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport =
    withContext(Dispatchers.IO) { readImageMetadataReport(options) }

internal fun Metadata.toImageMetadataReport(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport {
    val rawExif = toExifData()
    val exif = if (options.stripSensitiveMetadata) rawExif.withoutGps() else rawExif
    val dimensions = findDimensions(exif)
    val iccProfile = findIccProfile()
    val hdrHints = findHdrHints()
    val report = ImageMetadataReport(
        exif = exif,
        dimensions = dimensions,
        orientation = exif.orientation ?: findHeifRotation(),
        pageCount = findPageCount(),
        containsXmp = containsDirectoryOfType(XmpDirectory::class.java),
        containsIptc = containsDirectoryOfType(IptcDirectory::class.java),
        containsIccProfile = containsDirectoryOfType(IccDirectory::class.java),
        iccProfile = iccProfile,
        hdrHints = hdrHints,
        diagnostics = if (options.includeDiagnosticTags) toDiagnostics(options) else emptyList(),
        containsExif = containsDirectoryOfType(ExifDirectoryBase::class.java),
        containsGps = containsDirectoryOfType(GpsDirectory::class.java),
    )
    return if (report.hasAnyMetadata) report else ImageMetadataReport.EMPTY
}

private fun readImageMetadataReport(
    input: InputStream,
    options: ImageMetadataReadOptions,
    closeStream: Boolean,
): ImageMetadataReport =
    try {
        val metadata = if (closeStream) {
            input.use(ImageMetadataReader::readMetadata)
        } else {
            ImageMetadataReader.readMetadata(input)
        }
        metadata.toImageMetadataReport(options)
    } catch (e: IOException) {
        metadataLog.warn(e) { "Metadata report I/O error (InputStream)" }
        ImageMetadataReport.EMPTY
    } catch (e: Exception) {
        metadataLog.debug(e) { "Metadata report parse failure (InputStream)" }
        ImageMetadataReport.EMPTY
    }

private fun readStrictMetadata(
    input: InputStream,
    options: ImageMetadataReadOptions,
): ImageMetadataReadResult =
    try {
        ImageMetadataReadResult.Success(
            ImageMetadataReader.readMetadata(input).toImageMetadataReport(options),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ImageMetadataReadResult.Failure(ImageMetadataReadFailureKind.IO)
    } catch (e: Exception) {
        ImageMetadataReadResult.Failure(ImageMetadataReadFailureKind.PARSE)
    }

private fun File.requireLengthWithin(maxBytes: Int, subject: String) {
    if (exists()) {
        require(length() <= maxBytes) {
            "Image $subject exceeds $maxBytes bytes: ${length()} bytes"
        }
    }
}

private fun Path.requireLengthWithin(maxBytes: Int, subject: String) {
    val size = Files.size(this)
    require(size <= maxBytes) {
        "Image $subject exceeds $maxBytes bytes: $size bytes"
    }
}

private fun InputStream.readBoundedBytes(maxBytes: Int): ByteArray {
    maxBytes.requirePositiveNumber("maxBytes")
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) {
            break
        }
        total += read
        require(total <= maxBytes) {
            "Image stream exceeds $maxBytes bytes: more than $total bytes"
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun Metadata.findDimensions(exif: ExifData): ImageMetadataDimensions? =
    firstDimension(
        jpegDimensions(),
        pngDimensions(),
        gifDimensions(),
        webpDimensions(),
        heifDimensions(),
        exif.width?.let { width -> exif.height?.let { height -> ImageMetadataDimensions(width, height) } },
    )

private fun firstDimension(vararg candidates: ImageMetadataDimensions?): ImageMetadataDimensions? =
    candidates.filterNotNull().firstOrNull()

private fun Metadata.jpegDimensions(): ImageMetadataDimensions? =
    getFirstDirectoryOfType(JpegDirectory::class.java)?.let { directory ->
        readDimensions(
            directory.getInteger(JpegDirectory.TAG_IMAGE_WIDTH),
            directory.getInteger(JpegDirectory.TAG_IMAGE_HEIGHT),
        )
    }

private fun Metadata.pngDimensions(): ImageMetadataDimensions? =
    getDirectoriesOfType(PngDirectory::class.java)
        .firstNotNullOfOrNull { directory ->
            readDimensions(
                directory.getInteger(PngDirectory.TAG_IMAGE_WIDTH),
                directory.getInteger(PngDirectory.TAG_IMAGE_HEIGHT),
            )
        }

private fun Metadata.gifDimensions(): ImageMetadataDimensions? =
    getFirstDirectoryOfType(GifHeaderDirectory::class.java)?.let { directory ->
        readDimensions(
            directory.getInteger(GifHeaderDirectory.TAG_IMAGE_WIDTH),
            directory.getInteger(GifHeaderDirectory.TAG_IMAGE_HEIGHT),
        )
    }

private fun Metadata.webpDimensions(): ImageMetadataDimensions? =
    getFirstDirectoryOfType(WebpDirectory::class.java)?.let { directory ->
        readDimensions(
            directory.getInteger(WebpDirectory.TAG_IMAGE_WIDTH),
            directory.getInteger(WebpDirectory.TAG_IMAGE_HEIGHT),
        )
    }

private fun Metadata.heifDimensions(): ImageMetadataDimensions? =
    getFirstDirectoryOfType(HeifDirectory::class.java)?.let { directory ->
        readDimensions(
            directory.getInteger(HeifDirectory.TAG_IMAGE_WIDTH),
            directory.getInteger(HeifDirectory.TAG_IMAGE_HEIGHT),
        )
    }

private fun readDimensions(width: Int?, height: Int?): ImageMetadataDimensions? =
    if (width != null && height != null && width > 0 && height > 0) {
        ImageMetadataDimensions(width, height)
    } else {
        null
    }

private fun Metadata.findHeifRotation(): Int? =
    getFirstDirectoryOfType(HeifDirectory::class.java)
        ?.getInteger(HeifDirectory.TAG_IMAGE_ROTATION)

private fun Metadata.findPageCount(): Int? =
    getDirectoriesOfType(GifImageDirectory::class.java)
        .count()
        .takeIf { it > 1 }

private fun Metadata.findIccProfile(): ImageMetadataIccProfile? =
    getFirstDirectoryOfType(IccDirectory::class.java)?.let { directory ->
        ImageMetadataIccProfile(
            byteCount = directory.getInteger(IccDirectory.TAG_PROFILE_BYTE_COUNT),
            colorSpace = directory.getString(IccDirectory.TAG_COLOR_SPACE)?.trim(),
            profileVersion = directory.getString(IccDirectory.TAG_PROFILE_VERSION)?.trim(),
        )
    }

private fun Metadata.findHdrHints(): ImageMetadataHdrHints {
    val searchableText = getDirectories().flatMap { directory ->
        buildList {
            add(directory.name)
            directory.tags.forEach { tag ->
                add(tag.tagName)
                add(tag.description.orEmpty())
            }
        }
    }.joinToString(separator = " ").lowercase()
    return searchableText.toHdrHints()
}

private fun Map<String, String>.toHdrHints(): ImageMetadataHdrHints =
    entries.joinToString(separator = " ") { (key, value) -> "$key $value" }.lowercase().toHdrHints()

private fun String.toHdrHints(): ImageMetadataHdrHints {
    return ImageMetadataHdrHints(
        hasHdrHint = "hdr" in this || "high dynamic range" in this,
        hasGainMapHint = "gain map" in this || "gainmap" in this,
    )
}

private fun Metadata.toDiagnostics(options: ImageMetadataReadOptions): List<ImageMetadataDirectoryReport> =
    getDirectories()
        .filterNot { options.stripSensitiveMetadata && it is GpsDirectory }
        .mapNotNull { directory -> directory.toDiagnosticReport(options.maxDiagnosticValueLength) }

private fun Directory.toDiagnosticReport(maxDiagnosticValueLength: Int): ImageMetadataDirectoryReport? {
    val tags = getTags()
        .mapNotNull { tag ->
            val name = tag.tagName.trim()
            val value = tag.description?.trim().orEmpty()
            if (name.isBlank() || value.isBlank()) {
                null
            } else {
                name to value.truncate(maxDiagnosticValueLength)
            }
        }
        .toMap()
    return ImageMetadataDirectoryReport(name = name, tags = tags)
        .takeIf { it.tags.isNotEmpty() }
}

private fun Map<String, String>.toSafeDiagnosticTags(options: ImageMetadataReadOptions): Map<String, String> =
    mapNotNull { (key, value) ->
        val name = key.trim()
        val description = value.trim()
        if (name.isBlank() || description.isBlank() || name.isUnsafeDiagnosticKey(options)) {
            null
        } else {
            name to description.truncate(options.maxDiagnosticValueLength)
        }
    }.toMap()

private fun String.isUnsafeDiagnosticKey(options: ImageMetadataReadOptions): Boolean {
    val normalized = lowercase()
    val compact = normalized.replace(Regex("[^a-z0-9]"), "")
    val sourceUnsafe = listOf(
        "path",
        "filepath",
        "filename",
        "sourcepath",
        "sourcefile",
        "uri",
        "url",
        "native",
        "pointer",
        "address",
        "memory",
    ).any { it in compact }
    val blobUnsafe = listOf("blob", "raw", "bytes").any { it in normalized }
    val locationUnsafe = options.stripSensitiveMetadata &&
        listOf("gps", "latitude", "longitude", "altitude").any { it in normalized }
    return sourceUnsafe || blobUnsafe || locationUnsafe
}

private fun String.truncate(maxLength: Int): String =
    if (length <= maxLength) this else take(maxLength)
