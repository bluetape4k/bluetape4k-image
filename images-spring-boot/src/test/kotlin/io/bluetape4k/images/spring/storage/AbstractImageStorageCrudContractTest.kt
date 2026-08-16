package io.bluetape4k.images.spring.storage

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.UploadOptions
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * [ImageStorage] 구현체가 공유하는 기본 CRUD 계약입니다.
 *
 * backend별 concrete test는 [storage]와 필요한 사전 준비만 제공하고, 동일한 테스트 목록을 상속해
 * 공개 계약의 assertion drift를 방지합니다.
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
abstract class AbstractImageStorageCrudContractTest {

    protected abstract val storage: ImageStorage

    protected open suspend fun prepareUpload(key: ImageObjectKey) = Unit

    private val options = UploadOptions(contentType = "image/jpeg")

    @Test
    fun `byte upload 결과를 동일한 key에서 download한다`() = runTest {
        val key = contractKey("round-trip.jpg")
        val bytes = "contract-round-trip".toByteArray()
        prepareUpload(key)

        val uploaded = storage.upload(key, bytes, options)

        uploaded.key shouldBeEqualTo key
        uploaded.sizeBytes shouldBeEqualTo bytes.size.toLong()
        uploaded.contentType shouldBeEqualTo options.contentType
        storage.download(key).contentEquals(bytes).shouldBeTrue()
    }

    @Test
    fun `기존 object를 byte upload로 교체한다`() = runTest {
        val key = contractKey("overwrite.jpg")
        val original = "original".toByteArray()
        val replacement = "replacement".toByteArray()
        prepareUpload(key)
        storage.upload(key, original, options)

        storage.upload(key, replacement, options)

        storage.download(key).contentEquals(replacement).shouldBeTrue()
    }

    @Test
    fun `upload 전후 exists 상태를 반영한다`() = runTest {
        val key = contractKey("exists.jpg")
        storage.exists(key).shouldBeFalse()
        prepareUpload(key)

        storage.upload(key, "exists".toByteArray(), options)

        storage.exists(key).shouldBeTrue()
    }

    @Test
    fun `delete는 기존 object를 제거하고 missing key에도 idempotent하다`() = runTest {
        val key = contractKey("delete.jpg")
        prepareUpload(key)
        storage.upload(key, "delete".toByteArray(), options)

        storage.delete(key)
        storage.delete(key)

        storage.exists(key).shouldBeFalse()
    }

    @Test
    fun `missing key download는 NotFoundException을 던진다`() = runTest {
        val key = contractKey("missing.jpg")

        val error = assertFailsWith<ImageStorageException.NotFoundException> {
            storage.download(key)
        }

        error.key shouldBeEqualTo key
    }

    private fun contractKey(name: String): ImageObjectKey =
        ImageObjectKey.of("contract/crud", name)
}
