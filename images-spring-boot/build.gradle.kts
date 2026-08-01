plugins {
    alias(bt4k.plugins.kotlin.jvm)
    alias(bt4k.plugins.kotlin.spring)
    alias(bt4k.plugins.dependency.management)
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
        mavenBom("org.springframework.boot:spring-boot-dependencies:${bt4k.versions.spring.boot.get()}")
        // AWS SDK v2 BOM
        mavenBom(bt4k.aws2.bom.get().toString())
        // Kotlin BOM — override with our pinned Kotlin version
        mavenBom("org.jetbrains.kotlin:kotlin-bom:${bt4k.versions.kotlin.get()}")
        // Kotlinx Coroutines BOM
        mavenBom("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${bt4k.versions.kotlinx.coroutines.get()}")
        // bluetape4k BOM
        mavenBom(bt4k.bluetape4k.bom.get().toString())
    }
}

dependencies {
    // images module — non-transitive (consumers use :images directly)
    implementation(project(":bluetape4k-images"))

    // Spring Boot AutoConfig (compileOnly — consumers bring their own Spring Boot)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.boot.actuator)
    // Boot 4 split: ReactiveHealthIndicator / Health / Status moved to spring-boot-health
    compileOnly(libs.spring.boot.health)
    // @PostConstruct (JSR-250) — not transitively pulled by spring-boot-autoconfigure 4
    compileOnly(libs.jakarta.annotation.api)

    // AWS SDK v2 (compileOnly — optional S3/CloudFront support)
    compileOnly(libs.aws2.s3)
    compileOnly(libs.aws2.cloudfront)

    // bluetape4k-aws spring-boot integration (compileOnly — optional)
    compileOnly(bt4k.bluetape4k.aws.spring.boot)

    // Micrometer metrics (compileOnly — optional)
    compileOnly(libs.micrometer.core)

    // Kotlinx Coroutines Reactor (compileOnly — optional, needed for ReactiveHealthIndicator)
    compileOnly(libs.kotlinx.coroutines.reactor)

    // Annotation processor for @ConfigurationProperties metadata
    annotationProcessor(libs.spring.boot.configuration.processor)

    // Logging
    implementation(bt4k.bluetape4k.logging)
    implementation(libs.kotlinx.coroutines.core)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.springframework.boot:spring-boot-micrometer-metrics")
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.aws.spring.boot)
    testImplementation(libs.aws2.s3)
    testImplementation(libs.aws2.cloudfront)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.spring.boot.actuator)
    testImplementation(libs.spring.boot.health)
    testImplementation(libs.kotlinx.coroutines.reactor)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
