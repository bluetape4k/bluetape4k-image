plugins {
    alias(bt4k.plugins.kotlin.serialization)
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    api(project(":bluetape4k-images"))
    api(project(":bluetape4k-images-captcha"))
    api(bt4k.bluetape4k.ktor.core)
    api(libs.ktor.server.core)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.ktor.testing)
}
