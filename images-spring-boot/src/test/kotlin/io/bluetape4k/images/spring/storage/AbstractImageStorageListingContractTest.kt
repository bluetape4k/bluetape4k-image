package io.bluetape4k.images.spring.storage

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeLessOrEqualTo
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.UploadOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/** [ImageStorage.list] 구현체가 공유하는 cold Flow와 cancellation 계약입니다. */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
abstract class AbstractImageStorageListingContractTest {

    protected abstract val storage: ImageStorage

    protected abstract suspend fun prepareUpload(key: ImageObjectKey)

    protected abstract fun resetListObservations()

    protected abstract fun listInvocationCount(): Int?

    protected abstract fun listEmissionCount(): Int?

    protected abstract fun listEnumerationCount(): Int?

    protected abstract fun assertNoOpenListResources()

    private val options = UploadOptions(contentType = "image/jpeg")

    @Test
    fun `list는 collection 시점의 prefix 결과만 방출하는 cold Flow다`() = runTest {
        val prefix = ImageObjectKey.of("contract", "listing")
        val first = ImageObjectKey.of("contract/listing", "first.jpg")
        val late = ImageObjectKey.of("contract/listing", "late.jpg")
        val outside = ImageObjectKey.of("contract/outside", "ignored.jpg")
        val siblingPrefix = ImageObjectKey.of("contract/listing-other", "ignored.jpg")
        prepareAndUpload(first)
        resetListObservations()

        val listing = storage.list(prefix)
        listInvocationCount()?.shouldBeEqualTo(0)
        prepareAndUpload(late)
        prepareAndUpload(outside)
        prepareAndUpload(siblingPrefix)

        val listed = listing.toList()

        listed.map(ImageObjectKey::fullKey).sorted() shouldBeEqualTo
            listOf(first.fullKey, late.fullKey).sorted()
        listInvocationCount()?.shouldBeEqualTo(1)
        listEmissionCount()?.shouldBeEqualTo(2)
        listEnumerationCount()?.shouldBeEqualTo(2)
        assertNoOpenListResources()
    }

    @Test
    fun `take one cancellation은 전체 결과 materialization 없이 resource를 정리한다`() = runTest {
        val prefix = ImageObjectKey.of("contract", "take-one")
        val keys = (1..100).map { index ->
            ImageObjectKey.of("contract/take-one", "image-$index.jpg")
        }
        keys.forEach { prepareAndUpload(it) }
        resetListObservations()

        val listed = storage.list(prefix).take(1).toList()

        listed.size shouldBeEqualTo 1
        listEmissionCount()?.shouldBeLessOrEqualTo(2)
        listEnumerationCount()?.shouldBeLessOrEqualTo(2)
        assertNoOpenListResources()
    }

    @Test
    fun `collector CancellationException을 같은 type과 message로 전파하고 resource를 정리한다`() = runTest {
        val prefix = ImageObjectKey.of("contract", "collector-cancel")
        val key = ImageObjectKey.of("contract/collector-cancel", "first.jpg")
        prepareAndUpload(key)
        resetListObservations()
        val cancellation = CancellationException("contract collector cancelled")

        val thrown = assertFailsWith<CancellationException> {
            storage.list(prefix).collect { throw cancellation }
        }

        thrown::class shouldBeEqualTo cancellation::class
        thrown.message shouldBeEqualTo cancellation.message
        listEmissionCount()?.shouldBeEqualTo(1)
        assertNoOpenListResources()
    }

    private suspend fun prepareAndUpload(key: ImageObjectKey) {
        prepareUpload(key)
        storage.upload(key, key.fullKey.toByteArray(), options)
    }
}
