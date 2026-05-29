plugins {
    application
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-spring-boot"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.bluetape4k.junit5)
}

application {
    mainClass.set("io.bluetape4k.images.examples.spring.SpringBootImageApiApplicationKt")
}

springBoot {
    mainClass.set("io.bluetape4k.images.examples.spring.SpringBootImageApiApplicationKt")
}
