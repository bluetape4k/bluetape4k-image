plugins {
    `java-test-fixtures`
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

sourceSets {
    val main by getting
    val unoptedVipsOptInFixture by creating {
        compileClasspath += main.output
        compileClasspath += main.compileClasspath
        runtimeClasspath += main.output
    }
    val optedVipsOptInFixture by creating {
        compileClasspath += main.output
        compileClasspath += main.compileClasspath
        runtimeClasspath += main.output
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileUnoptedVipsOptInFixtureKotlin") {
    compilerOptions.allWarningsAsErrors.set(true)
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileOptedVipsOptInFixtureKotlin") {
    compilerOptions.allWarningsAsErrors.set(true)
}

dependencies {
    api(libs.bluetape4k.core)
    api(libs.bluetape4k.io)
    api(libs.bluetape4k.okio)
    // Consumers need @IncubatingImageApi annotation transitively
    api(project(":bluetape4k-images"))
    api(libs.bluetape4k.coroutines)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Test Fixtures — VipsGoldenAssert needs scrimage pixel comparison + JUnit5
    testFixturesApi(project(":bluetape4k-images"))
    testFixturesImplementation(libs.bluetape4k.junit5)
    testFixturesImplementation(libs.junit.jupiter.api)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
