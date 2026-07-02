package io.bluetape4k.images.examples.basic

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.moderation.SensitiveContentCategory
import io.bluetape4k.images.moderation.SensitiveContentDetection
import io.bluetape4k.images.moderation.SensitiveContentSeverity
import io.bluetape4k.images.moderation.SensitiveCoordinateSpace
import io.bluetape4k.images.moderation.SensitiveModerationDecision
import io.bluetape4k.images.moderation.SensitiveModerationPolicy
import io.bluetape4k.images.moderation.SensitiveModerationRule
import io.bluetape4k.images.moderation.SensitivePoint
import io.bluetape4k.images.moderation.SensitiveRasterMask
import io.bluetape4k.images.moderation.SensitiveRegion
import io.bluetape4k.images.moderation.SensitiveRegionGeometry
import io.bluetape4k.images.moderation.SensitiveTreatmentAction
import io.bluetape4k.images.moderation.SensitiveTreatmentLevel
import io.bluetape4k.images.moderation.SensitiveTreatmentParameters
import io.bluetape4k.images.privacy.PrivacyDerivativeOptions
import io.bluetape4k.images.privacy.PrivacyDerivativeResult
import io.bluetape4k.images.privacy.PrivacyRedaction
import io.bluetape4k.images.privacy.suspendPrivacyDerivative
import io.bluetape4k.images.suspendLoadImage
import io.bluetape4k.images.thumbnail.ThumbnailSize
import io.bluetape4k.support.requireNotBlank
import java.awt.Color
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path

fun main(args: Array<String>) = runBlocking {
    val outputDirectory = args.firstOrNull()?.let(::Path)
        ?: Path("build/tmp/sensitive-content-workflow")
    val result = SensitiveContentWorkflowQuickstart.generate(outputDirectory)

    println("Generated sensitive moderation workflow example under ${outputDirectory.toAbsolutePath().normalize()}")
    println("${result.previewOutput.fileName}: ${result.preview.report.outputDimensions.width}x${result.preview.report.outputDimensions.height}")
    println("${result.reportOutput.fileName}: ${result.actionPlans.size} policy action plans")
}

object SensitiveContentWorkflowQuickstart {

    suspend fun generate(
        outputDirectory: Path = Path("build/tmp/sensitive-content-workflow"),
    ): SensitiveWorkflowExampleResult {
        Files.createDirectories(outputDirectory)

        val source = resourcePath(CAFE_IMAGE)
        val image = suspendLoadImage(source)
        val detections = DeterministicSensitiveDetector.detect(image)
        val moderationReport = examplePolicy().evaluate(detections)
        val actionPlans = moderationReport.decisions.map { it.toActionPlan() }

        val preview = image.suspendPrivacyDerivative(
            options = PrivacyDerivativeOptions(
                thumbnailSize = ThumbnailSize(width = 640, height = 480, suffix = "public"),
                redactions = actionPlans.mapNotNull { it.toPrivacyRedaction() },
            ),
            source = source,
        )
        val previewOutput = outputDirectory.resolve(PREVIEW_FILE)
        Files.write(previewOutput, preview.bytes)

        val reportOutput = outputDirectory.resolve(REPORT_FILE)
        Files.writeString(
            reportOutput,
            buildModerationSummary(detections, actionPlans, preview),
        )

        return SensitiveWorkflowExampleResult(
            source = source,
            previewOutput = previewOutput,
            reportOutput = reportOutput,
            detections = detections,
            actionPlans = actionPlans,
            preview = preview,
        )
    }

