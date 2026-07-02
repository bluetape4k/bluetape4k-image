package io.bluetape4k.images.moderation

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * State names for an auditable sensitive-content moderation workflow.
 *
 * The policy layer records these states only as decision metadata. Rendering,
 * quarantine storage, and rejection side effects remain caller responsibilities.
 */
enum class SensitiveModerationWorkflowState {
    DETECTED,
    CLASSIFIED,
    POLICY_EVALUATED,
    ACTION_SELECTED,
    RENDERED,
    REJECTED,
    QUARANTINED,
    FAILED,
}

/**
 * Treatment selected by a sensitive-content moderation policy.
 */
enum class SensitiveTreatmentAction {
    ALLOW,
    MOSAIC,
    BLUR,
    SOLID_MASK,
    DROP,
    REJECT,
    QUARANTINE,
    MANUAL_REVIEW,
}

/**
 * Coarse strength or urgency assigned to a treatment action.
 */
enum class SensitiveTreatmentLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

/**
 * Optional treatment parameters selected by policy.
 *
 * The parameters are descriptive and renderer-neutral. A renderer may interpret
 * supported values such as [mosaicBlockSize], [blurRadius], or [maskOpacity],
 * while a pure rejection or quarantine path can ignore pixel parameters.
 */
data class SensitiveTreatmentParameters(
    val mosaicBlockSize: Int? = null,
    val blurRadius: Double? = null,
    val blurSigma: Double? = null,
    val maskOpacity: Double? = null,
    val maskStyle: String? = null,
    val reviewPriority: Int? = null,
    val rejectReason: String? = null,
    val metadata: Map<String, String> = emptyMap(),
): Serializable {

    init {
        mosaicBlockSize?.requirePositive("mosaicBlockSize")
        blurRadius?.requirePositiveFinite("blurRadius")
        blurSigma?.requirePositiveFinite("blurSigma")
        maskOpacity?.requireFiniteProbability("maskOpacity")
        maskStyle.requireNotBlankIfPresent("maskStyle")
        reviewPriority?.requireNonNegative("reviewPriority")
        rejectReason.requireNotBlankIfPresent("rejectReason")
        metadata.requireValidPolicyMetadata("metadata")
    }

    companion object {
        private const val serialVersionUID: Long = 2478670818038138570L
    }
}

/**
 * One rule in a sensitive-content moderation policy.
 *
 * Empty [categories] means the rule may match any category. [minimumSeverity]
 * and [minimumConfidence] are inclusive thresholds.
 */
data class SensitiveModerationRule(
    val id: String,
    val action: SensitiveTreatmentAction,
    val level: SensitiveTreatmentLevel,
    val reason: String,
    val categories: Set<SensitiveContentCategory> = emptySet(),
    val minimumSeverity: SensitiveContentSeverity = SensitiveContentSeverity.LOW,
    val minimumConfidence: Double = 0.0,
    val parameters: SensitiveTreatmentParameters = SensitiveTreatmentParameters(),
): Serializable {

    init {
        id.requireNotBlank("id")
        reason.requireNotBlank("reason")
        minimumConfidence.requireFiniteProbability("minimumConfidence")
    }

    /**
     * Returns true when this rule applies to [detection].
     */
    fun matches(detection: SensitiveContentDetection): Boolean =
        (categories.isEmpty() || detection.category in categories) &&
            detection.severity >= minimumSeverity &&
            detection.confidence >= minimumConfidence

    companion object {
        private const val serialVersionUID: Long = -8978805960184835272L
    }
}

/**
 * Per-detection moderation decision.
 */
data class SensitiveModerationDecision(
    val detection: SensitiveContentDetection,
    val workflowState: SensitiveModerationWorkflowState,
    val action: SensitiveTreatmentAction,
    val level: SensitiveTreatmentLevel,
    val parameters: SensitiveTreatmentParameters,
    val reason: String,
    val matchedRuleId: String?,
): Serializable {

    init {
        reason.requireNotBlank("reason")
        matchedRuleId.requireNotBlankIfPresent("matchedRuleId")
    }

    companion object {
        private const val serialVersionUID: Long = 8309861929051965644L
    }
}

/**
 * Auditable report produced by a sensitive-content moderation policy.
 */
data class SensitiveModerationReport(
    val decisions: List<SensitiveModerationDecision>,
    val workflowStates: List<SensitiveModerationWorkflowState>,
    val selectedAction: SensitiveTreatmentAction,
    val selectedLevel: SensitiveTreatmentLevel,
    val selectedParameters: SensitiveTreatmentParameters,
    val reason: String,
): Serializable {

    init {
        reason.requireNotBlank("reason")
        require(workflowStates.isNotEmpty()) { "workflowStates must not be empty" }
    }

    companion object {
        private const val serialVersionUID: Long = -5813981965963547276L
    }
}

/**
 * Backend-neutral moderation policy for sensitive-content detections.
 *
 * ## Contract
 * - Consumes [SensitiveContentDetection] facts and emits treatment decisions.
 * - Does not run detector inference or render pixels.
 * - Selects the first matching rule for each detection.
 * - Applies [fallbackRule] when no rule matches a detection, keeping unknown
 *   categories fail-closed by default.
 */
