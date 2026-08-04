plugins {
    application
    alias(bt4k.plugins.kotlin.spring)
    alias(bt4k.plugins.spring.boot)
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-ocr"))
    implementation(project(":bluetape4k-images-barcode-zxing"))
    implementation(bt4k.bluetape4k.workflow)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.scrimage.webp)
    testImplementation(bt4k.zxing.core)
    testImplementation(bt4k.zxing.javase)
}

application {
    mainClass.set(
        "io.bluetape4k.images.examples.spring.intelligence.ImageIntelligenceApiApplicationKt",
    )
}

springBoot {
    mainClass.set(
        "io.bluetape4k.images.examples.spring.intelligence.ImageIntelligenceApiApplicationKt",
    )
}
