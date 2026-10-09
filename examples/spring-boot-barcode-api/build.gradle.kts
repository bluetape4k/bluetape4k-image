plugins {
    application
    alias(bt4k.plugins.kotlin.spring)
    alias(bt4k.plugins.spring.boot)
}

application {
    mainClass.set("io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationKt")
}

springBoot {
    mainClass.set("io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationKt")
}

dependencies {
    implementation(project(":bluetape4k-images-barcode-zxing"))
    implementation(libs.spring.boot.starter.web)

    // Bluetape4k
    implementation(bt4k.bluetape4k.spring.boot.core)

    // Coroutines
    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(bt4k.bluetape4k.junit5)

    // Scrimage
    testImplementation(libs.scrimage.webp)
}
