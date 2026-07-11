configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    systemProperty("ocr.enabled", System.getProperty("ocr.enabled", "false"))
    systemProperty("ocr.container.enabled", System.getProperty("ocr.container.enabled", "false"))
    systemProperty("ocr.container.reuse", System.getProperty("ocr.container.reuse", "false"))
}

dependencies {
    api(project(":bluetape4k-images"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.tess4j)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
}
