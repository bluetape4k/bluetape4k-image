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

val bluetape4kDependenciesCatalogRef = providers.gradleProperty("bluetape4kDependenciesCatalogRef")
    .orElse(providers.environmentVariable("BLUETAPE4K_DEPENDENCIES_CATALOG_REF"))
    .orElse("catalog/2026-05-26-01")
    .get()

fun resolveBluetape4kDependenciesCatalogFile(): File {
    providers.gradleProperty("bluetape4kDependenciesCatalogPath")
        .orElse(providers.environmentVariable("BLUETAPE4K_DEPENDENCIES_CATALOG_PATH"))
        .orNull
        ?.let(::file)
        ?.let { return it }

    listOf(
        "../bluetape4k-dependencies/gradle/libs.versions.toml",
        "bluetape4k-dependencies/gradle/libs.versions.toml",
    ).map(::file).firstOrNull { it.isFile }?.let { return it }

    val catalogFile = file(".gradle/bluetape4k-dependencies/libs.versions.toml")
    if (!catalogFile.isFile) {
        catalogFile.parentFile.mkdirs()
        val catalogUrl =
            "https://raw.githubusercontent.com/bluetape4k/bluetape4k-dependencies/$bluetape4kDependenciesCatalogRef/gradle/libs.versions.toml"
        uri(catalogUrl).toURL().openStream().use { input ->
            catalogFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return catalogFile
}

val bluetape4kDependenciesCatalogFile = resolveBluetape4kDependenciesCatalogFile()

require(bluetape4kDependenciesCatalogFile.isFile) {
    "bluetape4k-dependencies catalog not found: $bluetape4kDependenciesCatalogFile. " +
        "Checkout bluetape4k-dependencies at the release-train tag or set bluetape4kDependenciesCatalogPath."
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
    versionCatalogs {
        create("bt4k") {
            from(files(bluetape4kDependenciesCatalogFile))
        }
    }
}

rootProject.name = "bluetape4k-image"

include(
    "bluetape4k-images",
    "bluetape4k-images-captcha",
    "bluetape4k-images-ktor",
    "bluetape4k-images-spring-boot",
    "bluetape4k-images-vips-api",
    "bluetape4k-images-vips-java21",
    "bluetape4k-images-vips-java25",
    "bluetape4k-images-benchmark",
)
project(":bluetape4k-images").projectDir = file("images")
project(":bluetape4k-images-captcha").projectDir = file("images-captcha")
project(":bluetape4k-images-ktor").projectDir = file("images-ktor")
project(":bluetape4k-images-spring-boot").projectDir = file("images-spring-boot")
project(":bluetape4k-images-vips-api").projectDir = file("images-vips-api")
project(":bluetape4k-images-vips-java21").projectDir = file("images-vips-java21")
project(":bluetape4k-images-vips-java25").projectDir = file("images-vips-java25")
project(":bluetape4k-images-benchmark").projectDir = file("images-benchmark")

include("bluetape4k-image-bom")
project(":bluetape4k-image-bom").projectDir = file("bom")

include("basic-processing")
project(":basic-processing").projectDir = file("examples/basic-processing")
