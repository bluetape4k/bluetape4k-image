plugins {
    application
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-ktor"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.logback)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}

application {
    mainClass.set("io.bluetape4k.images.examples.ktor.KtorImageApiApplicationKt")
}
