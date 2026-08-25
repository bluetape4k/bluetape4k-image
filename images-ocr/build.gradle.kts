import org.gradle.api.tasks.compile.JavaCompile

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
    api(project(":bluetape4k-images"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(bt4k.tess4j)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
}
