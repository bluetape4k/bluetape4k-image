package io.bluetape4k.images.examples.basic

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.moderation.SensitiveTreatmentAction
import io.bluetape4k.images.privacy.PrivacyDerivativeAction
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.nio.file.Files
import javax.imageio.ImageIO

class SensitiveContentWorkflowQuickstartTest {

    companion object: KLogging()

    @Test
    fun `generates deterministic moderation workflow example`() = runSuspendIO {
        val outputDirectory = Files.createTempDirectory("sensitive-workflow-example-")

        val result = SensitiveContentWorkflowQuickstart.generate(outputDirectory)
        log.debug { "result=$result" }

        Files.exists(result.previewOutput).shouldBeTrue()
        Files.exists(result.reportOutput).shouldBeTrue()
        Files.size(result.previewOutput) shouldBeGreaterThan 0L
        Files.size(result.reportOutput) shouldBeGreaterThan 0L

        val decoded = ImageIO.read(result.previewOutput.toFile())
        log.debug { "decoded=$decoded" }
        decoded.width shouldBeEqualTo 640
        decoded.height shouldBeEqualTo 480

        result.detections.size shouldBeEqualTo 8
        result.actionPlans.map { it.action }.toSet() shouldBeEqualTo setOf(
            SensitiveTreatmentAction.ALLOW,
            SensitiveTreatmentAction.MOSAIC,
            SensitiveTreatmentAction.BLUR,
            SensitiveTreatmentAction.SOLID_MASK,
            SensitiveTreatmentAction.MANUAL_REVIEW,
            SensitiveTreatmentAction.DROP,
            SensitiveTreatmentAction.REJECT,
            SensitiveTreatmentAction.QUARANTINE,
        )
        result.actionPlans.map { it.regionKind }.toSet() shouldBeEqualTo setOf(
            SensitiveWorkflowRegionKind.RECTANGLE,
            SensitiveWorkflowRegionKind.POLYGON,
            SensitiveWorkflowRegionKind.POLYLINE,
            SensitiveWorkflowRegionKind.RASTER_MASK,
        )
        result.actionPlans.count { it.renderableInCoreDerivative } shouldBeEqualTo 2
        result.preview.report.appliedActions shouldContain PrivacyDerivativeAction.REDACTED
        result.preview.report.redactions.size shouldBeEqualTo 2

        val reportText = Files.readString(result.reportOutput)
        log.debug { "reportText=$reportText" }
        reportText shouldContain "deterministic fake output"
        reportText shouldContain "false-positive"
        reportText shouldContain "coreDerivative=false"
    }
}
