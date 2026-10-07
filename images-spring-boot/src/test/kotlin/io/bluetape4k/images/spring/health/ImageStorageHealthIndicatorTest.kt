package io.bluetape4k.images.spring.health

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status

class ImageStorageHealthIndicatorTest {

    companion object: KLogging()

    private val storage = mockk<ImageStorage>(relaxed = true)
    private val indicator = ImageStorageHealthIndicator(storage, ".probe")

    @BeforeEach
    fun beforeEach() {
        clearMocks(storage)
    }

    @Test
    fun `returns Health up when storage is reachable`() {
        coEvery { storage.exists(any()) } returns true

        val health = indicator.health().block().shouldNotBeNull()
        log.debug { "health=$health" }
        health.status shouldBeEqualTo Status.UP
    }

    @Test
    fun `returns Health down when storage throws exception`() {
        coEvery { storage.exists(any()) } throws ImageStorageException.TransientException()

        val health = indicator.health().block().shouldNotBeNull()
        log.debug { "health=$health" }
        health.status shouldBeEqualTo Status.DOWN
        health.details["error"].shouldNotBeNull()
    }

    @Test
    fun `returns Health up even when exists returns false`() {
        // indicator는 object 존재 자체를 요구하지 않고 exists() 호출 가능성으로 reachability를 확인합니다.
        // false return은 health-probe object가 없다는 뜻이며 storage는 reachable한 상태입니다.
        coEvery { storage.exists(any()) } returns false

        val health = indicator.health().block().shouldNotBeNull()
        log.debug { "health=$health" }
        health.status shouldBeEqualTo Status.UP
    }

    @Test
    fun `probes using the configured probeKey`() = runTest {
        val expectedKey = ImageObjectKey.of("_health", ".probe")
        coEvery { storage.exists(expectedKey) } returns true

        indicator.health().awaitSingleOrNull().shouldNotBeNull()

        coVerify { storage.exists(expectedKey) }
    }
}
