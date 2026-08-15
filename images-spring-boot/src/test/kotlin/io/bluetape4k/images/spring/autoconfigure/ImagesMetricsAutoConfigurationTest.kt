package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeInstanceOf
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.spring.metrics.MetricImageStorage
import io.bluetape4k.images.spring.metrics.MetricImageStorageWithMetadata
import io.bluetape4k.images.spring.storage.ImageObjectMetadataReader
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.images.spring.storage.LocalImageStorage
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Path

class ImagesMetricsAutoConfigurationTest {

    @TempDir
    lateinit var tempDir: Path

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ImagesProcessingAutoConfiguration::class.java,
                ImagesStorageAutoConfiguration::class.java,
                ImagesMetricsAutoConfiguration::class.java,
            )
        )

    private val bootMetricsContextRunner = ApplicationContextRunner()
        .withPropertyValues("management.simple.metrics.export.enabled=true")
        .withConfiguration(
            AutoConfigurations.of(
                ImagesProcessingAutoConfiguration::class.java,
                ImagesStorageAutoConfiguration::class.java,
                MetricsAutoConfiguration::class.java,
                CompositeMeterRegistryAutoConfiguration::class.java,
                SimpleMetricsExportAutoConfiguration::class.java,
                ImagesMetricsAutoConfiguration::class.java,
            )
        )

    @Test
    fun `wraps ImageStorage in MetricImageStorage when MeterRegistry bean present`() {
        contextRunner
            .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ImageStorage::class.java)
                ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf MetricImageStorage::class
            }
    }

    @Test
    fun `preserves LocalImageStorage metadata capability through metrics decorator`() {
        contextRunner
            .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
            .run { ctx ->
                ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf MetricImageStorageWithMetadata::class
                ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf MetricImageStorage::class
                (ctx.getBean(ImageStorage::class.java) as? ImageObjectMetadataReader).shouldNotBeNull()
            }
    }

    @Test
    fun `does not advertise metadata capability for unsupported custom storage`() {
        val customStorage = mockk<ImageStorage>(relaxed = true)
        contextRunner
            .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
            .withBean(ImageStorage::class.java, { customStorage })
            .run { ctx ->
                (ctx.getBean(ImageStorage::class.java) as? ImageObjectMetadataReader).shouldBeNull()
            }
    }

    @Test
    fun `wraps ImageStorage when Boot creates the MeterRegistry after metrics auto-configuration`() {
        bootMetricsContextRunner.run { ctx ->
            assertThat(ctx).hasSingleBean(MeterRegistry::class.java)
            assertThat(ctx).hasSingleBean(ImageStorage::class.java)
            ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf MetricImageStorage::class
        }
    }

    @Test
    fun `backs off when Micrometer is on classpath but no MeterRegistry bean is registered`() {
        contextRunner.run { ctx ->
            assertThat(ctx).hasSingleBean(ImageStorage::class.java)
            ctx.getBean(ImageStorage::class.java) shouldNotBeInstanceOf MetricImageStorage::class
        }
    }

    @Test
    fun `disabled when metrics enabled=false`() {
        // MeterRegistry가 있어도 metrics를 끄면 BPP 전체를 건너뜁니다.
        contextRunner
            .withPropertyValues("bluetape4k.images.metrics.enabled=false")
            .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ImageStorage::class.java)
                ctx.getBean(ImageStorage::class.java) shouldNotBeInstanceOf MetricImageStorage::class
            }
    }

    @Test
    fun `does not double-wrap a pre-existing MetricImageStorage bean`() {
        // MetricImageStorage bean을 직접 제공합니다. BPP는 bean !is MetricImageStorage 조건으로 이를 건너뛰어야 합니다.
        val registry = SimpleMeterRegistry()
        val inner = LocalImageStorage(tempDir, 10 * 1024 * 1024L)
        val alreadyWrapped = MetricImageStorage(delegate = inner, registry = registry)

        contextRunner
            .withBean(MeterRegistry::class.java, { registry })
            .withBean(ImageStorage::class.java, { alreadyWrapped })
            .run { ctx ->
                val storage = ctx.getBean(ImageStorage::class.java)
                storage shouldBeSameInstanceAs alreadyWrapped
                // delegate가 또 다른 MetricImageStorage가 아니라 LocalImageStorage인지 확인합니다.
                val delegate = MetricImageStorage::class.java
                    .getDeclaredField("delegate")
                    .also { it.isAccessible = true }
                    .get(storage)
                delegate shouldNotBeInstanceOf MetricImageStorage::class
                delegate shouldBeInstanceOf LocalImageStorage::class
            }
    }

    @Test
    fun `does not double-wrap a capability-preserving metrics bean`() {
        val registry = SimpleMeterRegistry()
        val inner = LocalImageStorage(tempDir, 10 * 1024 * 1024L)
        val alreadyWrapped = MetricImageStorageWithMetadata(
            delegate = inner,
            registry = registry,
            metadataDelegate = inner,
        )

        contextRunner
            .withBean(MeterRegistry::class.java, { registry })
            .withBean(ImageStorage::class.java, { alreadyWrapped })
            .run { ctx ->
                val storage = ctx.getBean(ImageStorage::class.java)
                storage shouldBeSameInstanceAs alreadyWrapped
                storage shouldBeInstanceOf MetricImageStorageWithMetadata::class
                storage shouldBeInstanceOf ImageObjectMetadataReader::class
            }
    }
}
