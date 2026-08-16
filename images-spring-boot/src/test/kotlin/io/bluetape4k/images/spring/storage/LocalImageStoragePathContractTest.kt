package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.UploadOptions
import java.nio.file.Files
import java.nio.file.Path

class LocalImageStoragePathContractTest : AbstractImageStoragePathContractTest() {

    private val root: Path by lazy { contractDir.resolve("local-storage") }

    override val storage: ImageStorage by lazy {
        LocalImageStorage(rootDir = root, maxSizeBytes = MAX_SIZE_BYTES)
    }

    override suspend fun prepareUpload(key: ImageObjectKey) {
        Files.createDirectories(root.resolve(key.fullKey).parent)
    }

    override suspend fun seedStoredObject(key: ImageObjectKey, bytes: ByteArray) {
        prepareUpload(key)
        LocalImageStorage(rootDir = root, maxSizeBytes = bytes.size.toLong())
            .upload(key, bytes, UploadOptions(contentType = "image/jpeg"))
    }

    override fun assertNoOpenResources() = Unit

    override fun stagedArtifacts(): List<Path> =
        if (Files.notExists(root)) {
            emptyList()
        } else {
            Files.walk(root).use { paths ->
                paths.filter { path ->
                    path.fileName.toString().contains(".upload") ||
                        path.fileName.toString().contains(".download")
                }.toList()
            }
        }
}
