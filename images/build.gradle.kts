configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    api(bt4k.bluetape4k.core)
    api(bt4k.bluetape4k.io)
    api(bt4k.bluetape4k.okio)
    testImplementation(bt4k.bluetape4k.junit5)

    // Images
    // https://mvnrepository.com/artifact/com.sksamuel.scrimage/scrimage-core
    api(bt4k.scrimage.core)
    api(libs.scrimage.filters)
    implementation(libs.scrimage.webp)

    // EXIF metadata (required runtime dependency)
    implementation(libs.metadata.extractor)

    // TIFF support via TwelveMonkeys ImageIO (auto-registers via SPI)
    api(libs.twelvemonkeys.imageio.tiff)
    api(libs.twelvemonkeys.imageio.metadata)

    // SVG rasterization via Apache Batik (opt-in; add to your own dependencies if needed)
    compileOnly(libs.batik.transcoder)
    compileOnly(libs.batik.codec)
    testImplementation(libs.batik.transcoder)
    testImplementation(libs.batik.codec)

    // Coroutines
    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
