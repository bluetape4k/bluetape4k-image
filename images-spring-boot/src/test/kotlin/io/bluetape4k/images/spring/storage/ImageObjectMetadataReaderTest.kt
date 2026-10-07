package io.bluetape4k.images.spring.storage

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

class ImageObjectMetadataReaderTest {

    companion object: KLogging()

    @Test
    fun `metadata capability stays separate from ImageStorage contract`() {
        ImageStorage::class.java.methods.none { it.name == "readMetadata" }.shouldBeTrue()
        ImageObjectMetadataReader::class.java.methods.any { it.name == "readMetadata" }.shouldBeTrue()
        ImageStorage::class.java.isAssignableFrom(ImageObjectMetadataReader::class.java).shouldBeFalse()
    }
}
