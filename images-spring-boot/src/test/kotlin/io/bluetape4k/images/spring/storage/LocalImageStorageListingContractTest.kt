package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LocalImageStorageListingContractTest : AbstractImageStorageListingContractTest() {

    @field:TempDir
    private lateinit var root: Path

    override val storage: ImageStorage by lazy {
        LocalImageStorage(rootDir = root, maxSizeBytes = 1024L)
    }

    override suspend fun prepareUpload(key: ImageObjectKey) {
        Files.createDirectories(root.resolve(key.fullKey).parent)
    }

    override fun resetListObservations() = Unit

    override fun listInvocationCount(): Int? = null

    override fun listEmissionCount(): Int? = null

    override fun listEnumerationCount(): Int? = null

    override fun assertNoOpenListResources() = Unit
}
