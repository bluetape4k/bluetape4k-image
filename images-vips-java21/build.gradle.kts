import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.jar.JarFile

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

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

tasks.withType<Test>().configureEach {
    // The production module and its tests use the repository JDK 25 baseline.
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    // JNI native library: isolate each test class in its own JVM fork
    forkEvery = 1
    maxParallelForks = 1
    // 명시적으로 -Dvips.enabled=false/true 를 전달한 경우만 전파한다.
    // 미설정 시 AbstractJVipsTest 가 JVipsRuntime.init() 결과로 자동 감지한다.
    System.getProperty("vips.enabled")?.let { systemProperty("vips.enabled", it) }
}

tasks.named<Test>("test") {
    doFirst {
        if (System.getProperty("bluetape4k.images.golden.update", "false").toBoolean()) {
            throw org.gradle.api.GradleException(
                "Java21 JNI golden tests are read-only; regenerate canonical fixtures only with the Java25 FFM tests.",
            )
        }
    }
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

val mainSourceSet = sourceSets.named("main").get()
val consumerTestSourceSet = sourceSets.create("consumerTest")

// Keep this source set close to a published consumer: it sees only production
// output and production runtime dependencies, never src/test or testFixtures.
consumerTestSourceSet.compileClasspath += mainSourceSet.runtimeClasspath
consumerTestSourceSet.runtimeClasspath += mainSourceSet.runtimeClasspath

dependencies {
    add(consumerTestSourceSet.implementationConfigurationName, bt4k.bluetape4k.junit5)
}

tasks.named<KotlinJvmCompile>("compileConsumerTestKotlin") {
    kotlinJavaToolchain.toolchain.use(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

tasks.named<JavaCompile>("compileConsumerTestJava") {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    options.release.set(25)
}

val consumerEnabled = providers.gradleProperty("vips.consumer.enabled")

tasks.register<Test>("consumerTest") {
    description = "Runs the Java 25 production-only JVips consumer smoke."
    group = "verification"
    dependsOn(consumerTestSourceSet.classesTaskName)
    testClassesDirs = consumerTestSourceSet.output.classesDirs
    classpath = consumerTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    doFirst {
        val forbiddenOutputs = classpath.files.filter { file ->
            val path = file.invariantSeparatorsPath
            path.contains("/testFixtures") ||
                path.contains("/classes/kotlin/test") ||
                path.contains("/classes/java/test") ||
                path.contains("/test-classes")
        }
        require(forbiddenOutputs.isEmpty()) {
            "consumerTest must not include test or testFixtures output: $forbiddenOutputs"
        }
    }
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    forkEvery = 1
    maxParallelForks = 1
    onlyIf {
        val enabled = consumerEnabled.orNull == "true"
        if (!enabled) {
            logger.lifecycle("Skipping consumerTest: set -Pvips.consumer.enabled=true to enable the native smoke.")
        }
        enabled
    }
    consumerEnabled.orNull?.let { systemProperty("vips.consumer.enabled", it) }
    System.getProperty("vips.enabled")?.let { systemProperty("vips.enabled", it) }
}

val maxProductionClassFileMajor = 69

private fun classFileMajor(bytes: ByteArray): Int {
    require(bytes.size >= 8 &&
        bytes[0] == 0xCA.toByte() &&
        bytes[1] == 0xFE.toByte() &&
        bytes[2] == 0xBA.toByte() &&
        bytes[3] == 0xBE.toByte()
    ) { "invalid class file header" }
    return ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
}

val vipsApiProject = project(":bluetape4k-images-vips-api")
val vipsApiJar = vipsApiProject.tasks.named<Jar>("jar")
val vipsJava21Jar = tasks.named<Jar>("jar")

tasks.register("verifyVipsJava21Bytecode") {
    description = "Verifies Java 25 production bytecode for the API and legacy java21 module."
    group = "verification"
    dependsOn(vipsApiJar, vipsJava21Jar)
    doLast {
        val productionDirectories = listOf(
            "bluetape4k-images-vips-api/classes-kotlin" to
                vipsApiProject.layout.buildDirectory.dir("classes/kotlin/main").get().asFile,
            "bluetape4k-images-vips-api/classes-java" to
                vipsApiProject.layout.buildDirectory.dir("classes/java/main").get().asFile,
            "bluetape4k-images-vips-java21/classes-kotlin" to
                layout.buildDirectory.dir("classes/kotlin/main").get().asFile,
            "bluetape4k-images-vips-java21/classes-java" to
                layout.buildDirectory.dir("classes/java/main").get().asFile,
        )
        val productionJars = listOf(
            "bluetape4k-images-vips-api/jar" to vipsApiJar.get().archiveFile.get().asFile,
            "bluetape4k-images-vips-java21/jar" to vipsJava21Jar.get().archiveFile.get().asFile,
        )
        val violations = mutableListOf<String>()
        var inspected = 0

        fun inspect(location: String, bytes: ByteArray) {
            val major = classFileMajor(bytes)
            inspected++
            if (major > maxProductionClassFileMajor) {
                violations += "$location has class file major $major (maximum $maxProductionClassFileMajor)"
            }
        }

        productionDirectories
            .filter { (_, directory) -> directory.isDirectory }
            .forEach { (label, directory) ->
                directory.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        inspect("$label/${classFile.relativeTo(directory)}", classFile.readBytes())
                    }
            }

        productionJars.forEach { (label, jarFile) ->
            require(jarFile.isFile) { "production jar is missing: $jarFile" }
            JarFile(jarFile).use { jar ->
                jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { entry ->
                        jar.getInputStream(entry).use { input -> inspect("$label!/${entry.name}", input.readBytes()) }
                    }
            }
        }

        require(inspected > 0) { "no production class files were found for bytecode verification" }
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(separator = "\n"))
        }
        logger.lifecycle("Verified $inspected production class files at major <= $maxProductionClassFileMajor")
    }
}

dependencies {
    api(project(":bluetape4k-images-vips-api"))
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(testFixtures(project(":bluetape4k-images-vips-api")))
    // Use the JDK 25 structured-concurrency provider for the test runtime.
    testRuntimeOnly(bt4k.bluetape4k.virtualthread.jdk25)

    // JVips JNI bindings (Java 8+; Linux: bundled native / macOS: system libvips required)
    // D8: binding types are internal — use api() only if consumers need VImage directly
    implementation(bt4k.jvips.build69bf715)

    // BoundedInputStream for input size limits
    implementation(bt4k.commons.io)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
