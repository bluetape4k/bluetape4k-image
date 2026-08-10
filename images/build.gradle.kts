configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    api(bt4k.bluetape4k.core)
    api(bt4k.bluetape4k.io)
    api(bt4k.bluetape4k.okio)
    testImplementation(bt4k.bluetape4k.junit5)
    // JDK 25에서 StructuredTaskScope 테스트를 실행할 provider
    testRuntimeOnly(bt4k.bluetape4k.virtualthread.jdk25)

    // Images
    // https://mvnrepository.com/artifact/com.sksamuel.scrimage/scrimage-core
    api(bt4k.scrimage.core)
    api(libs.scrimage.filters)
    implementation(libs.scrimage.webp)

    // EXIF metadata (required runtime dependency)
    implementation(bt4k.metadata.extractor)

    // TIFF support via TwelveMonkeys ImageIO (auto-registers via SPI)
    api(bt4k.twelvemonkeys.imageio.tiff)
    api(bt4k.twelvemonkeys.imageio.metadata)

    // SVG rasterization via Apache Batik (opt-in; add to your own dependencies if needed)
    compileOnly(bt4k.batik.transcoder)
    compileOnly(bt4k.batik.codec)
    testImplementation(bt4k.batik.transcoder)
    testImplementation(bt4k.batik.codec)

    // Coroutines
    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
