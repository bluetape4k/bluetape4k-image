plugins {
    application
    alias(bt4k.plugins.kotlin.spring)
    alias(bt4k.plugins.spring.boot)
}

dependencies {
    implementation(project(":bluetape4k-images-barcode-zxing"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.scrimage.webp)
}

application {
    mainClass.set("io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationKt")
}

springBoot {
    mainClass.set("io.bluetape4k.images.examples.spring.barcode.SpringBootBarcodeApiApplicationKt")
}
