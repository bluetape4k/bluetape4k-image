plugins {
    application
    alias(bt4k.plugins.kotlin.spring)
    alias(bt4k.plugins.spring.boot)
}

application {
    mainClass.set("io.bluetape4k.images.examples.spring.SpringBootImageApiApplicationKt")
}

springBoot {
    mainClass.set("io.bluetape4k.images.examples.spring.SpringBootImageApiApplicationKt")
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-spring-boot"))
    implementation(libs.spring.boot.starter.web)

    // Coroutines
    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.kotlinx.coroutines.test)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(bt4k.bluetape4k.junit5)
}
