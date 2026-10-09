package io.bluetape4k.images.examples.spring.intelligence.config

import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.examples.spring.intelligence.service.DetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.DisabledDetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.DisabledOcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.FixtureDetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.FixtureOcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.OcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.TesseractOcrAnalysisProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ImageIntelligenceConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(ImageIntelligenceConfiguration::class.java)

    @Test
    fun `default profile owns disabled OCR and detector`() {
        contextRunner.run { context ->
            context.getBean<OcrAnalysisProvider>().shouldBeInstanceOf<DisabledOcrAnalysisProvider>()
            context.getBean<DetectionAnalysisProvider>().shouldBeInstanceOf<DisabledDetectionAnalysisProvider>()
        }
    }

    @Test
    fun `demo profile owns fixture OCR and detector`() {
        contextRunner
            .withInitializer { it.environment.setActiveProfiles("demo") }
            .run { context ->
                context.getBean<OcrAnalysisProvider>().shouldBeInstanceOf<FixtureOcrAnalysisProvider>()
                context.getBean<DetectionAnalysisProvider>().shouldBeInstanceOf<FixtureDetectionAnalysisProvider>()
            }
    }

    @Test
    fun `native OCR profile owns Tesseract and keeps detector disabled`() {
        contextRunner
            .withInitializer { it.environment.setActiveProfiles("native-ocr") }
            .run { context ->
                context.getBean<OcrAnalysisProvider>().shouldBeInstanceOf<TesseractOcrAnalysisProvider>()
                context.getBean<DetectionAnalysisProvider>().shouldBeInstanceOf<DisabledDetectionAnalysisProvider>()
            }
    }

    @Test
    fun `demo and native OCR profiles fail with a stable message`() {
        contextRunner
            .withInitializer { it.environment.setActiveProfiles("demo", "native-ocr") }
            .run { context ->
                val failure = context.startupFailure.shouldNotBeNull()

                generateSequence(failure) { it.cause }
                    .mapNotNull(Throwable::message)
                    .any { it.contains("Profiles 'demo' and 'native-ocr' cannot be active together.") }
                    .shouldBeTrue()
            }
    }
}
