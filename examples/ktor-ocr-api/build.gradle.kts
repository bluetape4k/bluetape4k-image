plugins {
    application
    alias(bt4k.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-ocr"))
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.serialization.kotlinx.json)
    runtimeOnly(libs.logback)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.server.test.host)
}

application {
    mainClass.set("io.bluetape4k.images.examples.ktor.ocr.KtorOcrApiApplicationKt")
}
