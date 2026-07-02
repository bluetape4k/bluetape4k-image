package io.bluetape4k.images.analysis

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
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
 * Header-derived dimensions reported by the metadata extraction API.
 *
 * This reuses the core [ImageDimensions] value object so callers can apply the
 * same decoded-size validation helpers to metadata reports and image probes.
 */
typealias ImageMetadataDimensions = ImageDimensions

/**
 * Options for privacy-aware metadata report extraction.
 *
 * By default the report is safe for public API responses: GPS fields are
 * removed and raw diagnostic tags are omitted. Set [includeDiagnosticTags] only
 * for internal logs or operator tooling, where bounded tag descriptions help
 * explain backend metadata behavior.
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
 * Sanitized ICC profile summary.
 *
 * The report exposes small scalar facts only. It never carries the raw ICC
 * payload, native pointers, or source file paths.
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
 * Best-effort HDR and gain-map metadata hints.
 *
 * These flags are conservative. They are true only when the parsed metadata
 * directory names, tag names, or descriptions contain recognizable HDR or
 * gain-map terms.
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
 * Bounded diagnostic metadata for internal observability.
 *
 * Diagnostics are opt-in through [ImageMetadataReadOptions.includeDiagnosticTags].
 * Tag values are truncated and GPS directories are omitted when sensitive
 * metadata stripping is enabled.
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
 * Privacy-aware image metadata extraction report.
 *
 * The default report is suitable for public DTOs: it contains normalized EXIF,
 * dimensions, orientation, boolean presence flags, small ICC/HDR summaries, and
 * no raw metadata blobs. Use [withoutSensitiveMetadata] before exposing a report
 * if it was built with [ImageMetadataReadOptions.stripSensitiveMetadata] set to
 * `false`.
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
) : Serializable {

    val hasAnyMetadata: Boolean
        get() = this != EMPTY

    val hasSensitiveMetadata: Boolean
        get() = exif.hasGps

    fun withoutSensitiveMetadata(): ImageMetadataReport =
        copy(exif = exif.withoutGps())

    companion object {
        val EMPTY = ImageMetadataReport()

        private const val serialVersionUID: Long = 1L
    }
}

private val metadataLog = KotlinLogging.logger(ImageMetadataReport::class)

/**
 * Adds sanitized backend header fields to an existing metadata report.
 *
 * This is intended for optional backend adapters, such as libvips-based
 * readers, that can expose small header facts without coupling this module to a
 * native runtime. Raw paths, native pointers, location fields, and unbounded
 * blobs are filtered out before the diagnostic directory is added.
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
 * Reads a privacy-aware metadata report from encoded image bytes.
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
 * Reads a privacy-aware metadata report from a [File].
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
 * Reads a privacy-aware metadata report from a [Path].
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
 * Reads a privacy-aware metadata report from an [InputStream].
 *
 * The stream remains caller-owned and is not closed by this function.
 */
fun InputStream.readImageMetadataReport(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport =
    readImageMetadataReport(readBoundedBytes(options.maxBytes), options)

/**
 * Reads a metadata report from a [File] on [Dispatchers.IO].
 */
suspend fun File.suspendReadImageMetadataReport(
    options: ImageMetadataReadOptions = ImageMetadataReadOptions(),
): ImageMetadataReport =
    withContext(Dispatchers.IO) { readImageMetadataReport(options) }

/**
 * Reads a metadata report from a [Path] on [Dispatchers.IO].
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
