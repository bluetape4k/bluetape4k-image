plugins {
    application
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-ocr"))
    implementation(libs.bluetape4k.ktor.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    runtimeOnly(libs.logback)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.ktor.testing)
    testImplementation(libs.ktor.server.test.host)
}

application {
    mainClass.set("io.bluetape4k.images.examples.ktor.ocr.KtorOcrApiApplicationKt")
}
