plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.dependency.management)
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-jvm-default=enable")
    }
}

dependencyManagement {
    imports {
        // Spring Boot BOM first — controls Spring versions
        mavenBom(libs.spring.boot.dependencies.get().toString())
        // AWS SDK v2 BOM
        mavenBom(libs.aws2.bom.get().toString())
        // Kotlin BOM — override with our pinned Kotlin version
        mavenBom(libs.kotlin.bom.get().toString())
        // Kotlinx Coroutines BOM
        mavenBom(libs.kotlinx.coroutines.bom.get().toString())
        // bluetape4k BOM
        mavenBom(libs.bluetape4k.bom.get().toString())
    }
}

dependencies {
    // images module — non-transitive (consumers use :images directly)
    implementation(project(":images"))

    // Spring Boot AutoConfig (compileOnly — consumers bring their own Spring Boot)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.boot.actuator)

    // AWS SDK v2 (compileOnly — optional S3/CloudFront support)
    compileOnly(libs.aws2.s3)
    compileOnly(libs.aws2.cloudfront)

    // bluetape4k-aws spring-boot integration (compileOnly — optional)
    compileOnly(libs.bluetape4k.aws.spring.boot)

    // Micrometer metrics (compileOnly — optional)
    compileOnly(libs.micrometer.core)

    // Kotlinx Coroutines Reactor (compileOnly — optional, needed for ReactiveHealthIndicator)
    compileOnly(libs.kotlinx.coroutines.reactor)

    // Annotation processor for @ConfigurationProperties metadata
    annotationProcessor(libs.spring.boot.configuration.processor)

    // Logging
    implementation(libs.bluetape4k.logging)
    implementation(libs.kotlinx.coroutines.core)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.aws.spring.boot)
    testImplementation(libs.aws2.s3)
    testImplementation(libs.aws2.cloudfront)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.spring.boot.actuator)
    testImplementation(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
