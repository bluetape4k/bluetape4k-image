plugins {
    alias(libs.plugins.kotlin.serialization)
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    api(project(":bluetape4k-images"))
    api(project(":bluetape4k-images-captcha"))
    api(libs.bluetape4k.ktor.core)
    api(libs.ktor.server.core)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.ktor.testing)
}
