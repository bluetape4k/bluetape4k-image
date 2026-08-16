package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LocalImageStorageCrudContractTest : AbstractImageStorageCrudContractTest() {

    @field:TempDir
    private lateinit var root: Path

    private val localStorage: ImageStorage by lazy {
        LocalImageStorage(
            rootDir = root,
            maxSizeBytes = 1024L,
        )
    }

    override val storage: ImageStorage
        get() = localStorage

    override suspend fun prepareUpload(key: ImageObjectKey) {
        Files.createDirectories(root.resolve(key.fullKey).parent)
    }
}
