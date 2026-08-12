package io.bluetape4k.images.privacy

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import io.bluetape4k.images.moderation.SensitiveCoordinateSpace
import io.bluetape4k.images.moderation.SensitiveRegion
import io.bluetape4k.images.moderation.SensitiveRegionGeometry
import io.bluetape4k.images.thumbnail.ThumbnailCrop
import io.bluetape4k.images.thumbnail.ThumbnailSize
import io.bluetape4k.images.transforms.SaliencyStrategy
import io.bluetape4k.support.requireNotBlank
import java.io.Serializable
import java.util.Collections
import java.util.LinkedHashSet

/** JSON/Java 직렬화에서 사용하는 안정적인 출력 포맷 식별자입니다. */
enum class PrivacyDerivativeFormatId {
    JPEG,
    PNG,
}

/** JSON wire contract에서 사용하는 좌표계 식별자입니다. */
enum class PrivacyWireCoordinateSpaceId {
    NORMALIZED,
    PIXEL,
}

/** JSON wire contract에서 사용하는 redaction mode 식별자입니다. */
enum class PrivacyWireRedactionModeId {
    SOLID,
    BLUR,
}

/** JSON wire contract에서 사용하는 metadata category 식별자입니다. */
enum class PrivacyWireMetadataCategoryId {
    EXIF,
    XMP,
    IPTC,
    ICC,
    GPS,
    ORIENTATION,
}

/** JSON wire contract에서 사용하는 derivative action 식별자입니다. */
enum class PrivacyWireDerivativeActionId {
    STRIP_METADATA,
    REMOVE_GPS,
    NORMALIZE_ORIENTATION,
    THUMBNAIL,
    REDACT,
    ENCODED,
}

/** JSON wire contract에서 사용하는 실패 stage 식별자입니다. */
enum class PrivacyWireFailureStageId {
    VALIDATION,
    LOAD,
    TRANSFORM,
    WRITE,
    VERIFY,
}

/** 제한된 batch 실패 분류입니다. 원본 Throwable과 message는 보존하지 않습니다. */
enum class PrivacyDerivativeFailureCode {
    VALIDATION,
    LOAD,
    TRANSFORM,
    WRITE,
    VERIFY,
    UNKNOWN,
}

/** snapshot으로 복원할 수 있는 thumbnail crop 종류입니다. */
enum class PrivacyThumbnailCropId {
    FIT,
    SMART_SOBEL_ENERGY,
}

