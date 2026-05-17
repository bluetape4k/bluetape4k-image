package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.images.spring.metrics.MetricImageStorage
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.images.spring.storage.LocalImageStorage
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.AutoConfigurations
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

    @Test
    fun `wraps ImageStorage in MetricImageStorage when MeterRegistry bean present`() {
        contextRunner
            .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ImageStorage::class.java)
                assertThat(ctx.getBean(ImageStorage::class.java))
                    .isInstanceOf(MetricImageStorage::class.java)
            }
    }

    @Test
    fun `context fails when Micrometer is on classpath but no MeterRegistry bean is registered`() {
        // MeterRegistry class is on the classpath (testImplementation) so @ConditionalOnClass
        // passes and MetricsDecorationConfiguration activates. The @Bean factory requires a
        // MeterRegistry bean; without one Spring raises UnsatisfiedDependencyException.
        // This is the expected sharp edge: consumers must supply a MeterRegistry.
        contextRunner.run { ctx ->
            assertThat(ctx).hasFailed()
        }
    }

    @Test
    fun `disabled when metrics enabled=false`() {
        // Even with a MeterRegistry present, disabling metrics skips the BPP entirely.
        contextRunner
            .withPropertyValues("bluetape4k.images.metrics.enabled=false")
            .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ImageStorage::class.java)
                assertThat(ctx.getBean(ImageStorage::class.java))
                    .isNotInstanceOf(MetricImageStorage::class.java)
            }
    }

    @Test
    fun `does not double-wrap a pre-existing MetricImageStorage bean`() {
        // Supply a MetricImageStorage bean directly; the BPP must skip it (checks bean !is MetricImageStorage).
        val registry = SimpleMeterRegistry()
        val inner = LocalImageStorage(tempDir, 10 * 1024 * 1024L)
        val alreadyWrapped = MetricImageStorage(delegate = inner, registry = registry)

        contextRunner
            .withBean(MeterRegistry::class.java, { registry })
            .withBean(ImageStorage::class.java, { alreadyWrapped })
            .run { ctx ->
                val storage = ctx.getBean(ImageStorage::class.java)
                assertThat(storage).isInstanceOf(MetricImageStorage::class.java)
                // Confirm the delegate is LocalImageStorage, not another MetricImageStorage.
                val delegate = MetricImageStorage::class.java
                    .getDeclaredField("delegate")
                    .also { it.isAccessible = true }
                    .get(storage)
                assertThat(delegate).isNotInstanceOf(MetricImageStorage::class.java)
                assertThat(delegate).isInstanceOf(LocalImageStorage::class.java)
            }
    }
}
