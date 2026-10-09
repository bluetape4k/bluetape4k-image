plugins {
    application
    alias(bt4k.plugins.kotlin.spring)
    alias(bt4k.plugins.spring.boot)
}

application {
    mainClass.set("io.bluetape4k.images.examples.spring.ocr.SpringBootOcrApiApplicationKt")
}

springBoot {
    mainClass.set("io.bluetape4k.images.examples.spring.ocr.SpringBootOcrApiApplicationKt")
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-ocr"))
    implementation(libs.spring.boot.starter.web)

    // Coroutines
    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(bt4k.bluetape4k.junit5)
}