/** snapshot용 thumbnail 크기입니다. */
data class PrivacyThumbnailSizeSnapshot(
    val width: Int,
    val height: Int,
    val suffix: String,
) : Serializable {
    init {
        require(width > 0) { "thumbnail width must be positive" }
        require(height > 0) { "thumbnail height must be positive" }
        suffix.requireNotBlank("suffix")
    }

    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        require(width > 0 && height > 0 && suffix.isNotBlank()) { "Invalid thumbnail snapshot" }
    }

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** rectangle redaction의 wire snapshot입니다. */
data class PrivacyRedactionSnapshot(
    val regionId: String?,
    val coordinateSpace: PrivacyWireCoordinateSpaceId,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val mode: PrivacyWireRedactionModeId,
    val maskColorArgb: Int,
    val maskOpacity: Double,
) : Serializable {
    init {
        regionId.requireSafePrivacySourceId("regionId")
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite()) {
            "redaction coordinates must be finite"
        }
        require(width > 0.0 && height > 0.0) { "redaction dimensions must be positive" }
        require(maskOpacity.isFinite() && maskOpacity in 0.0..1.0) {
            "maskOpacity must be in 0.0..1.0"
        }
        if (coordinateSpace == PrivacyWireCoordinateSpaceId.NORMALIZED) {
            require(x >= 0.0 && y >= 0.0 && x + width <= 1.0 && y + height <= 1.0) {
                "normalized redaction must fit in 0.0..1.0"
            }
        } else {
            require(x >= 0.0 && y >= 0.0) { "pixel redaction coordinates must be non-negative" }
        }
    }

    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        regionId.requireSafePrivacySourceId("regionId")
        require(coordinateSpace != null && mode != null) { "Invalid redaction snapshot" }
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite()) {
            "Invalid redaction coordinates"
        }
        require(width > 0.0 && height > 0.0) { "Invalid redaction dimensions" }
        require(maskOpacity.isFinite() && maskOpacity in 0.0..1.0) { "Invalid redaction snapshot" }
        if (coordinateSpace == PrivacyWireCoordinateSpaceId.NORMALIZED) {
            require(x >= 0.0 && y >= 0.0 && x + width <= 1.0 && y + height <= 1.0) {
                "Invalid normalized redaction"
            }
        } else {
            require(x >= 0.0 && y >= 0.0) { "Invalid pixel redaction" }
        }
    }

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** privacy derivative 옵션의 wire snapshot입니다. */
class PrivacyDerivativeOptionsSnapshot(
    val stripMetadata: Boolean,
    val removeGps: Boolean,
    val normalizeOrientation: Boolean,
    val maxPixels: Long,
    val maxSide: Int?,
    val thumbnailSize: PrivacyThumbnailSizeSnapshot?,
    val thumbnailCrop: PrivacyThumbnailCropId,
    val outputFormat: PrivacyDerivativeFormatId,
    redactions: List<PrivacyRedactionSnapshot>,
) : Serializable {
    private var storedRedactions: List<PrivacyRedactionSnapshot> = immutableList(redactions)

    val redactions: List<PrivacyRedactionSnapshot>
        get() = storedRedactions

    init {
        require(maxPixels > 0) { "maxPixels must be positive" }
        require(maxSide == null || maxSide > 0) { "maxSide must be positive" }
    }

    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        require(thumbnailSize == null || (thumbnailSize.width > 0 && thumbnailSize.height > 0)) {
            "Invalid thumbnail size"
        }
        require(thumbnailCrop != null && outputFormat != null) { "Invalid options snapshot" }
        require(maxPixels > 0 && (maxSide == null || maxSide > 0)) { "Invalid options snapshot" }
        requireNotNull(storedRedactions) { "Invalid options redactions" }
        require(storedRedactions.size <= PRIVACY_DERIVATIVE_DEFAULT_MAX_REDACTIONS) {
            "Too many redactions"
        }
        storedRedactions = immutableList(storedRedactions)
    }

    override fun equals(other: Any?): Boolean =
        other is PrivacyDerivativeOptionsSnapshot &&
            stripMetadata == other.stripMetadata && removeGps == other.removeGps &&
            normalizeOrientation == other.normalizeOrientation && maxPixels == other.maxPixels &&
            maxSide == other.maxSide && thumbnailSize == other.thumbnailSize &&
            thumbnailCrop == other.thumbnailCrop && outputFormat == other.outputFormat &&
            redactions == other.redactions

    override fun hashCode(): Int = listOf(
        stripMetadata, removeGps, normalizeOrientation, maxPixels, maxSide, thumbnailSize,
        thumbnailCrop, outputFormat, redactions,
    ).hashCode()

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** 이미지 크기만 보존하는 wire snapshot입니다. */
data class PrivacyImageDimensionsSnapshot(
    val width: Int,
    val height: Int,
) : Serializable {
    init {
        require(width > 0 && height > 0) { "image dimensions must be positive" }
    }

    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        require(width > 0 && height > 0) { "Invalid image dimensions snapshot" }
    }

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** 적용된 redaction의 제한된 결과 snapshot입니다. */
data class PrivacyAppliedRedactionSnapshot(
    val regionId: String?,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) : Serializable {
    init {
        regionId.requireSafePrivacySourceId("regionId")
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite()) {
            "applied redaction coordinates must be finite"
        }
        require(x >= 0.0 && y >= 0.0 && width > 0.0 && height > 0.0) {
            "applied redaction bounds are invalid"
        }
    }

    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        regionId.requireSafePrivacySourceId("regionId")
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite()) {
            "Invalid applied redaction coordinates"
        }
        require(x >= 0.0 && y >= 0.0 && width > 0.0 && height > 0.0) {
            "Invalid applied redaction bounds"
        }
    }

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** metadata 검증 결과의 wire snapshot입니다. */
class PrivacyMetadataVerificationSnapshot(
    requested: Set<PrivacyWireMetadataCategoryId>,
    sourcePresent: Set<PrivacyWireMetadataCategoryId>,
    remaining: Set<PrivacyWireMetadataCategoryId>,
    val verified: Boolean,
) : Serializable {
    private var storedRequested: Set<PrivacyWireMetadataCategoryId> = immutableEnumSet(requested)
    private var storedSourcePresent: Set<PrivacyWireMetadataCategoryId> = immutableEnumSet(sourcePresent)
    private var storedRemaining: Set<PrivacyWireMetadataCategoryId> = immutableEnumSet(remaining)

    val requested: Set<PrivacyWireMetadataCategoryId>
        get() = storedRequested
    val sourcePresent: Set<PrivacyWireMetadataCategoryId>
        get() = storedSourcePresent
    val remaining: Set<PrivacyWireMetadataCategoryId>
        get() = storedRemaining

    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        requireNotNull(storedRequested) { "Invalid requested metadata" }
        requireNotNull(storedSourcePresent) { "Invalid source metadata" }
        requireNotNull(storedRemaining) { "Invalid remaining metadata" }
        require(storedRequested.size <= PRIVACY_DERIVATIVE_DEFAULT_MAX_METADATA_ENTRIES)
        require(storedSourcePresent.size <= PRIVACY_DERIVATIVE_DEFAULT_MAX_METADATA_ENTRIES)
        require(storedRemaining.size <= PRIVACY_DERIVATIVE_DEFAULT_MAX_METADATA_ENTRIES)
        storedRequested = immutableEnumSet(storedRequested)
        storedSourcePresent = immutableEnumSet(storedSourcePresent)
        storedRemaining = immutableEnumSet(storedRemaining)
    }

    override fun equals(other: Any?): Boolean =
        other is PrivacyMetadataVerificationSnapshot && requested == other.requested &&
            sourcePresent == other.sourcePresent && remaining == other.remaining && verified == other.verified

    override fun hashCode(): Int = listOf(requested, sourcePresent, remaining, verified).hashCode()

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** report failure의 안정적인 wire snapshot입니다. */
data class PrivacyDerivativeFailureSnapshot(
    val stage: PrivacyWireFailureStageId,
    val code: PrivacyDerivativeFailureCode,
) : Serializable {
    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        require(stage != null && code != null) { "Invalid derivative failure snapshot" }
    }

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** 단일 derivative report의 wire snapshot입니다. */
class PrivacyDerivativeReportSnapshot(
    val sourceId: String?,
    val sourceDimensions: PrivacyImageDimensionsSnapshot,
    val outputDimensions: PrivacyImageDimensionsSnapshot,
    strippedMetadataCategories: Set<PrivacyWireMetadataCategoryId>,
    appliedActions: List<PrivacyWireDerivativeActionId>,
    redactions: List<PrivacyAppliedRedactionSnapshot>,
    failures: List<PrivacyDerivativeFailureSnapshot>,
    val elapsedMillis: Long,
    val metadataVerification: PrivacyMetadataVerificationSnapshot,
) : Serializable {
    private var storedStrippedMetadataCategories: Set<PrivacyWireMetadataCategoryId> =
        immutableEnumSet(strippedMetadataCategories)
    private var storedAppliedActions: List<PrivacyWireDerivativeActionId> = immutableList(appliedActions)
    private var storedRedactions: List<PrivacyAppliedRedactionSnapshot> = immutableList(redactions)
    private var storedFailures: List<PrivacyDerivativeFailureSnapshot> = immutableList(failures)

    val strippedMetadataCategories: Set<PrivacyWireMetadataCategoryId>
        get() = storedStrippedMetadataCategories
    val appliedActions: List<PrivacyWireDerivativeActionId>
        get() = storedAppliedActions
    val redactions: List<PrivacyAppliedRedactionSnapshot>
        get() = storedRedactions
    val failures: List<PrivacyDerivativeFailureSnapshot>
        get() = storedFailures

    init {
        sourceId.requireSafePrivacySourceId("sourceId")
        require(elapsedMillis >= 0L) { "elapsedMillis must be non-negative" }
    }

    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        sourceId.requireSafePrivacySourceId("sourceId")
        requireNotNull(sourceDimensions) { "Invalid source dimensions" }
        requireNotNull(outputDimensions) { "Invalid output dimensions" }
        requireNotNull(metadataVerification) { "Invalid metadata verification" }
        requireNotNull(storedStrippedMetadataCategories) { "Invalid stripped metadata" }
        requireNotNull(storedAppliedActions) { "Invalid applied actions" }
        requireNotNull(storedRedactions) { "Invalid applied redactions" }
        requireNotNull(storedFailures) { "Invalid failures" }
        require(elapsedMillis >= 0L) { "Invalid report snapshot" }
        require(storedStrippedMetadataCategories.size <= PRIVACY_DERIVATIVE_DEFAULT_MAX_METADATA_ENTRIES)
        require(storedAppliedActions.size <= PRIVACY_DERIVATIVE_DEFAULT_MAX_ACTIONS)
        require(storedRedactions.size <= PRIVACY_DERIVATIVE_DEFAULT_MAX_REDACTIONS)
        require(storedFailures.size <= PRIVACY_DERIVATIVE_DEFAULT_MAX_FAILURES)
        storedStrippedMetadataCategories = immutableEnumSet(storedStrippedMetadataCategories)
        storedAppliedActions = immutableList(storedAppliedActions)
        storedRedactions = immutableList(storedRedactions)
        storedFailures = immutableList(storedFailures)
    }

    override fun equals(other: Any?): Boolean =
        other is PrivacyDerivativeReportSnapshot && sourceId == other.sourceId &&
            sourceDimensions == other.sourceDimensions && outputDimensions == other.outputDimensions &&
            strippedMetadataCategories == other.strippedMetadataCategories && appliedActions == other.appliedActions &&
            redactions == other.redactions && failures == other.failures && elapsedMillis == other.elapsedMillis &&
            metadataVerification == other.metadataVerification

    override fun hashCode(): Int = listOf(
        sourceId, sourceDimensions, outputDimensions, strippedMetadataCategories, appliedActions,
        redactions, failures, elapsedMillis, metadataVerification,
    ).hashCode()

    override fun toString(): String =
        "PrivacyDerivativeReportSnapshot(sourceId=$sourceId, sourceDimensions=$sourceDimensions, " +
            "outputDimensions=$outputDimensions, strippedMetadataCategories=$strippedMetadataCategories, " +
            "appliedActions=$appliedActions, redactions=$redactions, failures=$failures, " +
            "elapsedMillis=$elapsedMillis, metadataVerification=$metadataVerification)"

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** 성공 payload의 Java/JSON value snapshot입니다. */
class PrivacyDerivativePayload(
    @param:JsonProperty("encodedBytes")
    @param:JsonAlias("bytes")
    encodedBytes: ByteArray,
    val report: PrivacyDerivativeReportSnapshot,
) : Serializable {
    private val storedBytes: ByteArray = encodedBytes.copyOf()

    /** 저장된 payload를 caller가 변경할 수 없도록 새 배열로 반환합니다. */
    @get:JsonProperty("encodedBytes")
    val bytes: ByteArray
        get() = storedBytes.copyOf()

    override fun equals(other: Any?): Boolean =
        other is PrivacyDerivativePayload &&
            storedBytes.contentEquals(other.storedBytes) &&
            report == other.report

    override fun hashCode(): Int = 31 * storedBytes.contentHashCode() + report.hashCode()

    override fun toString(): String =
        "PrivacyDerivativePayload(bytes=${storedBytes.size}, report=$report)"

    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        requireNotNull(storedBytes) { "Invalid encoded payload" }
        requireNotNull(report) { "Invalid payload report" }
        require(storedBytes.size <= PRIVACY_DERIVATIVE_DEFAULT_MAX_PAYLOAD_BYTES) {
            "encoded payload exceeds the supported Java serialization limit"
        }
    }

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** 성공 payload 또는 제한된 failure 중 정확히 하나를 보존하는 batch snapshot입니다. */
data class PrivacyDerivativeBatchSnapshot(
    val sourceId: String,
    val payload: PrivacyDerivativePayload?,
    val failure: PrivacyDerivativeFailureSnapshot?,
) : Serializable {
    init {
        sourceId.requireSafePrivacySourceId("sourceId")
        require((payload == null) xor (failure == null)) {
            "exactly one of payload and failure must be present"
        }
    }

    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
        require(!sourceId.isNullOrBlank()) { "Invalid batch source id" }
        sourceId.requireSafePrivacySourceId("sourceId")
        require((payload == null) xor (failure == null)) {
            "Invalid batch snapshot"
        }
    }

    private companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

