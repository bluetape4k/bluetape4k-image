configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

dependencies {
    api(project(":bluetape4k-images"))

    // Coroutines
    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // Test
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.fory.kotlin)
}
