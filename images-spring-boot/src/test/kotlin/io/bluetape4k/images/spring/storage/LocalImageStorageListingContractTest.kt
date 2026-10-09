package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LocalImageStorageListingContractTest: AbstractImageStorageListingContractTest() {

    companion object: KLoggingChannel()

    @TempDir
    private lateinit var root: Path

    override val storage: ImageStorage by lazy {
        LocalImageStorage(rootDir = root, maxSizeBytes = 1024L)
    }

    override suspend fun prepareUpload(key: ImageObjectKey) {
        withContext(Dispatchers.IO) {
            Files.createDirectories(root.resolve(key.fullKey).parent)
        }
    }

    override fun resetListObservations() = Unit

    override fun listInvocationCount(): Int? = null

    override fun listEmissionCount(): Int? = null

    override fun listEnumerationCount(): Int? = null

    override fun assertNoOpenListResources() = Unit
}