/** runtime 옵션을 wire snapshot으로 변환합니다. custom writer는 거부합니다. */
fun PrivacyDerivativeOptions.toSnapshot(): PrivacyDerivativeOptionsSnapshot {
    val format = when {
        this.outputFormat === PrivacyDerivativeFormat.Jpeg -> PrivacyDerivativeFormatId.JPEG
        this.outputFormat === PrivacyDerivativeFormat.Png -> PrivacyDerivativeFormatId.PNG
        else -> throw IllegalArgumentException("Only built-in JPEG and PNG formats can be snapshotted")
    }
    val crop = when (val value = thumbnailCrop) {
        ThumbnailCrop.Fit -> PrivacyThumbnailCropId.FIT
        is ThumbnailCrop.Smart -> when (value.strategy) {
            SaliencyStrategy.SobelEnergy -> PrivacyThumbnailCropId.SMART_SOBEL_ENERGY
        }
    }
    return PrivacyDerivativeOptionsSnapshot(
        stripMetadata = stripMetadata,
        removeGps = removeGps,
        normalizeOrientation = normalizeOrientation,
        maxPixels = maxPixels,
        maxSide = maxSide,
        thumbnailSize = thumbnailSize?.let { PrivacyThumbnailSizeSnapshot(it.width, it.height, it.suffix) },
        thumbnailCrop = crop,
        outputFormat = format,
        redactions = redactions.map { it.toSnapshot() },
    )
}

