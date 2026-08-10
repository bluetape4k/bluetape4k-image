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

// The API is consumed by both the Java 21 JNI implementation and the Java 25
// FFM implementation, so keep this shared contract on the lowest supported
// bytecode level while the rest of the repository defaults to Java 25.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

// Test fixtures compare against the regular images module, which targets JDK
// 25. Keep the published API/JNI contract on 21 while compiling this internal
// verification-only source set on the dependency's bytecode level.
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
// is Java 25. Keep the shared API itself on Java 21, but run this verification
// task on JDK 25 so Gradle selects the matching test-fixtures variant.
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
    val main by getting
    val unoptedVipsOptInFixture by creating {
        compileClasspath += main.output
        compileClasspath += main.compileClasspath
        runtimeClasspath += main.output
    }
    val optedVipsOptInFixture by creating {
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
