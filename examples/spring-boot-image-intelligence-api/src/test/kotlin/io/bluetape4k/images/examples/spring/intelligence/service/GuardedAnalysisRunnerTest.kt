package io.bluetape4k.images.examples.spring.intelligence.service

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.coroutines.support.log
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import io.bluetape4k.javatimes.millis
import io.bluetape4k.javatimes.nanos
import io.bluetape4k.javatimes.seconds
import io.bluetape4k.logging.coroutines.KLoggingChannel
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuardedAnalysisRunnerTest {

    companion object: KLoggingChannel()

    private val runner = GuardedAnalysisRunner()

    @Test
    fun `maps completed empty unavailable and failed outcomes`() = runTest {
        val semaphore = Semaphore(1)

        val completed = runner.run("fixture", 1.seconds(), semaphore) { "value" }
        val empty = runner.run(
            provider = "fixture",
            timeout = 1.seconds(),
            semaphore = semaphore,
            isEmpty = { it.isEmpty() },
        ) { "" }
        val unavailable = runner.run<String>("disabled", 1.seconds(), semaphore) {
            throw ProviderUnavailableException("provider_not_configured")
        }
        val failed = runner.run<String>("broken", 1.seconds(), semaphore) {
            error("raw-secret")
        }

        completed.shouldBeInstanceOf<AnalysisResult.Completed<String>>().value shouldBeEqualTo "value"
        empty.shouldBeInstanceOf<AnalysisResult.Empty>()

        unavailable.shouldBeInstanceOf<AnalysisResult.Unavailable>().reasonCode shouldBeEqualTo "provider_not_configured"
        failed.shouldBeInstanceOf<AnalysisResult.Failed>().reasonCode shouldBeEqualTo "provider_failure"
        failed.elapsedMillis shouldBeGreaterThan -1L
    }

    @Test
    fun `maps only the local timeout to failed`() = runTest {
        val result = runner.run(
            provider = "slow",
            timeout = 200.millis(),
            semaphore = Semaphore(1),
        ) {
            delay(200.milliseconds)
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
                timeout = 10.seconds(),
                semaphore = Semaphore(1),
            ) {
                awaitCancellation()
            }
        }.log("Cancelled")
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
                    timeout = 1.seconds(),
                    semaphore = semaphore,
                ) {
                    val current = active.incrementAndGet()
                    maximum.updateAndGet { previous -> maxOf(previous, current) }
                    try {
                        delay(100.milliseconds)
                        current
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }.log("Job #$it")
        }.awaitAll()

        maximum.get() shouldBeEqualTo 2
        semaphore.availablePermits shouldBeEqualTo 2
    }

    @Test
    fun `releases permit after failure timeout and cancellation`() = runTest {
        val semaphore = Semaphore(1)

        runner.run<Unit>("failed", 1.seconds(), semaphore) {
            error("failure")
        }
        runner.run("timeout", 10.millis(), semaphore) {
            delay(20.milliseconds)
        }
        val cancelled = async {
            runner.run<Unit>("cancelled", 1.seconds(), semaphore) {
                awaitCancellation()
            }
        }
        runCurrent()
        cancelled.cancel()
        assertFailsWith<CancellationException> {
            cancelled.await()
        }

        val subsequent = runner.run("subsequent", 1.seconds(), semaphore) {
            "ok"
        }

        subsequent.shouldBeInstanceOf<AnalysisResult.Completed<String>>()
            .value shouldBeEqualTo "ok"
        semaphore.availablePermits shouldBeEqualTo 1
    }

    @Test
    fun `rejects blank provider and sub-millisecond timeout`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            runner.run("", 1.seconds(), Semaphore(1)) { "value" }
        }
        assertFailsWith<IllegalArgumentException> {
            runner.run("fixture", 1.nanos(), Semaphore(1)) { "value" }
        }
    }
}