/** wire snapshot을 built-in runtime 옵션으로 복원합니다. */
fun PrivacyDerivativeOptionsSnapshot.toOptions(): PrivacyDerivativeOptions =
    PrivacyDerivativeOptions(
        stripMetadata = stripMetadata,
        removeGps = removeGps,
        normalizeOrientation = normalizeOrientation,
        maxPixels = maxPixels,
        maxSide = maxSide,
        thumbnailSize = thumbnailSize?.let { ThumbnailSize(it.width, it.height, it.suffix) },
        thumbnailCrop = when (thumbnailCrop) {
            PrivacyThumbnailCropId.FIT -> ThumbnailCrop.Fit
            PrivacyThumbnailCropId.SMART_SOBEL_ENERGY -> ThumbnailCrop.Smart(SaliencyStrategy.SobelEnergy)
        },
        outputFormat = when (outputFormat) {
            PrivacyDerivativeFormatId.JPEG -> PrivacyDerivativeFormat.Jpeg
            PrivacyDerivativeFormatId.PNG -> PrivacyDerivativeFormat.Png
        },
        redactions = redactions.map { it.toRuntimeRedaction() },
    )

/** runtime result를 source path 없이 payload snapshot으로 변환합니다. */
fun PrivacyDerivativeResult.toPayload(sourceId: String? = null): PrivacyDerivativePayload =
    PrivacyDerivativePayload(
        encodedBytes = bytes,
        report = report.toSnapshot(sourceId),
    )

