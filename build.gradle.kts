import dev.detekt.gradle.Detekt
import dev.detekt.gradle.report.ReportMergeTask
import nmcp.NmcpAggregationExtension
import nmcp.NmcpExtension
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.authentication.http.BasicAuthentication
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.util.concurrent.TimeUnit

plugins {
    base
    `maven-publish`
    signing
    alias(bt4k.plugins.kotlin.jvm)

    alias(bt4k.plugins.kotlin.allopen) apply false
    alias(bt4k.plugins.kotlin.spring) apply false
    alias(bt4k.plugins.kotlin.serialization) apply false
    alias(bt4k.plugins.spring.boot) apply false
    alias(bt4k.plugins.kotlinx.atomicfu)
    alias(bt4k.plugins.kotlinx.benchmark) apply false

    alias(bt4k.plugins.detekt.dev)
    alias(bt4k.plugins.dependency.management)

    alias(bt4k.plugins.dokka)
    alias(bt4k.plugins.test.logger)

    alias(bt4k.plugins.nmcp.aggregation)
    alias(bt4k.plugins.nmcp) apply false

    alias(bt4k.plugins.kover)
}

val rootLibs = libs
val rootBt4k = bt4k
val bt4kCatalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("bt4k")
fun bt4kLibrary(alias: String) = bt4kCatalog.findLibrary(alias).get()
fun bt4kVersion(alias: String): String {
    val version = bt4kCatalog.findVersion(alias).get()
    return version.requiredVersion
        .ifBlank { version.preferredVersion }
        .ifBlank { version.strictVersion }
}

@OptIn(ExperimentalAbiValidation::class)
fun Project.configureProductionAbiValidation() {
    if (!isPublishedJvmModule()) return

    fun File.normalizeAbiDump() {
        if (!isFile) return
        writeText(readText().trimEnd('\r', '\n') + "\n")
    }

    extensions.configure<KotlinProjectExtension> {
        abiValidation {
            referenceDumpDir.set(rootProject.layout.projectDirectory.dir("api"))
            binariesSource.set(BinariesSource.MAVEN_PUBLICATIONS)
            filters {
                exclude {
                    byNames.add("io.bluetape4k.images.IncubatingImageApi")
                    byNames.add("io.bluetape4k.images.vips.VipsIncubatingApi")
                    annotatedWith.add("io.bluetape4k.images.IncubatingImageApi")
                    annotatedWith.add("io.bluetape4k.images.vips.VipsIncubatingApi")
                }
            }
        }
    }

    val abiDumpFile = layout.buildDirectory.file("kotlin/abi/${name}.api")
    tasks.named("internalDumpKotlinAbi") {
        doLast {
            abiDumpFile.get().asFile.normalizeAbiDump()
        }
    }
}

val centralPublishing = resolveCentralPublishingConfig()
val centralUser: String = centralPublishing.username
val centralPassword: String = centralPublishing.password
val centralSnapshotsParallelism: Int = providers
    .gradleProperty("centralSnapshotsParallelism")
    .map(String::toInt)
    .orElse(4)
    .get()

val projectGroup: String = project.property("projectGroup") as String
val baseVersion: String = project.property("baseVersion") as String
val snapshotVersion: String = project.property("snapshotVersion") as String
val vipsConsumerRepositoryDirectory = layout.buildDirectory.dir("tmp/vips-bom-consumer/repository")
val vipsConsumerPublicationModules = setOf("bluetape4k-images-vips-api", "bluetape4k-images-vips-java21")

fun MavenArtifactRepository.configureCentralSnapshotCredentials() {
    if (centralUser.isNotBlank() && centralPassword.isNotBlank()) {
        credentials(PasswordCredentials::class) {
            username = centralUser
            password = centralPassword
        }
        authentication {
            create<BasicAuthentication>("basic")
        }
    }
}

