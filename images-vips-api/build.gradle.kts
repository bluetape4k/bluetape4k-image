import groovy.util.Node
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure

plugins {
    `java-test-fixtures`
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
