pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
    }
}

rootProject.name = "bluetape4k-image"

include(
    "bluetape4k-images",
    "bluetape4k-images-spring-boot",
    "bluetape4k-images-vips-api",
    "bluetape4k-images-vips-java21",
    "bluetape4k-images-vips-java25",
    "bluetape4k-images-benchmark",
)
project(":bluetape4k-images").projectDir = file("images")
project(":bluetape4k-images-spring-boot").projectDir = file("images-spring-boot")
project(":bluetape4k-images-vips-api").projectDir = file("images-vips-api")
project(":bluetape4k-images-vips-java21").projectDir = file("images-vips-java21")
project(":bluetape4k-images-vips-java25").projectDir = file("images-vips-java25")
project(":bluetape4k-images-benchmark").projectDir = file("images-benchmark")

include("bluetape4k-image-bom")
project(":bluetape4k-image-bom").projectDir = file("bom")
