package io.bluetape4k.images.spring.metrics

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.ImageUploadResult
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.storage.ImageStorage
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MetricImageStorageTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var delegate: ImageStorage
    private lateinit var storage: MetricImageStorage

    private val key = ImageObjectKey.of("test", "file.jpg")
    private val options = UploadOptions()
    private val result = ImageUploadResult(key, "etag-1", 100L, "image/jpeg", Instant.now())

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        delegate = mockk(relaxed = true)
        storage = MetricImageStorage(delegate, registry)
    }

    @Test
    fun `successful upload records timer`() = runTest {
        coEvery { delegate.upload(any(), any<ByteArray>(), any()) } returns result

        storage.upload(key, ByteArray(10), options)

        registry.find("images.storage.upload.duration").timer().shouldNotBeNull().count() shouldBeEqualTo 1L
    }

    @Test
    fun `failed upload records timer and increments error counter`() = runTest {
        val error = ImageStorageException.TransientException(key = key)
        coEvery { delegate.upload(any(), any<ByteArray>(), any()) } throws error

        assertFailsWith<ImageStorageException.TransientException> {
            storage.upload(key, ByteArray(10), options)
        }

        registry.find("images.storage.upload.duration").timer().shouldNotBeNull().count() shouldBeEqualTo 1L
        registry.find("images.storage.upload.errors").counter().shouldNotBeNull().count() shouldBeEqualTo 1.0
    }

    @Test
    fun `CancellationException on upload records timer but does not increment error counter`() = runTest {
        coEvery { delegate.upload(any(), any<ByteArray>(), any()) } throws CancellationException()

        assertFailsWith<CancellationException> {
            storage.upload(key, ByteArray(10), options)
        }

        registry.find("images.storage.upload.duration").timer().shouldNotBeNull().count() shouldBeEqualTo 1L
        val counter = registry.find("images.storage.upload.errors").counter()
        // CancellationException에서는 counter를 증가시키면 안 됩니다.
        (counter == null || counter.count() == 0.0).shouldBeTrue()
    }

    @Test
    fun `successful download records timer`() = runTest {
        coEvery { delegate.download(any()) } returns ByteArray(10)

        storage.download(key)

        registry.find("images.storage.download.duration").timer().shouldNotBeNull().count() shouldBeEqualTo 1L
    }

    @Test
    fun `failed download records timer and increments error counter`() = runTest {
        coEvery { delegate.download(any()) } throws ImageStorageException.NotFoundException(key)

        assertFailsWith<ImageStorageException.NotFoundException> {
            storage.download(key)
        }

        registry.find("images.storage.download.duration").timer().shouldNotBeNull().count() shouldBeEqualTo 1L
        registry.find("images.storage.download.errors").counter().shouldNotBeNull().count() shouldBeEqualTo 1.0
    }

    @Test
    fun `delegates exists to underlying storage`() = runTest {
        coEvery { delegate.exists(key) } returns true
        storage.exists(key).shouldBeTrue()
        coVerify { delegate.exists(key) }
    }
}
