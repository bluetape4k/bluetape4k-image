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
    .orElse("8d81f59e0a2667938edfabaa68e2e7b62778d82e")
    .get()
require(bluetape4kDependenciesCatalogRef.matches(Regex("[0-9a-f]{40}|[0-9a-f]{64}"))) {
    "bluetape4k-dependencies catalog ref must be an immutable Git commit SHA: " +
        bluetape4kDependenciesCatalogRef
}
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
    catalogFile.isFile &&
        !java.nio.file.Files.isSymbolicLink(catalogFile.toPath()) &&
        expectedCatalogSha256(checksumFile)?.let { it == catalogSha256(catalogFile) } == true

val catalogConnectTimeoutMillis = 10_000
val catalogReadTimeoutMillis = 30_000
val catalogMaxBytes = 2L * 1024 * 1024
val catalogChecksumMaxBytes = 1024L

fun downloadCatalogFile(url: String, target: File, maxBytes: Long) {
    val connection = uri(url).toURL().openConnection()
    connection.connectTimeout = catalogConnectTimeoutMillis
    connection.readTimeout = catalogReadTimeoutMillis
    require(connection.contentLengthLong < 0 || connection.contentLengthLong <= maxBytes) {
        "Catalog download exceeds allowed size: $url"
    }
    connection.getInputStream().buffered().use { input ->
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                copied += count
                require(copied <= maxBytes) { "Catalog download exceeds allowed size: $url" }
                output.write(buffer, 0, count)
            }
        }
    }
}

fun validateCatalogStructure(catalogFile: File) {
    val catalogText = catalogFile.readText()
    val requiredMarkers = listOf(
        "# <shared-version-source-of-truth by scripts/sync-shared-versions.py>",
        "# </shared-version-source-of-truth>",
        "[versions]",
        "[libraries]",
    )
    require(requiredMarkers.all(catalogText::contains)) {
        "Invalid bluetape4k-dependencies catalog structure: $catalogFile"
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
        ?.let { catalogFile ->
            require(catalogFile.isFile && !java.nio.file.Files.isSymbolicLink(catalogFile.toPath())) {
                "Explicit bluetape4k-dependencies catalog must be a regular non-symlink file: $catalogFile"
            }
            validateCatalogStructure(catalogFile)
            return catalogFile
        }

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
            downloadCatalogFile("$catalogBaseUrl/libs.versions.toml", catalogTempFile, catalogMaxBytes)
            downloadCatalogFile(
                "$catalogBaseUrl/libs.versions.toml.sha256",
                checksumTempFile,
                catalogChecksumMaxBytes,
            )
            val expectedChecksum = requireNotNull(expectedCatalogSha256(checksumTempFile)) {
                "Invalid bluetape4k-dependencies catalog checksum: $checksumTempFile"
            }
            require(catalogSha256(catalogTempFile) == expectedChecksum) {
                "bluetape4k-dependencies catalog checksum mismatch for ref $bluetape4kDependenciesCatalogRef"
            }
            validateCatalogStructure(catalogTempFile)
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
    validateCatalogStructure(catalogFile)
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

include("spring-boot-image-intelligence-api")
project(":spring-boot-image-intelligence-api").projectDir =
    file("examples/spring-boot-image-intelligence-api")

include("spring-boot-ocr-api")
project(":spring-boot-ocr-api").projectDir = file("examples/spring-boot-ocr-api")

include("ktor-image-api")
project(":ktor-image-api").projectDir = file("examples/ktor-image-api")

include("ktor-ocr-api")
project(":ktor-ocr-api").projectDir = file("examples/ktor-ocr-api")