    private fun examplePolicy(): SensitiveModerationPolicy =
        SensitiveModerationPolicy.failClosed(
            rules = listOf(
                SensitiveModerationRule(
                    id = "allow-low-suggestive",
                    action = SensitiveTreatmentAction.ALLOW,
                    level = SensitiveTreatmentLevel.LOW,
                    reason = "Low-severity suggestive content stays visible with audit logging",
                    categories = setOf(SensitiveContentCategory.SUGGESTIVE),
                    minimumSeverity = SensitiveContentSeverity.LOW,
                    minimumConfidence = 0.50,
                ),
                SensitiveModerationRule(
                    id = "mosaic-explicit",
                    action = SensitiveTreatmentAction.MOSAIC,
                    level = SensitiveTreatmentLevel.MEDIUM,
                    reason = "Medium explicit-content confidence uses mosaic treatment",
                    categories = setOf(SensitiveContentCategory.EXPLICIT_NUDITY),
                    minimumSeverity = SensitiveContentSeverity.MEDIUM,
                    minimumConfidence = 0.70,
                    parameters = SensitiveTreatmentParameters(mosaicBlockSize = 18),
                ),
                SensitiveModerationRule(
                    id = "blur-sensitive-text",
                    action = SensitiveTreatmentAction.BLUR,
                    level = SensitiveTreatmentLevel.MEDIUM,
                    reason = "Detected sensitive text should be blurred before public previews",
                    categories = setOf(SensitiveContentCategory.SENSITIVE_TEXT),
                    minimumSeverity = SensitiveContentSeverity.MEDIUM,
                    minimumConfidence = 0.65,
                    parameters = SensitiveTreatmentParameters(blurRadius = 6.0, blurSigma = 2.0),
                ),
                SensitiveModerationRule(
                    id = "mask-minor-safety",
                    action = SensitiveTreatmentAction.SOLID_MASK,
                    level = SensitiveTreatmentLevel.HIGH,
                    reason = "Minor-safety regions need an opaque mask",
                    categories = setOf(SensitiveContentCategory.MINOR_SAFETY),
                    minimumSeverity = SensitiveContentSeverity.HIGH,
                    minimumConfidence = 0.80,
                    parameters = SensitiveTreatmentParameters(maskOpacity = 0.95, maskStyle = "solid"),
                ),
                SensitiveModerationRule(
                    id = "review-violence",
                    action = SensitiveTreatmentAction.MANUAL_REVIEW,
                    level = SensitiveTreatmentLevel.HIGH,
                    reason = "Violence detections require a human reviewer before publication",
                    categories = setOf(SensitiveContentCategory.VIOLENCE),
                    minimumSeverity = SensitiveContentSeverity.MEDIUM,
                    minimumConfidence = 0.60,
                    parameters = SensitiveTreatmentParameters(reviewPriority = 70),
                ),
                SensitiveModerationRule(
                    id = "drop-weapon-preview",
                    action = SensitiveTreatmentAction.DROP,
                    level = SensitiveTreatmentLevel.CRITICAL,
                    reason = "Weapon-like regions should drop the derivative from automated feeds",
                    categories = setOf(SensitiveContentCategory.WEAPON),
                    minimumSeverity = SensitiveContentSeverity.CRITICAL,
                    minimumConfidence = 0.85,
                    parameters = SensitiveTreatmentParameters(rejectReason = "weapon policy"),
                ),
                SensitiveModerationRule(
                    id = "reject-hate-symbol",
                    action = SensitiveTreatmentAction.REJECT,
                    level = SensitiveTreatmentLevel.CRITICAL,
                    reason = "High-confidence hate-symbol detections are rejected",
                    categories = setOf(SensitiveContentCategory.HATE_SYMBOL),
                    minimumSeverity = SensitiveContentSeverity.HIGH,
                    minimumConfidence = 0.85,
                    parameters = SensitiveTreatmentParameters(rejectReason = "hate-symbol policy"),
                ),
            ),
            fallbackReason = "Unknown sensitive category is quarantined until reviewed",
        )

    private fun SensitiveModerationDecision.toActionPlan(): SensitiveWorkflowActionPlan {
        val region = detection.region
        return SensitiveWorkflowActionPlan(
            detectionLabel = detection.label,
            category = detection.category,
            severity = detection.severity,
            confidence = detection.confidence,
            regionKind = region?.geometry?.regionKind() ?: SensitiveWorkflowRegionKind.NONE,
            action = action,
            level = level,
            intensity = parameters.intensitySummary(action),
            reason = reason,
            renderableInCoreDerivative = region?.geometry is SensitiveRegionGeometry.Rectangle &&
                action in CORE_DERIVATIVE_REDACTION_ACTIONS,
            region = region,
        )
    }

    private fun SensitiveTreatmentParameters.intensitySummary(action: SensitiveTreatmentAction): String =
        when (action) {
            SensitiveTreatmentAction.MOSAIC -> "mosaicBlockSize=${mosaicBlockSize ?: "adapter-default"}"
            SensitiveTreatmentAction.BLUR -> "blurRadius=${blurRadius ?: "adapter-default"}"
            SensitiveTreatmentAction.SOLID_MASK -> "maskOpacity=${maskOpacity ?: "adapter-default"}"
            SensitiveTreatmentAction.MANUAL_REVIEW -> "reviewPriority=${reviewPriority ?: "normal"}"
            SensitiveTreatmentAction.DROP,
            SensitiveTreatmentAction.REJECT -> "rejectReason=${rejectReason ?: "policy"}"
            SensitiveTreatmentAction.QUARANTINE -> "hold=manual-triage"
            SensitiveTreatmentAction.ALLOW -> "none"
        }

    private fun SensitiveWorkflowActionPlan.toPrivacyRedaction(): PrivacyRedaction? {
        val region = region ?: return null
        if (!renderableInCoreDerivative) {
            return null
        }
        return PrivacyRedaction(
            region = region,
            maskColorArgb = redactionColor(action).rgb,
            maskOpacity = when (level) {
                SensitiveTreatmentLevel.LOW -> 0.35
                SensitiveTreatmentLevel.MEDIUM -> 0.55
                SensitiveTreatmentLevel.HIGH -> 0.80
                SensitiveTreatmentLevel.CRITICAL -> 1.0
            },
        )
    }

