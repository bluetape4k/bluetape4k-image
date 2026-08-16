import groovy.util.Node
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.gradle.kotlin.dsl.configure

plugins {
    `java-test-fixtures`
}

// The API is consumed by the JVips JNI implementation (whose legacy artifact
// name contains java21) and the Java 25 FFM implementation. Keep the shared
// contract on the repository-wide Java 25 bytecode level.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

// Test fixtures compare against the regular images module, which targets JDK
// 25. Keep the published API/native contract on the repository JDK 25 baseline
// while compiling this internal verification-only source set at that level.
tasks.named<JavaCompile>("compileTestFixturesJava") {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    options.release.set(25)
}

tasks.named<KotlinJvmCompile>("compileTestFixturesKotlin") {
    kotlinJavaToolchain.toolchain.use(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

// The test fixtures consume the regular images module, whose bytecode target
// The shared API and its verification fixtures use the same Java 25 bytecode
// level, so Gradle selects one consistent variant for all consumers.
tasks.named<Test>("test") {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}

tasks.named<JavaCompile>("compileTestJava") {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    options.release.set(25)
}

tasks.named<KotlinJvmCompile>("compileTestKotlin") {
    kotlinJavaToolchain.toolchain.use(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

sourceSets {
    val main = getByName("main")
    create("unoptedVipsOptInFixture") {
        compileClasspath += main.output
        compileClasspath += main.compileClasspath
        runtimeClasspath += main.output
    }
    create("optedVipsOptInFixture") {
        compileClasspath += main.output
        compileClasspath += main.compileClasspath
        runtimeClasspath += main.output
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileUnoptedVipsOptInFixtureKotlin") {
    compilerOptions.allWarningsAsErrors.set(true)
    onlyIf { providers.gradleProperty("verifyVipsOptInFixtures").isPresent }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileOptedVipsOptInFixtureKotlin") {
    compilerOptions.allWarningsAsErrors.set(true)
    onlyIf { providers.gradleProperty("verifyVipsOptInFixtures").isPresent }
}

dependencies {
    api(bt4k.bluetape4k.core)
    api(bt4k.bluetape4k.io)
    api(bt4k.bluetape4k.okio)
    api(bt4k.bluetape4k.coroutines)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Test Fixtures — VipsGoldenAssert needs scrimage pixel comparison + JUnit5
    testFixturesApi(project(":bluetape4k-images"))
    testFixturesImplementation(bt4k.bluetape4k.junit5)
    testFixturesImplementation(libs.junit.jupiter.api)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}

extensions.configure<PublishingExtension> {
    publications.named<MavenPublication>("BluetapeImage") {
        pom.withXml {
            val dependencies = asNode()
                .children()
                .filterIsInstance<Node>()
                .firstOrNull { it.name().toString().substringAfter('}') == "dependencies" }

            dependencies
                ?.children()
                ?.filterIsInstance<Node>()
                ?.filter { dependency ->
                    dependency.name().toString().substringAfter('}') == "dependency" &&
                        dependency.children()
                            .filterIsInstance<Node>()
                            .associate { it.name().toString().substringAfter('}') to it.text() }
                            .let { details ->
                                details["optional"] == "true" &&
                                    "${details["groupId"]}:${details["artifactId"]}" in setOf(
                                        "io.github.bluetape4k.image:bluetape4k-images",
                                        "com.sksamuel.scrimage:scrimage-core",
                                        "com.twelvemonkeys.imageio:imageio-core",
                                )
                            }
                }
                ?.forEach(dependencies::remove)
        }
    }
}
