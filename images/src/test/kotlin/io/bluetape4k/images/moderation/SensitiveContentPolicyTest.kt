package io.bluetape4k.images.moderation

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import org.junit.jupiter.api.Test

class SensitiveContentPolicyTest {

    @Test
    fun `level based policy maps high severity to blur parameters`() {
        val policy = SensitiveModerationPolicy.failClosed(
            rules = listOf(
                SensitiveModerationRule(
                    id = "explicit-high-blur",
                    categories = setOf(SensitiveContentCategory.EXPLICIT_NUDITY),
                    minimumSeverity = SensitiveContentSeverity.HIGH,
                    minimumConfidence = 0.85,
                    action = SensitiveTreatmentAction.BLUR,
                    level = SensitiveTreatmentLevel.HIGH,
                    parameters = SensitiveTreatmentParameters(
                        blurRadius = 12.0,
                        blurSigma = 3.0,
                        metadata = mapOf("renderer" to "local-blur"),
                    ),
                    reason = "High confidence explicit content must be blurred",
                ),
            ),
        )

        val report = policy.evaluate(
            listOf(
                detection(
                    category = SensitiveContentCategory.EXPLICIT_NUDITY,
                    severity = SensitiveContentSeverity.HIGH,
                    confidence = 0.91,
                ),
            ),
        )

        report.decisions shouldHaveSize 1
        report.selectedAction shouldBeEqualTo SensitiveTreatmentAction.BLUR
        report.selectedLevel shouldBeEqualTo SensitiveTreatmentLevel.HIGH
        report.selectedParameters.blurRadius shouldBeEqualTo 12.0
        report.selectedParameters.blurSigma shouldBeEqualTo 3.0
        report.workflowStates shouldContain SensitiveModerationWorkflowState.ACTION_SELECTED
    }

    @Test
    fun `threshold boundaries are inclusive`() {
        val policy = SensitiveModerationPolicy.failClosed(
            rules = listOf(
                SensitiveModerationRule(
                    id = "violence-medium-mosaic",
                    categories = setOf(SensitiveContentCategory.VIOLENCE),
                    minimumSeverity = SensitiveContentSeverity.MEDIUM,
                    minimumConfidence = 0.70,
                    action = SensitiveTreatmentAction.MOSAIC,
                    level = SensitiveTreatmentLevel.MEDIUM,
                    parameters = SensitiveTreatmentParameters(mosaicBlockSize = 16),
                    reason = "Violence at threshold uses mosaic",
                ),
            ),
        )

        val report = policy.evaluate(
            listOf(
                detection(
                    category = SensitiveContentCategory.VIOLENCE,
                    severity = SensitiveContentSeverity.MEDIUM,
                    confidence = 0.70,
                ),
            ),
        )

        report.selectedAction shouldBeEqualTo SensitiveTreatmentAction.MOSAIC
        report.selectedParameters.mosaicBlockSize shouldBeEqualTo 16
    }

    @Test
    fun `unknown categories use fail closed fallback`() {
        val policy = SensitiveModerationPolicy.failClosed(
            rules = listOf(
                SensitiveModerationRule(
                    id = "known-low-allow",
                    categories = setOf(SensitiveContentCategory.SUGGESTIVE),
                    minimumSeverity = SensitiveContentSeverity.LOW,
                    minimumConfidence = 0.0,
                    action = SensitiveTreatmentAction.ALLOW,
                    level = SensitiveTreatmentLevel.LOW,
                    reason = "Known low-risk category can pass",
                ),
            ),
        )

        val report = policy.evaluate(
            listOf(
                detection(
                    category = SensitiveContentCategory.OTHER,
                    severity = SensitiveContentSeverity.LOW,
                    confidence = 0.20,
                    rawBackendLabel = "unknown_model_label",
                ),
            ),
        )

        report.selectedAction shouldBeEqualTo SensitiveTreatmentAction.QUARANTINE
        report.selectedLevel shouldBeEqualTo SensitiveTreatmentLevel.CRITICAL
        report.decisions.single().matchedRuleId shouldBeEqualTo "fallback-fail-closed"
        report.workflowStates shouldContain SensitiveModerationWorkflowState.QUARANTINED
    }