    private fun SensitiveRegionGeometry.regionKind(): SensitiveWorkflowRegionKind =
        when (this) {
            is SensitiveRegionGeometry.Rectangle -> SensitiveWorkflowRegionKind.RECTANGLE
            is SensitiveRegionGeometry.Polygon -> SensitiveWorkflowRegionKind.POLYGON
            is SensitiveRegionGeometry.Polyline -> SensitiveWorkflowRegionKind.POLYLINE
            is SensitiveRegionGeometry.RasterMask -> SensitiveWorkflowRegionKind.RASTER_MASK
        }

    private fun redactionColor(action: SensitiveTreatmentAction): Color =
        when (action) {
            SensitiveTreatmentAction.MOSAIC -> Color(35, 96, 162)
            SensitiveTreatmentAction.BLUR -> Color(120, 120, 120)
            SensitiveTreatmentAction.SOLID_MASK -> Color.BLACK
            else -> Color.DARK_GRAY
        }

    private fun buildModerationSummary(
        detections: List<SensitiveContentDetection>,
        actionPlans: List<SensitiveWorkflowActionPlan>,
        preview: PrivacyDerivativeResult,
    ): String =
        buildString {
            appendLine("Sensitive content moderation workflow example")
            appendLine()
            appendLine("Detector: deterministic fake output; no model runtime or weights are bundled.")
            appendLine("Detections: ${detections.size}")
            appendLine("Preview dimensions: ${preview.report.outputDimensions.width}x${preview.report.outputDimensions.height}")
            appendLine("Derivative actions: ${preview.report.appliedActions.joinToString()}")
            appendLine()
            appendLine("Policy action plans:")
            actionPlans.forEachIndexed { index, plan ->
                appendLine(
                    "${index + 1}. ${plan.detectionLabel}: ${plan.category}/${plan.severity} " +
                        "confidence=${String.format(Locale.ROOT, "%.2f", plan.confidence)} region=${plan.regionKind} " +
                        "action=${plan.action} level=${plan.level} intensity=${plan.intensity} " +
                        "coreDerivative=${plan.renderableInCoreDerivative} reason=${plan.reason}"
                )
            }
            appendLine()
            appendLine("Caveat: a real detector adapter can return the same SensitiveContentDetection model later,")
            appendLine("but thresholds still need false-positive, false-negative, drift, and review-loop monitoring.")
        }

    private fun resourcePath(resourceName: String): Path {
        val resource = requireNotNull(
            SensitiveContentWorkflowQuickstart::class.java.classLoader.getResource(resourceName)
        ) {
            "Example resource is missing: $resourceName"
        }
        require(resource.protocol == "file") {
            "Example resource must be available as a file for path-based loading: $resourceName"
        }
        return Paths.get(resource.toURI())
    }

    private const val CAFE_IMAGE = "images/cafe.jpg"
    private const val PREVIEW_FILE = "06-sensitive-moderation-preview.jpg"
    private const val REPORT_FILE = "06-sensitive-moderation-report.txt"

    private val CORE_DERIVATIVE_REDACTION_ACTIONS = setOf(
        SensitiveTreatmentAction.MOSAIC,
        SensitiveTreatmentAction.BLUR,
        SensitiveTreatmentAction.SOLID_MASK,
    )
}

private object DeterministicSensitiveDetector {

