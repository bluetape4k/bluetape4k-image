package io.bluetape4k.images.moderation

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * 감사 가능한 sensitive-content moderation workflow의 상태 이름입니다.
 *
 * policy layer는 이 상태를 decision metadata로만 기록합니다. rendering, quarantine
 * storage, rejection side effect는 계속 caller 책임입니다.
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
 * sensitive-content moderation policy가 선택한 treatment입니다.
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
 * treatment action에 부여되는 대략적인 강도 또는 긴급도입니다.
 */
enum class SensitiveTreatmentLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

/**
 * policy가 선택한 optional treatment parameter입니다.
 *
 * parameter는 descriptive하며 renderer-neutral입니다. renderer는 [mosaicBlockSize],
 * [blurRadius], [maskOpacity] 같은 지원 값을 해석할 수 있고, 순수 rejection 또는
 * quarantine path는 pixel parameter를 무시할 수 있습니다.
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
 * sensitive-content moderation policy의 단일 rule입니다.
 *
 * [categories]가 비어 있으면 모든 category와 match될 수 있습니다. [minimumSeverity]와
 * [minimumConfidence]는 inclusive threshold입니다.
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
     * 이 rule이 [detection]에 적용되면 `true`를 반환합니다.
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
 * detection별 moderation decision입니다.
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
 * sensitive-content moderation policy가 생성하는 감사 가능한 report입니다.
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
 * sensitive-content detection을 위한 backend-neutral moderation policy입니다.
 *
 * ## 동작/계약
 * - [SensitiveContentDetection] fact를 소비해 treatment decision을 냅니다.
 * - detector inference를 실행하거나 pixel을 render하지 않습니다.
 * - 각 detection마다 처음 match되는 rule을 선택합니다.
 * - detection에 match되는 rule이 없으면 [fallbackRule]을 적용해 unknown category를
 *   기본적으로 fail-closed로 처리합니다.
 */
data class SensitiveModerationPolicy(
    val rules: List<SensitiveModerationRule>,
    val fallbackRule: SensitiveModerationRule = failClosedFallbackRule(),
): Serializable {

    /**
     * [detections]를 평가하고 감사 가능한 moderation report를 반환합니다.
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
         * match되지 않은 detection을 quarantine하는 fail-closed policy를 생성합니다.
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
