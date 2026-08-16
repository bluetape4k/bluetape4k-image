package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.images.spring.autoconfigure.ImageStorageProperties
import io.bluetape4k.images.spring.storage.AbstractImageStorageCrudContractTest
import io.bluetape4k.images.spring.storage.ImageStorage

class S3ImageStorageCrudContractTest : AbstractImageStorageCrudContractTest() {

    override val storage: ImageStorage = S3ImageStorage(
        operations = StatefulS3Operations(),
        properties = ImageStorageProperties(
            backend = ImageStorageProperties.Backend.S3,
            bucket = "contract-images",
            maxSizeBytes = 1024L,
        ),
    )
}