allprojects {
    group = projectGroup
    version = baseVersion + snapshotVersion

    repositories {
        mavenCentral()
        maven {
            name = "central-snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            configureCentralSnapshotCredentials()
        }
    }
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(1, TimeUnit.DAYS)
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
    }
    if (!isNonPublishedModule()) {
        apply(plugin = "com.gradleup.nmcp")
    }

    configurations.matching { it.name.startsWith("nmcp") }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization")) {
                useVersion("1.9.0")
                because("nmcp runtime compatibility")
            }
        }
    }

    plugins.withId("com.gradleup.nmcp") {
        extensions.configure<NmcpExtension>("nmcp") {
            publishAllPublicationsToCentralPortal {
                username.set(centralUser)
                password.set(centralPassword)
                publishingType.set("AUTOMATIC")
                uploadSnapshotsParallelism.set(centralSnapshotsParallelism)
            }
        }
    }
}

subprojects {
    // BOM 모듈은 java-platform 플러그인을 사용하므로 Java/Kotlin 설정을 건너뜁니다.
    if (name == "bluetape4k-image-bom") return@subprojects

    apply {
        plugin<JavaLibraryPlugin>()
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.jetbrains.kotlinx.atomicfu")
        if (!isNonPublishedModule()) {
            plugin("org.jetbrains.kotlinx.kover")
            plugin("maven-publish")
            plugin("signing")
        }
        plugin("io.spring.dependency-management")
        plugin("org.jetbrains.dokka")
        plugin("com.adarshr.test-logger")
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configureProductionAbiValidation()
        kotlin {
            jvmToolchain(25)
            compilerOptions {
                languageVersion.set(KotlinVersion.KOTLIN_2_4)
                apiVersion.set(KotlinVersion.KOTLIN_2_4)
                jvmTarget.set(JvmTarget.JVM_25)
                freeCompilerArgs = listOf(
                    "-Xjsr305=strict",
                    "-jvm-default=enable",
                    "-Xstring-concat=indy",
                )
                val experimentalAnnotations = listOf(
                    "kotlin.RequiresOptIn",
                    "kotlin.ExperimentalStdlibApi",
                    "kotlin.contracts.ExperimentalContracts",
                    "kotlin.experimental.ExperimentalTypeInference",
                    "kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "kotlinx.coroutines.InternalCoroutinesApi",
                    "kotlinx.coroutines.FlowPreview",
                    "kotlinx.coroutines.DelicateCoroutinesApi",
                )
                freeCompilerArgs.addAll(experimentalAnnotations.map { "-opt-in=$it" })
            }
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlinx.atomicfu") {
        atomicfu {
            transformJvm = true
            jvmVariant = "VH"
        }
    }

    tasks {
        abstract class TestMutexService: BuildService<BuildServiceParameters.None>
        abstract class SigningMutexService: BuildService<BuildServiceParameters.None>

        val testMutex = gradle.sharedServices.registerIfAbsent("test-mutex", TestMutexService::class) {
            maxParallelUsages.set(1)
        }
        val signingMutex = gradle.sharedServices.registerIfAbsent("signing-mutex", SigningMutexService::class) {
            maxParallelUsages.set(1)
        }

        compileJava { options.isIncremental = true }
        compileKotlin { compilerOptions { incremental = true } }

        test {
            usesService(testMutex)
            useJUnitPlatform()
            jvmArgs(
                "-Xshare:off",
                "-Xms2M",
                "-Xmx4G",
                "-XX:+UseG1GC",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+EnableDynamicAgentLoading",
                "--enable-preview",
                "-Didea.io.use.nio2=true"
            )
            testLogging {
                showExceptions = true
                showCauses = true
                showStackTraces = true
                events("failed")
            }
        }

        withType<Sign>().configureEach {
            usesService(signingMutex)
        }

        testlogger {
            theme = com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA_PARALLEL
            showFullStackTraces = true
        }

        val reportMerge = register<ReportMergeTask>("reportMerge") {
            val file = rootProject.layout.buildDirectory.asFile.get().resolve("reports/detekt/merged.xml")
            output.set(file)
        }
        withType<Detekt>().configureEach detekt@{
            reports.checkstyle.required.set(true)
            finalizedBy(reportMerge)
            reportMerge.configure { input.from(this@detekt.reports.checkstyle.outputLocation) }
        }

        jar {
            manifest.attributes["Specification-Title"] = project.name
            manifest.attributes["Specification-Version"] = project.version
            manifest.attributes["Implementation-Title"] = project.name
            manifest.attributes["Implementation-Version"] = project.version
            manifest.attributes["Automatic-Module-Name"] = project.name.replace('-', '.')
            manifest.attributes["Created-By"] =
                "${System.getProperty("java.version")} (${System.getProperty("java.specification.vendor")})"
        }

        dokka {
            dokkaPublications.html {
                outputDirectory.set(layout.buildDirectory.asFile.get().resolve("javadoc"))
            }
            dokkaSourceSets.configureEach {
                includes.from(project.files("README.md"))
            }
        }

        clean {
            doLast {
                delete("./.project")
                delete("./out")
                delete("./bin")
            }
        }

        // atomicfu transform output → kover coverage collection: make ordering explicit
        matching { it.name == "koverGenerateArtifactJvm" }.configureEach {
            mustRunAfter(matching { it.name == "transformMainAtomicfu" })
        }
    }

    dependencyManagement {
        setApplyMavenExclusions(false)
        imports {
            mavenBom(bt4kLibrary("bluetape4k-bom").get().toString())
            mavenBom("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${bt4kVersion("kotlinx-coroutines")}")
            mavenBom("org.jetbrains.kotlin:kotlin-bom:${bt4kVersion("kotlin")}")
            mavenBom(rootBt4k.junit.bom.get().toString())
            mavenBom("org.testcontainers:testcontainers-bom:${bt4kVersion("testcontainers")}")
        }

        dependencies {

            // <central-catalog-local-aliases>

            dependency("com.sksamuel.scrimage:scrimage-filters:${bt4kVersion("scrimage")}")

            dependency("com.sksamuel.scrimage:scrimage-webp:${bt4kVersion("scrimage")}")

            dependency("io.ktor:ktor-client-content-negotiation:${bt4kVersion("ktor")}")

            dependency("io.ktor:ktor-serialization-kotlinx-json:${bt4kVersion("ktor")}")

            dependency("io.ktor:ktor-server-content-negotiation:${bt4kVersion("ktor")}")

            dependency("io.ktor:ktor-server-core:${bt4kVersion("ktor")}")

            dependency("io.ktor:ktor-server-netty:${bt4kVersion("ktor")}")

            dependency("io.ktor:ktor-server-status-pages:${bt4kVersion("ktor")}")

            dependency("io.ktor:ktor-server-test-host:${bt4kVersion("ktor")}")

            dependency("org.awaitility:awaitility-kotlin:${bt4kVersion("awaitility")}")

            dependency("org.jetbrains.kotlin:kotlin-bom:${bt4kVersion("kotlin")}")

            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${bt4kVersion("kotlinx-coroutines")}")

            dependency("org.slf4j:jcl-over-slf4j:${bt4kVersion("slf4j")}")

            dependency("org.slf4j:jul-to-slf4j:${bt4kVersion("slf4j")}")

            dependency("org.slf4j:log4j-over-slf4j:${bt4kVersion("slf4j")}")

            dependency("org.springframework.boot:spring-boot-dependencies:${bt4kVersion("spring-boot")}")

            dependency("org.testcontainers:testcontainers-bom:${bt4kVersion("testcontainers")}")

            dependency("org.testcontainers:testcontainers-junit-jupiter:${bt4kVersion("testcontainers")}")

            dependency("software.amazon.awssdk:cloudfront:${bt4kVersion("aws2")}")

            dependency("software.amazon.awssdk:s3:${bt4kVersion("aws2")}")

            // </central-catalog-local-aliases>
            dependency("commons-io:commons-io:${bt4kVersion("commons-io")}")
            dependency("com.sksamuel.scrimage:scrimage-core:${bt4kVersion("scrimage")}")
            dependency("org.slf4j:slf4j-api:${bt4kVersion("slf4j")}")
        }
    }

    dependencies {
        api(rootBt4k.jetbrains.annotations)

        implementation(rootLibs.kotlin.stdlib)
        implementation(rootLibs.kotlin.reflect)
        testImplementation(rootLibs.kotlin.test)
        testImplementation(rootLibs.kotlin.test.junit5)

        implementation(rootLibs.kotlinx.coroutines.core)
        implementation(rootBt4k.kotlinx.atomicfu)

        api(bt4kLibrary("slf4j-api"))
        testImplementation(rootBt4k.logback.asProvider())
        testImplementation(rootLibs.jcl.over.slf4j)
        testImplementation(rootLibs.jul.to.slf4j)
        testImplementation(rootLibs.log4j.over.slf4j)

        testImplementation(rootLibs.junit.jupiter)
        testRuntimeOnly(rootLibs.junit.platform.engine)

        testImplementation(rootLibs.awaitility.kotlin)
        testImplementation(rootBt4k.mockk)
    }

    if (!isNonPublishedModule()) {
        publishing {
            publications {
                create<MavenPublication>("BluetapeImage") {
                    val sourcesJar = tasks.register<Jar>("sourcesJar") {
                        archiveClassifier.set("sources")
                        from(sourceSets["main"].allSource)
                    }
                    val javadocJar = tasks.register<Jar>("javadocJar") {
                        archiveClassifier.set("javadoc")
                        from(layout.buildDirectory.asFile.get().resolve("javadoc"))
                    }
                    from(components["java"])
                    artifact(sourcesJar)
                    artifact(javadocJar)

                    pom {
                        name.set(project.name)
                        description.set("Kotlin/JVM image processing library — scrimage, VipsImage, TwelveMonkeys — part of the bluetape4k ecosystem")
                        url.set("https://github.com/bluetape4k/bluetape4k-image")
                        licenses {
                            license {
                                name.set("MIT License")
                                url.set("https://opensource.org/licenses/MIT")
                            }
                        }
                        developers {
                            developer {
                                id.set("debop")
                                name.set("Sunghyouk Bae")
                                email.set("sunghyouk.bae@gmail.com")
                            }
                        }
                        scm {
                            connection.set("scm:git:git://github.com/bluetape4k/bluetape4k-image.git")
                            developerConnection.set("scm:git:ssh://github.com/bluetape4k/bluetape4k-image.git")
                            url.set("https://github.com/bluetape4k/bluetape4k-image")
                        }
                    }
                }
            }
            repositories {
                mavenCentral()
                maven {
                    name = "central-snapshots"
                    url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                }
                if (project.name in vipsConsumerPublicationModules) {
                    maven {
                        name = "vipsConsumer"
                        url = uri(vipsConsumerRepositoryDirectory.get().asFile)
                    }
                }
            }
        }

        configurePublishingSigning("BluetapeImage")
    }
}

extensions.configure<NmcpAggregationExtension>("nmcpAggregation") {
    centralPortal {
        username.set(centralUser)
        password.set(centralPassword)
        publishingType.set("AUTOMATIC")
        uploadSnapshotsParallelism.set(centralSnapshotsParallelism)
    }
}

dependencies {
    subprojects
        .filterNot { it.isNonPublishedModule() }
        .forEach { add("nmcpAggregation", project(it.path)) }
}

dependencies {
    subprojects
        .filter { it.name != "bluetape4k-image-bom" }
        .filterNot { it.isNonPublishedModule() }
        .forEach { sub -> kover(project(sub.path)) }
}

// atomicfu transforms output before kover collects coverage — make ordering explicit
tasks.matching { it.name == "koverGenerateArtifactJvm" }.configureEach {
    mustRunAfter(tasks.matching { it.name == "transformMainAtomicfu" })
}

/**
 * Published JVM modules are the single ABI inventory used by the release gate.
 * Examples, benchmarks, and the platform BOM remain intentionally outside this
 * Kotlin binary baseline; the shared buildSrc classifier is also used by BOM
 * constraints so the two sets cannot drift silently.
 */
val productionAbiProjects = subprojects
    .filter { it.isPublishedJvmModule() }
    .sortedBy(Project::getPath)

check(productionAbiProjects.isNotEmpty()) {
    "Production ABI publication inventory must not be empty"
}

val productionAbiCheckTasks = productionAbiProjects.map { project ->
    project.tasks.named("checkKotlinAbi")
}
val productionAbiUpdateTasks = productionAbiProjects.map { project ->
    project.tasks.named("updateKotlinAbi")
}
val productionAbiReport = layout.buildDirectory.file("abi/reports/production-abi.txt")

tasks.register("checkProductionAbi") {
    group = "verification"
    description = "Checks the fail-closed ABI baseline for every published JVM module."
    dependsOn(productionAbiCheckTasks)
    doLast {
        val expectedProjects = productionAbiProjects.map(Project::getName).toSet()
        val baselineFiles = rootProject.layout.projectDirectory.dir("api").asFile
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "api" }
        val baselineProjects = baselineFiles
            .map { it.name.removeSuffix(".api") }
            .toSet()
        val emptyBaselineProjects = baselineFiles
            .filter { it.length() == 0L }
            .map { it.name.removeSuffix(".api") }
            .toSet()
        val actualProjects = productionAbiProjects
            .map { project ->
                project.layout.buildDirectory.file("kotlin/abi/${project.name}.api").get().asFile
            }
            .filter { it.isFile && it.length() > 0L }
            .map { it.name.removeSuffix(".api") }
            .toSet()

        validateProductionAbiInventory(
            expectedProjects = expectedProjects,
            baselineProjects = baselineProjects,
            actualProjects = actualProjects,
            emptyBaselineProjects = emptyBaselineProjects,
        ).requireValid()

        productionAbiReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("modules=${expectedProjects.size}/${expectedProjects.size}")
                    appendLine("baselines=${baselineProjects.size}/${expectedProjects.size}")
                    appendLine("actualDumps=${actualProjects.size}/${expectedProjects.size}")
                    appendLine("orphanBaselines=${baselineProjects - expectedProjects}")
                    appendLine("orphanActuals=${actualProjects - expectedProjects}")
                    appendLine("emptyBaselines=$emptyBaselineProjects")
                    expectedProjects.sorted().forEach { appendLine(it) }
                },
            )
        }
        logger.lifecycle("Production ABI validated: ${expectedProjects.size} published JVM modules")
    }
}

tasks.register("updateProductionAbiBaseline") {
    group = "verification"
    description = "Manually updates the committed production ABI baseline after review."
    dependsOn(productionAbiUpdateTasks)
    doLast {
        val baselineFiles = rootProject.layout.projectDirectory.dir("api").asFile
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "api" && it.length() > 0L }
        check(baselineFiles.size == productionAbiProjects.size) {
            "Production ABI baseline must contain ${productionAbiProjects.size} non-empty files, " +
                "found ${baselineFiles.size}"
        }
    }
}

tasks.named("check") {
    dependsOn("checkProductionAbi")
}

tasks.matching {
    it.name == "publishAggregationToCentralPortal" || it.name == "publishAggregationToCentralSnapshots"
}.configureEach {
    dependsOn("checkProductionAbi")
}