    @Test
    fun `empty rule policy quarantines every detection through fallback`() {
        val report = SensitiveModerationPolicy.failClosed(rules = emptyList()).evaluate(
            listOf(
                detection(
                    category = SensitiveContentCategory.SENSITIVE_TEXT,
                    severity = SensitiveContentSeverity.LOW,
                    confidence = 0.10,
                ),
            ),
        )

        report.selectedAction shouldBeEqualTo SensitiveTreatmentAction.QUARANTINE
        report.decisions.single().reason shouldBeEqualTo "No sensitive-content policy rule matched"
    }

    @Test
    fun `multiple regions select highest precedence mixed action`() {
        val policy = SensitiveModerationPolicy.failClosed(
            rules = listOf(
                SensitiveModerationRule(
                    id = "weapon-review",
                    categories = setOf(SensitiveContentCategory.WEAPON),
                    minimumSeverity = SensitiveContentSeverity.MEDIUM,
                    minimumConfidence = 0.60,
                    action = SensitiveTreatmentAction.MANUAL_REVIEW,
                    level = SensitiveTreatmentLevel.MEDIUM,
                    parameters = SensitiveTreatmentParameters(reviewPriority = 3),
                    reason = "Weapon detections require human review",
                ),
                SensitiveModerationRule(
                    id = "minor-critical-reject",
                    categories = setOf(SensitiveContentCategory.MINOR_SAFETY),
                    minimumSeverity = SensitiveContentSeverity.CRITICAL,
                    minimumConfidence = 0.90,
                    action = SensitiveTreatmentAction.REJECT,
                    level = SensitiveTreatmentLevel.CRITICAL,
                    parameters = SensitiveTreatmentParameters(rejectReason = "minor-safety-critical"),
                    reason = "Critical minor safety content must be rejected",
                ),
            ),
        )

        val report = policy.evaluate(
            listOf(
                detection(
                    category = SensitiveContentCategory.WEAPON,
                    severity = SensitiveContentSeverity.MEDIUM,
                    confidence = 0.72,
                    regionId = "weapon-region",
                ),
                detection(
                    category = SensitiveContentCategory.MINOR_SAFETY,
                    severity = SensitiveContentSeverity.CRITICAL,
                    confidence = 0.96,
                    regionId = "minor-region",
                ),
            ),
        )

        report.decisions shouldHaveSize 2
        report.selectedAction shouldBeEqualTo SensitiveTreatmentAction.REJECT
        report.selectedParameters.rejectReason shouldBeEqualTo "minor-safety-critical"
        report.reason shouldBeEqualTo "Critical minor safety content must be rejected"
        report.workflowStates shouldContain SensitiveModerationWorkflowState.REJECTED
    }

    @Test
    fun `empty detection list is allowed without rendering`() {
        val report = SensitiveModerationPolicy.failClosed(
            rules = listOf(
                SensitiveModerationRule(
                    id = "solid-mask-high",
                    categories = setOf(SensitiveContentCategory.SENSITIVE_TEXT),
                    minimumSeverity = SensitiveContentSeverity.HIGH,
                    action = SensitiveTreatmentAction.SOLID_MASK,
                    level = SensitiveTreatmentLevel.HIGH,
                    parameters = SensitiveTreatmentParameters(maskOpacity = 0.85, maskStyle = "solid-black"),
                    reason = "High-risk text must be masked",
                ),
            ),
        ).evaluate(emptyList())

        report.decisions shouldHaveSize 0
        report.selectedAction shouldBeEqualTo SensitiveTreatmentAction.ALLOW
        report.workflowStates shouldBeEqualTo listOf(SensitiveModerationWorkflowState.POLICY_EVALUATED)
    }

    @Test
    fun `action parameters reject invalid values`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SensitiveTreatmentParameters(maskOpacity = 1.2)
        }

        error.message shouldContain "maskOpacity"
    }

    private fun detection(
        category: SensitiveContentCategory,
        severity: SensitiveContentSeverity,
        confidence: Double,
        rawBackendLabel: String = category.name.lowercase(),
        regionId: String? = null,
    ): SensitiveContentDetection =
        SensitiveContentDetection(
            label = category.name.lowercase(),
            category = category,
            severity = severity,
            confidence = confidence,
            sourceBackend = "unit-sensitive-detector",
            rawBackendLabel = rawBackendLabel,
            policyReason = "unit fixture",
            region = regionId?.let {
                SensitiveRegion(
                    geometry = SensitiveRegionGeometry.Rectangle(
                        x = 0.10,
                        y = 0.10,
                        width = 0.30,
                        height = 0.20,
                        coordinateSpace = SensitiveCoordinateSpace.NORMALIZED,
                    ),
                    id = it,
                )
            },
        )
}
