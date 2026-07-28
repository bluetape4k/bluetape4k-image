package io.bluetape4k.images.spring.health

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.storage.ImageStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImageStorageHealthIndicatorTest {

    private val storage = mockk<ImageStorage>()
    private val indicator = ImageStorageHealthIndicator(storage, ".probe")

    @Test
    fun `returns Health up when storage is reachable`() {
        coEvery { storage.exists(any()) } returns true

        val health = indicator.health().block()!!
        assertEquals(Status.UP, health.status)
    }

    @Test
    fun `returns Health down when storage throws exception`() {
        coEvery { storage.exists(any()) } throws ImageStorageException.TransientException()

        val health = indicator.health().block()!!
        assertEquals(Status.DOWN, health.status)
        assertNotNull(health.details["error"])
    }

    @Test
    fun `returns Health up even when exists returns false`() {
        // indicator는 object 존재 자체를 요구하지 않고 exists() 호출 가능성으로 reachability를 확인합니다.
        // false return은 health-probe object가 없다는 뜻이며 storage는 reachable한 상태입니다.
        coEvery { storage.exists(any()) } returns false

        val health = indicator.health().block()!!
        assertEquals(Status.UP, health.status)
    }

    @Test
    fun `probes using the configured probeKey`() {
        val expectedKey = ImageObjectKey.of("_health", ".probe")
        coEvery { storage.exists(expectedKey) } returns true

        indicator.health().block()

        coVerify { storage.exists(expectedKey) }
    }
}
