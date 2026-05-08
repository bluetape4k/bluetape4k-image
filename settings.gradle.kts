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
    "images",
    "images-vips-api",
    "images-vips-java21",
    "images-vips-java25",
    "images-benchmark",
)

include("bluetape4k-image-bom")
project(":bluetape4k-image-bom").projectDir = file("bom")
