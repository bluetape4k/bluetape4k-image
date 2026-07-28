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
        // testImplementation 때문에 MeterRegistry class가 classpath에 있어 @ConditionalOnClass가 통과하고
        // MetricsDecorationConfiguration이 활성화됩니다. @Bean factory는 MeterRegistry bean을 요구하므로,
        // bean이 없으면 Spring은 UnsatisfiedDependencyException을 발생시킵니다.
        // consumer가 MeterRegistry를 제공해야 한다는 의도된 sharp edge입니다.
        contextRunner.run { ctx ->
            assertThat(ctx).hasFailed()
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
                assertThat(ctx.getBean(ImageStorage::class.java))
                    .isNotInstanceOf(MetricImageStorage::class.java)
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
                assertThat(storage).isInstanceOf(MetricImageStorage::class.java)
                // delegate가 또 다른 MetricImageStorage가 아니라 LocalImageStorage인지 확인합니다.
                val delegate = MetricImageStorage::class.java
                    .getDeclaredField("delegate")
                    .also { it.isAccessible = true }
                    .get(storage)
                assertThat(delegate).isNotInstanceOf(MetricImageStorage::class.java)
                assertThat(delegate).isInstanceOf(LocalImageStorage::class.java)
            }
    }
}
