configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

dependencies {
    api(project(":bluetape4k-images"))

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
