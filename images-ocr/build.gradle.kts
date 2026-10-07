configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    systemProperty("ocr.enabled", System.getProperty("ocr.enabled", "false"))
    systemProperty("ocr.container.enabled", System.getProperty("ocr.container.enabled", "false"))
    systemProperty("ocr.container.reuse", System.getProperty("ocr.container.reuse", "false"))
}

// atomicfu rewrites Kotlin tests into a dedicated output directory. Keep the
// regular Java test output visible so the Java ABI test remains discoverable.
val javaTestClasses = tasks.named<JavaCompile>("compileTestJava").flatMap { it.destinationDirectory }
tasks.named<Test>("test") {
    testClassesDirs = project.files(testClassesDirs, javaTestClasses)
    classpath = project.files(classpath, javaTestClasses)
}

dependencies {
    // OCR 라이브리러
    api(bt4k.tess4j)

    api(project(":bluetape4k-images"))

    // Coroutines
    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // Test & Testcontainers
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.testcontainers)
}