/** runtime batch result를 opaque source id를 사용하는 snapshot으로 변환합니다. */
fun PrivacyDerivativeBatchResult.toSnapshot(sourceId: String): PrivacyDerivativeBatchSnapshot =
    when (this) {
        is PrivacyDerivativeBatchResult.Success ->
            PrivacyDerivativeBatchSnapshot(sourceId, result.toPayload(sourceId), null)

        is PrivacyDerivativeBatchResult.Failure ->
            PrivacyDerivativeBatchSnapshot(
                sourceId = sourceId,
                payload = null,
                failure = PrivacyDerivativeFailureSnapshot(
                    stage = stage.toWireId(),
                    code = stage.toFailureCode(),
                ),
            )
    }

private fun PrivacyRedaction.toSnapshot(): PrivacyRedactionSnapshot {
    val geometry = region.geometry as? SensitiveRegionGeometry.Rectangle
        ?: throw IllegalArgumentException("Only rectangle redactions can be snapshotted")
    return PrivacyRedactionSnapshot(
        regionId = region.id,
        coordinateSpace = geometry.coordinateSpace.toWireId(),
        x = geometry.x,
        y = geometry.y,
        width = geometry.width,
        height = geometry.height,
        mode = when (mode) {
            PrivacyRedactionMode.SOLID_MASK -> PrivacyWireRedactionModeId.SOLID
        },
        maskColorArgb = maskColorArgb,
        maskOpacity = maskOpacity,
    )
}

