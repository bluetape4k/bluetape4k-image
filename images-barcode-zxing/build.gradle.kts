tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

dependencies {
    api(project(":bluetape4k-images-barcode-api"))

    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(testFixtures(project(":bluetape4k-images-barcode-api")))
}
