plugins {
    alias(libs.plugins.kotlin.serialization)
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    api(project(":bluetape4k-images"))
    api(project(":bluetape4k-images-captcha"))
    api(libs.ktor.server.core)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.server.status.pages)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}
