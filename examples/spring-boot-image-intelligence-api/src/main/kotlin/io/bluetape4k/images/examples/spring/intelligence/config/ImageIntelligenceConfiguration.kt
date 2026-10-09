package io.bluetape4k.images.examples.spring.intelligence.config

import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader
import io.bluetape4k.images.examples.spring.intelligence.service.BarcodeAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.DetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.DisabledDetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.DisabledOcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.FixtureDetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.FixtureOcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.GuardedAnalysisRunner
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceAggregator
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceProfileGuard
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceService
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceWorkflow
import io.bluetape4k.images.examples.spring.intelligence.service.ImageUploadQualifier
import io.bluetape4k.images.examples.spring.intelligence.service.OcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.TesseractOcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.VisitorPassPolicy
import io.bluetape4k.images.examples.spring.intelligence.service.ZxingBarcodeAnalysisProvider
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.OcrStructuredDetail
import io.bluetape4k.images.ocr.TesseractOcrEngine
import io.bluetape4k.javatimes.inMillis
import io.bluetape4k.javatimes.seconds
import io.bluetape4k.support.requireGt
import io.bluetape4k.support.requireInRange
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import java.io.Serializable
import java.time.Duration

/**
 * image-intelligence example의 input 및 provider execution limit입니다.
 */
@ConfigurationProperties(prefix = "example.image-intelligence")
data class ImageIntelligenceProperties(
    val maxInputBytes: Long = 5L * 1024L * 1024L,
    val maxInputPixels: Long = 16_777_216L,
    val maxInputSide: Int = 8_192,
    val ocrTimeout: Duration = 3.seconds(),
    val detectionTimeout: Duration = 2.seconds(),
    val barcodeTimeout: Duration = 2.seconds(),
    val ocrConcurrency: Int = 1,
    val detectionConcurrency: Int = 2,
    val barcodeConcurrency: Int = 4,
    val tessdataPath: String? = null,
): Serializable {

    init {
        maxInputBytes.requireInRange(1, Int.MAX_VALUE.toLong(), "maxInputBytes")
        maxInputPixels.requirePositiveNumber("maxInputPixels")
        maxInputSide.requirePositiveNumber("maxInputSide")
        ocrTimeout.inMillis().requireGt(0) { "ocrTimeout must be at least 1 ms" }
        detectionTimeout.inMillis().requireGt(0) { "detectionTimeout must be at least 1 ms" }
        barcodeTimeout.inMillis().requireGt(0) { "barcodeTimeout must be at least 1 ms" }
        ocrConcurrency.requirePositiveNumber("ocrConcurrency")
        detectionConcurrency.requirePositiveNumber("detectionConcurrency")
        barcodeConcurrency.requirePositiveNumber("barcodeConcurrency")
        tessdataPath?.requireNotBlank { "tessdataPath must be null or non-blank" }
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImageIntelligenceProperties::class)
internal class ImageIntelligenceConfiguration {

    @Bean
    fun imageIntelligenceProfileGuard(environment: Environment): SmartInitializingSingleton =
        ImageIntelligenceProfileGuard(environment)

    @Bean
    @Profile("!demo & !native-ocr")
    fun disabledOcrAnalysisProvider(): OcrAnalysisProvider =
        DisabledOcrAnalysisProvider()

    @Bean
    @Profile("demo & !native-ocr")
    fun fixtureOcrAnalysisProvider(): OcrAnalysisProvider =
        FixtureOcrAnalysisProvider()

    @Bean
    @Profile("native-ocr & !demo")
    fun tesseractOcrAnalysisProvider(properties: ImageIntelligenceProperties): OcrAnalysisProvider =
        TesseractOcrAnalysisProvider(
            engine = TesseractOcrEngine(),
            options = OcrOptions(
                tessdataPath = properties.tessdataPath,
                structuredDetail = OcrStructuredDetail.LINE,
            ),
            dispatcher = Dispatchers.IO,
        )

    @Bean
    @Profile("!demo")
    fun disabledDetectionAnalysisProvider(): DetectionAnalysisProvider =
        DisabledDetectionAnalysisProvider()

    @Bean
    @Profile("demo")
    fun fixtureDetectionAnalysisProvider(): DetectionAnalysisProvider =
        FixtureDetectionAnalysisProvider()

    @Bean
    fun barcodeAnalysisProvider(): BarcodeAnalysisProvider =
        ZxingBarcodeAnalysisProvider(
            reader = ZxingBarcodeReader(),
            dispatcher = Dispatchers.IO,
        )

    @Bean
    fun imageUploadQualifier(properties: ImageIntelligenceProperties): ImageUploadQualifier =
        ImageUploadQualifier(properties)

    @Bean
    fun guardedAnalysisRunner(): GuardedAnalysisRunner =
        GuardedAnalysisRunner()

    @Bean
    @ConditionalOnBean(
        OcrAnalysisProvider::class,
        DetectionAnalysisProvider::class,
        BarcodeAnalysisProvider::class,
    )
    fun imageIntelligenceWorkflow(
        ocrProvider: OcrAnalysisProvider,
        detectionProvider: DetectionAnalysisProvider,
        barcodeProvider: BarcodeAnalysisProvider,
        runner: GuardedAnalysisRunner,
        properties: ImageIntelligenceProperties,
    ): ImageIntelligenceWorkflow =
        ImageIntelligenceWorkflow(
            ocrProvider = ocrProvider,
            detectionProvider = detectionProvider,
            barcodeProvider = barcodeProvider,
            runner = runner,
            properties = properties,
        )

    @Bean
    fun imageIntelligenceAggregator(): ImageIntelligenceAggregator =
        ImageIntelligenceAggregator()

    @Bean
    fun visitorPassPolicy(): VisitorPassPolicy =
        VisitorPassPolicy()

    @Bean
    @ConditionalOnBean(ImageIntelligenceWorkflow::class)
    fun imageIntelligenceService(
        qualifier: ImageUploadQualifier,
        workflow: ImageIntelligenceWorkflow,
        aggregator: ImageIntelligenceAggregator,
        policy: VisitorPassPolicy,
    ): ImageIntelligenceService =
        ImageIntelligenceService(
            qualifier = qualifier,
            workflow = workflow,
            aggregator = aggregator,
            policy = policy,
        )
}
