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
    .orElse("d8f18653928dfc24e36b3a1ff980ac08472c821e")
    .get()
val bluetape4kDependenciesCatalogCacheKey = bluetape4kDependenciesCatalogRef.replace(Regex("[^A-Za-z0-9._-]"), "_")

fun catalogSha256(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

fun expectedCatalogSha256(checksumFile: File): String? =
    checksumFile.takeIf(File::isFile)
        ?.readText()
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull()
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

fun catalogChecksumMatches(catalogFile: File, checksumFile: File): Boolean =
    catalogFile.isFile && expectedCatalogSha256(checksumFile)?.let { it == catalogSha256(catalogFile) } == true

fun downloadCatalogFile(url: String, target: File) {
    uri(url).toURL().openStream().use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
}
val centralSnapshotUsername = providers.gradleProperty("central.user")
    .orElse(providers.gradleProperty("centralPortalUsername"))
    .orElse(providers.environmentVariable("CENTRAL_USERNAME"))
    .orNull
val centralSnapshotPassword = providers.gradleProperty("central.password")
    .orElse(providers.gradleProperty("centralPortalPassword"))
    .orElse(providers.environmentVariable("CENTRAL_PASSWORD"))
    .orNull

fun org.gradle.api.artifacts.repositories.MavenArtifactRepository.configureCentralSnapshotCredentials() {
    if (!centralSnapshotUsername.isNullOrBlank() && !centralSnapshotPassword.isNullOrBlank()) {
        credentials(org.gradle.api.artifacts.repositories.PasswordCredentials::class) {
            username = centralSnapshotUsername
            password = centralSnapshotPassword
        }
        authentication {
            create<org.gradle.authentication.http.BasicAuthentication>("basic")
        }
    }
}

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

    val catalogFile = file(".gradle/bluetape4k-dependencies/$bluetape4kDependenciesCatalogCacheKey/libs.versions.toml")
    val checksumFile = file(".gradle/bluetape4k-dependencies/$bluetape4kDependenciesCatalogCacheKey/libs.versions.toml.sha256")
    if (!catalogChecksumMatches(catalogFile, checksumFile)) {
        require(catalogFile.parentFile.mkdirs() || catalogFile.parentFile.isDirectory) {
            "Cannot create bluetape4k-dependencies catalog cache: ${catalogFile.parentFile}"
        }
        val catalogBaseUrl =
            "https://raw.githubusercontent.com/bluetape4k/bluetape4k-dependencies/$bluetape4kDependenciesCatalogRef/gradle"
        val catalogTempFile = File.createTempFile("libs.versions-", ".toml.tmp", catalogFile.parentFile)
        val checksumTempFile = File.createTempFile("libs.versions-", ".sha256.tmp", catalogFile.parentFile)
        try {
            downloadCatalogFile("$catalogBaseUrl/libs.versions.toml", catalogTempFile)
            downloadCatalogFile("$catalogBaseUrl/libs.versions.toml.sha256", checksumTempFile)
            val expectedChecksum = requireNotNull(expectedCatalogSha256(checksumTempFile)) {
                "Invalid bluetape4k-dependencies catalog checksum: $checksumTempFile"
            }
            require(catalogSha256(catalogTempFile) == expectedChecksum) {
                "bluetape4k-dependencies catalog checksum mismatch for ref $bluetape4kDependenciesCatalogRef"
            }
            java.nio.file.Files.move(
                checksumTempFile.toPath(),
                checksumFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            java.nio.file.Files.move(
                catalogTempFile.toPath(),
                catalogFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            catalogTempFile.delete()
            checksumTempFile.delete()
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
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            name = "central-snapshots"
            configureCentralSnapshotCredentials()
        }
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
    "bluetape4k-images-barcode-api",
    "bluetape4k-images-barcode-zxing",
    "bluetape4k-images-captcha",
    "bluetape4k-images-ocr",
    "bluetape4k-images-ktor",
    "bluetape4k-images-spring-boot",
    "bluetape4k-images-vips-api",
    "bluetape4k-images-vips-java21",
    "bluetape4k-images-vips-java25",
    "bluetape4k-images-benchmark",
)
project(":bluetape4k-images").projectDir = file("images")
project(":bluetape4k-images-barcode-api").projectDir = file("images-barcode-api")
project(":bluetape4k-images-barcode-zxing").projectDir = file("images-barcode-zxing")
project(":bluetape4k-images-captcha").projectDir = file("images-captcha")
project(":bluetape4k-images-ocr").projectDir = file("images-ocr")
project(":bluetape4k-images-ktor").projectDir = file("images-ktor")
project(":bluetape4k-images-spring-boot").projectDir = file("images-spring-boot")
project(":bluetape4k-images-vips-api").projectDir = file("images-vips-api")
project(":bluetape4k-images-vips-java21").projectDir = file("images-vips-java21")
project(":bluetape4k-images-vips-java25").projectDir = file("images-vips-java25")
project(":bluetape4k-images-benchmark").projectDir = file("benchmark/images-benchmark")

include("bluetape4k-image-bom")
project(":bluetape4k-image-bom").projectDir = file("bom")

include("basic-processing")
project(":basic-processing").projectDir = file("examples/basic-processing")

include("spring-boot-image-api")
project(":spring-boot-image-api").projectDir = file("examples/spring-boot-image-api")

include("spring-boot-barcode-api")
project(":spring-boot-barcode-api").projectDir = file("examples/spring-boot-barcode-api")

include("spring-boot-ocr-api")
project(":spring-boot-ocr-api").projectDir = file("examples/spring-boot-ocr-api")

include("ktor-image-api")
project(":ktor-image-api").projectDir = file("examples/ktor-image-api")

include("ktor-ocr-api")
project(":ktor-ocr-api").projectDir = file("examples/ktor-ocr-api")