private fun PrivacyRedactionSnapshot.toRuntimeRedaction(): PrivacyRedaction =
    PrivacyRedaction(
        region = SensitiveRegion(
            geometry = SensitiveRegionGeometry.Rectangle(
                x = x,
                y = y,
                width = width,
                height = height,
                coordinateSpace = coordinateSpace.toRuntimeSpace(),
            ),
            id = regionId,
        ),
        mode = when (mode) {
            PrivacyWireRedactionModeId.SOLID -> PrivacyRedactionMode.SOLID_MASK
            PrivacyWireRedactionModeId.BLUR ->
                throw IllegalArgumentException("BLUR redaction is not supported by the core pipeline")
        },
        maskColorArgb = maskColorArgb,
        maskOpacity = maskOpacity,
    )

private fun PrivacyDerivativeReport.toSnapshot(sourceId: String?): PrivacyDerivativeReportSnapshot =
    PrivacyDerivativeReportSnapshot(
        sourceId = sourceId,
        sourceDimensions = PrivacyImageDimensionsSnapshot(sourceDimensions.width, sourceDimensions.height),
        outputDimensions = PrivacyImageDimensionsSnapshot(outputDimensions.width, outputDimensions.height),
        strippedMetadataCategories = strippedMetadataCategories.mapToEnumSet { it.toWireId() },
        appliedActions = appliedActions.map { it.toWireId() },
        redactions = redactions.map {
            PrivacyAppliedRedactionSnapshot(
                regionId = it.regionId,
                x = it.x.toDouble(),
                y = it.y.toDouble(),
                width = it.width.toDouble(),
                height = it.height.toDouble(),
            )
        },
        failures = failures.map { PrivacyDerivativeFailureSnapshot(it.stage.toWireId(), it.stage.toFailureCode()) },
        elapsedMillis = elapsedMillis,
        metadataVerification = metadataVerification.toSnapshot(),
    )

private fun PrivacyMetadataVerification.toSnapshot(): PrivacyMetadataVerificationSnapshot =
    PrivacyMetadataVerificationSnapshot(
        requested = requested.mapToEnumSet { it.toWireId() },
        sourcePresent = sourcePresent.mapToEnumSet { it.toWireId() },
        remaining = remaining.mapToEnumSet { it.toWireId() },
        verified = verified,
    )

private fun PrivacyWireCoordinateSpaceId.toRuntimeSpace(): SensitiveCoordinateSpace =
    when (this) {
        PrivacyWireCoordinateSpaceId.NORMALIZED -> SensitiveCoordinateSpace.NORMALIZED
        PrivacyWireCoordinateSpaceId.PIXEL -> SensitiveCoordinateSpace.PIXEL
    }

private fun SensitiveCoordinateSpace.toWireId(): PrivacyWireCoordinateSpaceId =
    when (this) {
        SensitiveCoordinateSpace.NORMALIZED -> PrivacyWireCoordinateSpaceId.NORMALIZED
        SensitiveCoordinateSpace.PIXEL -> PrivacyWireCoordinateSpaceId.PIXEL
    }

private fun PrivacyMetadataCategory.toWireId(): PrivacyWireMetadataCategoryId =
    when (this) {
        PrivacyMetadataCategory.EXIF -> PrivacyWireMetadataCategoryId.EXIF
        PrivacyMetadataCategory.XMP -> PrivacyWireMetadataCategoryId.XMP
        PrivacyMetadataCategory.IPTC -> PrivacyWireMetadataCategoryId.IPTC
        PrivacyMetadataCategory.ICC -> PrivacyWireMetadataCategoryId.ICC
        PrivacyMetadataCategory.GPS -> PrivacyWireMetadataCategoryId.GPS
        PrivacyMetadataCategory.ORIENTATION -> PrivacyWireMetadataCategoryId.ORIENTATION
    }

