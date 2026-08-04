tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

dependencies {
    api(project(":bluetape4k-images-barcode-api"))

    implementation(bt4k.zxing.core)
    implementation(bt4k.zxing.javase)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(testFixtures(project(":bluetape4k-images-barcode-api")))
}
