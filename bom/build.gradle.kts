import java.io.DataInputStream
import java.util.jar.JarFile
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec

plugins {
    `java-platform`
    `maven-publish`
    signing
}

dependencies {
    constraints {
        rootProject.subprojects {
            if (isPublishedJvmModule()) {
                api(rootProject.dependencies.project(path))
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("BluetapeImage") {
            from(components["javaPlatform"])
            pom {
                name.set("bluetape4k-image-bom")
                description.set("BOM for bluetape4k-image — image processing modules (scrimage, libvips)")
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
        maven {
            name = "vipsConsumer"
            url = uri(rootProject.layout.buildDirectory.dir("tmp/vips-bom-consumer/repository").get().asFile)
        }
    }
}

configurePublishingSigning("BluetapeImage")

val vipsConsumerRepository = rootProject.layout.buildDirectory.dir("tmp/vips-bom-consumer/repository")
val vipsConsumerProject = rootProject.layout.buildDirectory.dir("tmp/vips-bom-consumer/project")
val cleanVipsBomConsumer = tasks.register<Delete>("cleanVipsBomConsumer") {
    description = "Remove the isolated Vips BOM consumer repository and project"
    delete(vipsConsumerRepository, vipsConsumerProject)
}

val vipsBomPublicationTasks = listOf(
    ":bluetape4k-image-bom:publishBluetapeImagePublicationToVipsConsumerRepository",
    ":bluetape4k-images-vips-api:publishBluetapeImagePublicationToVipsConsumerRepository",
    ":bluetape4k-images-vips-java21:publishBluetapeImagePublicationToVipsConsumerRepository",
)
vipsBomPublicationTasks.forEach { publicationTaskPath ->
    val targetProjectPath = publicationTaskPath.substringBeforeLast(":")
    val targetTaskName = publicationTaskPath.substringAfterLast(":")
    rootProject.project(targetProjectPath).tasks.named(targetTaskName).configure {
        mustRunAfter(cleanVipsBomConsumer)
    }
}

tasks.register<Exec>("verifyVipsBomConsumer") {
    description = "Publish Vips API/JNI and BOM to a file repository and run an independent Java25 consumer"
    group = "verification"
    dependsOn(cleanVipsBomConsumer)
    dependsOn(vipsBomPublicationTasks)
    outputs.upToDateWhen { false }

    doFirst {
        val repository = vipsConsumerRepository.get().asFile
        val project = vipsConsumerProject.get().asFile
        repository.mkdirs()
        project.resolve("src/main/java/consumer").mkdirs()

        project.resolve("settings.gradle.kts").writeText(
            """
            import org.gradle.api.initialization.resolve.RepositoriesMode

            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    maven {
                        url = uri("${repository.toURI()}")
                        metadataSources {
                            gradleMetadata()
                            artifact()
                        }
                    }
                    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
                    mavenCentral()
                }
            }
            rootProject.name = "vips-bom-consumer"
            """.trimIndent() + "\n",
        )
        project.resolve("build.gradle.kts").writeText(
            """
            plugins {
                application
            }

            java {
                toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            }

            dependencies {
                implementation(platform("${rootProject.group}:bluetape4k-image-bom:${rootProject.version}"))
                implementation("${rootProject.group}:bluetape4k-images-vips-java21")
            }

            application {
                mainClass.set("consumer.VipsBomConsumer")
            }
            """.trimIndent() + "\n",
        )
        project.resolve("src/main/java/consumer/VipsBomConsumer.java").writeText(
            """
            package consumer;

            import io.bluetape4k.images.vips.java21.JVipsRuntime;

            import io.bluetape4k.images.vips.VipsImage;
            import io.bluetape4k.images.vips.java21.JVipsImageSupportKt;
            import java.util.Base64;

            public final class VipsBomConsumer {
                private VipsBomConsumer() {
                }

                public static void main(String[] args) throws Exception {
                    byte[] png = Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
                    );
                    JVipsRuntime.INSTANCE.init(1, 100_000_000L);
                    try (VipsImage image = JVipsImageSupportKt.vipsImageOf(png)) {
                        if (image.getWidth() != 1 || image.getHeight() != 1) {
                            throw new IllegalStateException("unexpected consumer image dimensions");
                        }
                    }
                }
            }
            """.trimIndent() + "\n",
        )
        commandLine(
            rootProject.file("gradlew").absolutePath,
            "--project-dir",
            project.absolutePath,
            "--no-daemon",
            "--no-configuration-cache",
            "--console=plain",
            "run",
        )
    }

    doLast {
        val repository = vipsConsumerRepository.get().asFile
        val publishedMainJars = setOf(
            "bluetape4k-images-vips-api-${rootProject.version}.jar",
            "bluetape4k-images-vips-java21-${rootProject.version}.jar",
        )
        val publishedJars = repository.walkTopDown()
            .filter { file -> file.isFile && file.name in publishedMainJars }
            .toList()
        require(publishedJars.map { it.name }.toSet() == publishedMainJars) {
            "expected published API/JNI main jars $publishedMainJars but found ${publishedJars.map { it.name }}"
        }
        val bomDirectory = repository.resolve(
            "${rootProject.group.toString().replace('.', '/')}/bluetape4k-image-bom/${rootProject.version}"
        )
        val bomMetadata = listOf(
            bomDirectory.resolve("bluetape4k-image-bom-${rootProject.version}.pom"),
            bomDirectory.resolve("bluetape4k-image-bom-${rootProject.version}.module"),
        )
        require(bomMetadata.all { it.isFile }) {
            "expected published BOM metadata ${bomMetadata.map { it.name }} in $bomDirectory"
        }
        publishedJars.forEach { jarFile ->
            JarFile(jarFile).use { jar ->
                jar.entries().asSequence()
                    .filter { entry -> !entry.isDirectory && entry.name.endsWith(".class") }
                    .forEach { entry ->
                        val major = jar.getInputStream(entry).use { input ->
                            DataInputStream(input).use { data ->
                                require(data.readInt() == 0xCAFEBABE.toInt()) {
                                    "invalid class header in ${jarFile.name}!/${entry.name}"
                                }
                                data.readUnsignedShort()
                                data.readUnsignedShort()
                            }
                        }
                        require(major <= 69) {
                            "${jarFile.name}!/${entry.name} uses class major $major (maximum 69)"
                        }
                    }
            }
        }
        println("Validated isolated Vips BOM consumer, API/JNI main jars, and BOM metadata")
    }
}
