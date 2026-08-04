plugins {
    application
    alias(bt4k.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-ktor"))
    implementation(bt4k.bluetape4k.ktor.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    runtimeOnly(bt4k.logback)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.ktor.testing)
}

application {
    mainClass.set("io.bluetape4k.images.examples.ktor.KtorImageApiApplicationKt")
}
