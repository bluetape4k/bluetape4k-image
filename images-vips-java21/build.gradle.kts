import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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
