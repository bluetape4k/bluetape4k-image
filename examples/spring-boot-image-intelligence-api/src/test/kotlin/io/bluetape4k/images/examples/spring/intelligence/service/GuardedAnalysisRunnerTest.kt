package io.bluetape4k.images.examples.spring.intelligence.service

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuardedAnalysisRunnerTest {

    private val runner = GuardedAnalysisRunner()

    @Test
    fun `maps completed empty unavailable and failed outcomes`() = runTest {
        val semaphore = Semaphore(1)

        val completed = runner.run("fixture", Duration.ofSeconds(1), semaphore) { "value" }
        val empty = runner.run(
            provider = "fixture",
            timeout = Duration.ofSeconds(1),
            semaphore = semaphore,
            isEmpty = { it.isEmpty() },
        ) { "" }
        val unavailable = runner.run<String>("disabled", Duration.ofSeconds(1), semaphore) {
            throw ProviderUnavailableException("provider_not_configured")
        }
        val failed = runner.run<String>("broken", Duration.ofSeconds(1), semaphore) {
            error("raw-secret")
        }

        completed.shouldBeInstanceOf<AnalysisResult.Completed<String>>()
            .value shouldBeEqualTo "value"
        empty shouldBeInstanceOf AnalysisResult.Empty::class
        unavailable.shouldBeInstanceOf<AnalysisResult.Unavailable>()
            .reasonCode shouldBeEqualTo "provider_not_configured"
        failed.shouldBeInstanceOf<AnalysisResult.Failed>()
            .reasonCode shouldBeEqualTo "provider_failure"
        failed.elapsedMillis shouldBeGreaterThan -1L
    }

    @Test
    fun `maps only the local timeout to failed`() = runTest {
        val result = runner.run<String>(
            provider = "slow",
            timeout = Duration.ofMillis(100),
            semaphore = Semaphore(1),
        ) {
            delay(200)
            "late"
        }

        result.shouldBeInstanceOf<AnalysisResult.Failed>()
            .reasonCode shouldBeEqualTo "timeout"
    }

    @Test
    fun `rethrows external cancellation`() = runTest {
        val deferred = async {
            runner.run<String>(
                provider = "cancelled",
                timeout = Duration.ofSeconds(10),
                semaphore = Semaphore(1),
            ) {
                awaitCancellation()
            }
        }
        runCurrent()

        deferred.cancel(CancellationException("caller-cancelled"))

        val cancelled = assertFailsWith<CancellationException> {
            deferred.await()
        }
        cancelled.message shouldBeEqualTo "caller-cancelled"
    }

    @Test
    fun `bounds concurrent provider entries`() = runTest {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val semaphore = Semaphore(2)

        List(6) {
            async {
                runner.run(
                    provider = "bounded",
                    timeout = Duration.ofSeconds(1),
                    semaphore = semaphore,
                ) {
                    val current = active.incrementAndGet()
                    maximum.updateAndGet { previous -> maxOf(previous, current) }
                    try {
                        delay(100)
                        current
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }
        }.awaitAll()

        maximum.get() shouldBeEqualTo 2
        semaphore.availablePermits shouldBeEqualTo 2
    }

    @Test
    fun `releases permit after failure timeout and cancellation`() = runTest {
        val semaphore = Semaphore(1)

        runner.run<Unit>("failed", Duration.ofSeconds(1), semaphore) {
            error("failure")
        }
        runner.run<Unit>("timeout", Duration.ofMillis(10), semaphore) {
            delay(20)
        }
        val cancelled = async {
            runner.run<Unit>("cancelled", Duration.ofSeconds(1), semaphore) {
                awaitCancellation()
            }
        }
        runCurrent()
        cancelled.cancel()
        assertFailsWith<CancellationException> {
            cancelled.await()
        }

        val subsequent = runner.run("subsequent", Duration.ofSeconds(1), semaphore) {
            "ok"
        }

        subsequent.shouldBeInstanceOf<AnalysisResult.Completed<String>>()
            .value shouldBeEqualTo "ok"
        semaphore.availablePermits shouldBeEqualTo 1
    }

    @Test
    fun `rejects blank provider and sub-millisecond timeout`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            runner.run("", Duration.ofSeconds(1), Semaphore(1)) { "value" }
        }
        assertFailsWith<IllegalArgumentException> {
            runner.run("fixture", Duration.ofNanos(1), Semaphore(1)) { "value" }
        }
    }
}