data class SensitiveModerationPolicy(
    val rules: List<SensitiveModerationRule>,
    val fallbackRule: SensitiveModerationRule = failClosedFallbackRule(),
): Serializable {

    /**
     * Evaluates [detections] and returns an auditable moderation report.
     */
    fun evaluate(detections: Iterable<SensitiveContentDetection>): SensitiveModerationReport {
        val decisions = detections.map { detection ->
            val rule = rules.firstOrNull { it.matches(detection) } ?: fallbackRule
            SensitiveModerationDecision(
                detection = detection,
                workflowState = SensitiveModerationWorkflowState.ACTION_SELECTED,
                action = rule.action,
                level = rule.level,
                parameters = rule.parameters,
                reason = rule.reason,
                matchedRuleId = rule.id,
            )
        }

        if (decisions.isEmpty()) {
            return SensitiveModerationReport(
                decisions = emptyList(),
                workflowStates = listOf(SensitiveModerationWorkflowState.POLICY_EVALUATED),
                selectedAction = SensitiveTreatmentAction.ALLOW,
                selectedLevel = SensitiveTreatmentLevel.LOW,
                selectedParameters = SensitiveTreatmentParameters(),
                reason = "No sensitive-content detections were provided",
            )
        }

        val selected = decisions.maxWith(SENSITIVE_DECISION_ORDER)
        return SensitiveModerationReport(
            decisions = decisions,
            workflowStates = selected.workflowStates(),
            selectedAction = selected.action,
            selectedLevel = selected.level,
            selectedParameters = selected.parameters,
            reason = selected.reason,
        )
    }

    companion object {
        private const val serialVersionUID: Long = -1616822281669313145L

        /**
         * Creates a fail-closed policy that quarantines unmatched detections.
         */
        fun failClosed(
            rules: List<SensitiveModerationRule>,
            fallbackReason: String = "No sensitive-content policy rule matched",
        ): SensitiveModerationPolicy =
            SensitiveModerationPolicy(
                rules = rules,
                fallbackRule = failClosedFallbackRule(fallbackReason),
            )

        private fun failClosedFallbackRule(reason: String = "No sensitive-content policy rule matched") =
            SensitiveModerationRule(
                id = "fallback-fail-closed",
                action = SensitiveTreatmentAction.QUARANTINE,
                level = SensitiveTreatmentLevel.CRITICAL,
                reason = reason,
                categories = emptySet(),
                minimumSeverity = SensitiveContentSeverity.LOW,
                minimumConfidence = 0.0,
            )
    }
}

private val SENSITIVE_DECISION_ORDER: Comparator<SensitiveModerationDecision> =
    compareBy<SensitiveModerationDecision> { it.action.precedence }
        .thenBy { it.level.ordinal }
        .thenBy { it.detection.confidence }

private val SensitiveTreatmentAction.precedence: Int
    get() = when (this) {
        SensitiveTreatmentAction.ALLOW -> 0
        SensitiveTreatmentAction.MOSAIC -> 10
        SensitiveTreatmentAction.BLUR -> 20
        SensitiveTreatmentAction.SOLID_MASK -> 30
        SensitiveTreatmentAction.MANUAL_REVIEW -> 40
        SensitiveTreatmentAction.DROP -> 50
        SensitiveTreatmentAction.QUARANTINE -> 60
        SensitiveTreatmentAction.REJECT -> 70
    }

private fun SensitiveModerationDecision.workflowStates(): List<SensitiveModerationWorkflowState> =
    buildList {
        add(SensitiveModerationWorkflowState.DETECTED)
        add(SensitiveModerationWorkflowState.CLASSIFIED)
        add(SensitiveModerationWorkflowState.POLICY_EVALUATED)
        add(SensitiveModerationWorkflowState.ACTION_SELECTED)
        when (action) {
            SensitiveTreatmentAction.REJECT ->
                add(SensitiveModerationWorkflowState.REJECTED)

            SensitiveTreatmentAction.QUARANTINE ->
                add(SensitiveModerationWorkflowState.QUARANTINED)

            else -> Unit
        }
    }

private fun Int.requirePositive(name: String) {
    require(this > 0) { "$name must be > 0, but was $this" }
}

private fun Int.requireNonNegative(name: String) {
    require(this >= 0) { "$name must be >= 0, but was $this" }
}

private fun Double.requirePositiveFinite(name: String) {
    require(isFinite()) { "$name must be finite, but was $this" }
    require(this > 0.0) { "$name must be > 0, but was $this" }
}

private fun Double.requireFiniteProbability(name: String) {
    require(isFinite()) { "$name must be finite, but was $this" }
    require(this in 0.0..1.0) { "$name must be in 0.0..1.0, but was $this" }
}

private fun String?.requireNotBlankIfPresent(name: String) {
    if (this != null) {
        requireNotBlank(name)
    }
}

private fun Map<String, String>.requireValidPolicyMetadata(name: String) {
    forEach { (key, value) ->
        key.requireNotBlank("$name key")
        value.requireNotBlank("$name[$key]")
    }
}
