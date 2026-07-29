package io.bluetape4k.images.vips.testfixtures

/** test fixture의 알려진 dimension입니다. */
object VipsTestFixtures {

    /** JPEG fixture: 800 × 600, blue→red gradient입니다. */
    const val SAMPLE_JPEG = "fixtures/sample.jpg"
    const val SAMPLE_JPEG_WIDTH = 800
    const val SAMPLE_JPEG_HEIGHT = 600

    /** PNG fixture: 640 × 480, green→yellow gradient입니다. */
    const val SAMPLE_PNG = "fixtures/sample.png"
    const val SAMPLE_PNG_WIDTH = 640
    const val SAMPLE_PNG_HEIGHT = 480

    /** WebP fixture: 400 × 300, orange→purple gradient입니다. */
    const val SAMPLE_WEBP = "fixtures/sample.webp"
    const val SAMPLE_WEBP_WIDTH = 400
    const val SAMPLE_WEBP_HEIGHT = 300

    /**
     * classpath resource에서 test fixture를 load합니다.
     *
     * @param resourcePath classpath 기준 relative path입니다(예: "fixtures/sample.jpg").
     * @return resource의 raw bytes입니다.
     * @throws IllegalArgumentException resource를 찾을 수 없으면 던집니다.
     */
    fun loadFixture(resourcePath: String): ByteArray {
        val stream = VipsTestFixtures::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: error("Test fixture not found on classpath: $resourcePath")
        return stream.use { it.readBytes() }
    }
}