    fun detect(image: ImmutableImage): List<SensitiveContentDetection> {
        image.width.requirePositive("image.width")
        image.height.requirePositive("image.height")

        return listOf(
            detection(
                label = "suggestive-low",
                category = SensitiveContentCategory.SUGGESTIVE,
                severity = SensitiveContentSeverity.LOW,
                confidence = 0.58,
                region = rectangle("r-allow", x = 0.03, y = 0.04, width = 0.12, height = 0.14),
            ),
            detection(
                label = "explicit-medium",
                category = SensitiveContentCategory.EXPLICIT_NUDITY,
                severity = SensitiveContentSeverity.MEDIUM,
                confidence = 0.82,
                region = rectangle("r-mosaic", x = 0.20, y = 0.18, width = 0.18, height = 0.22),
            ),
            detection(
                label = "pii-text-line",
                category = SensitiveContentCategory.SENSITIVE_TEXT,
                severity = SensitiveContentSeverity.MEDIUM,
                confidence = 0.88,
                region = polyline("p-blur"),
            ),
            detection(
                label = "minor-face",
                category = SensitiveContentCategory.MINOR_SAFETY,
                severity = SensitiveContentSeverity.HIGH,
                confidence = 0.91,
                region = rectangle("r-mask", x = 0.48, y = 0.12, width = 0.19, height = 0.26),
            ),
            detection(
                label = "violence-contour",
                category = SensitiveContentCategory.VIOLENCE,
                severity = SensitiveContentSeverity.MEDIUM,
                confidence = 0.73,
                region = polygon("g-review"),
            ),
            detection(
                label = "weapon-silhouette",
                category = SensitiveContentCategory.WEAPON,
                severity = SensitiveContentSeverity.CRITICAL,
                confidence = 0.89,
                region = rectangle("r-drop", x = 0.72, y = 0.52, width = 0.16, height = 0.18),
            ),
            detection(
                label = "hate-symbol-mask",
                category = SensitiveContentCategory.HATE_SYMBOL,
                severity = SensitiveContentSeverity.HIGH,
                confidence = 0.90,
                region = rasterMask("m-reject", image),
            ),
            detection(
                label = "unknown-sensitive-region",
                category = SensitiveContentCategory.OTHER,
                severity = SensitiveContentSeverity.HIGH,
                confidence = 0.77,
                region = polygon("g-quarantine"),
            ),
        )
    }

    private fun detection(
        label: String,
        category: SensitiveContentCategory,
        severity: SensitiveContentSeverity,
        confidence: Double,
        region: SensitiveRegion,
    ): SensitiveContentDetection =
        SensitiveContentDetection(
            label = label,
            category = category,
            severity = severity,
            confidence = confidence,
            sourceBackend = "deterministic-example-detector",
            rawBackendLabel = "fixture:$label",
            policyReason = "Fixture output used to demonstrate workflow behavior",
            region = region,
            metadata = mapOf("detectorMode" to "fake"),
        )

    private fun rectangle(
        id: String,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
    ): SensitiveRegion =
        SensitiveRegion(
            id = id,
            geometry = SensitiveRegionGeometry.Rectangle(
                x = x,
                y = y,
                width = width,
                height = height,
                coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
            ),
        )

    private fun polygon(id: String): SensitiveRegion =
        SensitiveRegion(
            id = id,
            geometry = SensitiveRegionGeometry.Polygon(
                points = listOf(
                    SensitivePoint(0.58, 0.58),
                    SensitivePoint(0.72, 0.56),
                    SensitivePoint(0.75, 0.70),
                    SensitivePoint(0.61, 0.74),
                    SensitivePoint(0.58, 0.58),
                ),
                coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
            ),
        )

    private fun polyline(id: String): SensitiveRegion =
        SensitiveRegion(
            id = id,
            geometry = SensitiveRegionGeometry.Polyline(
                points = listOf(
                    SensitivePoint(0.12, 0.76),
                    SensitivePoint(0.36, 0.79),
                    SensitivePoint(0.48, 0.82),
                ),
                coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
            ),
        )

    private fun rasterMask(id: String, image: ImmutableImage): SensitiveRegion =
        SensitiveRegion(
            id = id,
            geometry = SensitiveRegionGeometry.RasterMask(
                mask = SensitiveRasterMask(
                    width = (image.width / 4).coerceAtLeast(1),
                    height = (image.height / 4).coerceAtLeast(1),
                    reference = "memory://fixture/hate-symbol-mask",
                    mediaType = "image/png",
                    checksum = "sha256:example-fixture-only",
                ),
            ),
        )

    private fun Int.requirePositive(name: String) {
        require(this > 0) { "$name must be > 0, but was $this" }
    }
}

data class SensitiveWorkflowExampleResult(
    val source: Path,
    val previewOutput: Path,
    val reportOutput: Path,
    val detections: List<SensitiveContentDetection>,
    val actionPlans: List<SensitiveWorkflowActionPlan>,
    val preview: PrivacyDerivativeResult,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = -2512653319463079765L
    }
}

data class SensitiveWorkflowActionPlan(
    val detectionLabel: String,
    val category: SensitiveContentCategory,
    val severity: SensitiveContentSeverity,
    val confidence: Double,
    val regionKind: SensitiveWorkflowRegionKind,
    val action: SensitiveTreatmentAction,
    val level: SensitiveTreatmentLevel,
    val intensity: String,
    val reason: String,
    val renderableInCoreDerivative: Boolean,
    val region: SensitiveRegion?,
) : Serializable {

    init {
        detectionLabel.requireNotBlank("detectionLabel")
        intensity.requireNotBlank("intensity")
        reason.requireNotBlank("reason")
    }

    companion object {
        private const val serialVersionUID: Long = 4900983886370352925L
    }
}

enum class SensitiveWorkflowRegionKind {
    NONE,
    RECTANGLE,
    POLYGON,
    POLYLINE,
    RASTER_MASK,
}
