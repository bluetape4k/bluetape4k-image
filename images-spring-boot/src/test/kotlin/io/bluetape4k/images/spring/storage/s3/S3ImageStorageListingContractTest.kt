package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.bluetape4k.images.spring.storage.AbstractImageStorageListingContractTest
import io.bluetape4k.images.spring.storage.ImageStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class S3ImageStorageListingContractTest : AbstractImageStorageListingContractTest() {

    private val operations = StatefulS3Operations()

    override val storage: ImageStorage = S3ImageStorage(
        operations = operations,
        properties = ImageStorageProperties(
            backend = ImageStorageProperties.Backend.S3,
            bucket = "contract-images",
            maxSizeBytes = 1024L,
        ),
    )

    override suspend fun prepareUpload(key: ImageObjectKey) = Unit

    override fun resetListObservations() = operations.resetListObservations()

    override fun listInvocationCount(): Int = operations.listInvocationCount()

    override fun listEmissionCount(): Int = operations.listEmissionCount()

    override fun listEnumerationCount(): Int = operations.listEnumerationCount()

    override fun assertNoOpenListResources() {
        operations.hasOpenListCollectors().not().shouldBeTrue()
    }

    @Test
    fun `backend CancellationException을 같은 type과 message로 전파하고 collector를 정리한다`() = runTest {
        val prefix = ImageObjectKey.of("contract", "backend-cancel")
        val cancellation = CancellationException("contract backend cancelled")
        operations.cancelNextList(cancellation)

        val thrown = assertFailsWith<CancellationException> {
            storage.list(prefix).toList()
        }

        thrown::class shouldBeEqualTo cancellation::class
        thrown.message shouldBeEqualTo cancellation.message
        operations.listInvocationCount() shouldBeEqualTo 1
        operations.listEmissionCount() shouldBeEqualTo 0
        operations.listEnumerationCount() shouldBeEqualTo 0
        assertNoOpenListResources()
    }
}