private fun PrivacyDerivativeAction.toWireId(): PrivacyWireDerivativeActionId =
    when (this) {
        PrivacyDerivativeAction.GPS_REMOVED -> PrivacyWireDerivativeActionId.REMOVE_GPS
        PrivacyDerivativeAction.METADATA_STRIPPED -> PrivacyWireDerivativeActionId.STRIP_METADATA
        PrivacyDerivativeAction.ORIENTATION_NORMALIZED -> PrivacyWireDerivativeActionId.NORMALIZE_ORIENTATION
        PrivacyDerivativeAction.RESIZED -> PrivacyWireDerivativeActionId.THUMBNAIL
        PrivacyDerivativeAction.REDACTED -> PrivacyWireDerivativeActionId.REDACT
        PrivacyDerivativeAction.ENCODED -> PrivacyWireDerivativeActionId.ENCODED
    }

private fun PrivacyDerivativeFailureStage.toWireId(): PrivacyWireFailureStageId =
    when (this) {
        PrivacyDerivativeFailureStage.VALIDATION -> PrivacyWireFailureStageId.VALIDATION
        PrivacyDerivativeFailureStage.LOAD -> PrivacyWireFailureStageId.LOAD
        PrivacyDerivativeFailureStage.TRANSFORM -> PrivacyWireFailureStageId.TRANSFORM
        PrivacyDerivativeFailureStage.WRITE -> PrivacyWireFailureStageId.WRITE
        PrivacyDerivativeFailureStage.VERIFY -> PrivacyWireFailureStageId.VERIFY
    }

private fun PrivacyDerivativeFailureStage.toFailureCode(): PrivacyDerivativeFailureCode =
    when (this) {
        PrivacyDerivativeFailureStage.VALIDATION -> PrivacyDerivativeFailureCode.VALIDATION
        PrivacyDerivativeFailureStage.LOAD -> PrivacyDerivativeFailureCode.LOAD
        PrivacyDerivativeFailureStage.TRANSFORM -> PrivacyDerivativeFailureCode.TRANSFORM
        PrivacyDerivativeFailureStage.WRITE -> PrivacyDerivativeFailureCode.WRITE
        PrivacyDerivativeFailureStage.VERIFY -> PrivacyDerivativeFailureCode.VERIFY
    }

private fun <T : Enum<T>, R : Enum<R>> Iterable<T>.mapToEnumSet(transform: (T) -> R): Set<R> =
    immutableEnumSet(map(transform))

private fun <T> immutableList(values: Iterable<T>): List<T> =
    java.util.List.copyOf(values.toList())

private fun <T : Enum<T>> immutableEnumSet(values: Iterable<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values.toList().sortedBy { it.name }))

private fun String?.requireSafePrivacySourceId(name: String) {
    if (this == null) return
    require(isNotEmpty()) { "$name must not be empty" }
    require(length <= PRIVACY_DERIVATIVE_MAX_SOURCE_ID_LENGTH) { "$name is too long" }
    require(indexOf('/') < 0 && indexOf('\\') < 0) { "$name must be an opaque identifier" }
    require(none(Char::isISOControl)) { "$name must not contain control characters" }
    require(!startsWith("/") && !startsWith("\\") && !matches(Regex("^[A-Za-z]:.*"))) {
        "$name must not be an absolute path"
    }
}

private const val PRIVACY_DERIVATIVE_MAX_SOURCE_ID_LENGTH = 4 * 1024
internal const val PRIVACY_DERIVATIVE_DEFAULT_MAX_PAYLOAD_BYTES = 64 * 1024 * 1024
private const val PRIVACY_DERIVATIVE_DEFAULT_MAX_REDACTIONS = 1_024
private const val PRIVACY_DERIVATIVE_DEFAULT_MAX_ACTIONS = 256
private const val PRIVACY_DERIVATIVE_DEFAULT_MAX_FAILURES = 256
private const val PRIVACY_DERIVATIVE_DEFAULT_MAX_METADATA_ENTRIES = 256
