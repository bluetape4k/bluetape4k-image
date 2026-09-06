import java.io.DataInputStream
import java.util.jar.JarFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import groovy.json.JsonSlurper
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
val vipsConsumerReports = rootProject.layout.buildDirectory.dir("reports/vips-bom-consumer")
val cleanVipsBomConsumer = tasks.register<Delete>("cleanVipsBomConsumer") {
    description = "Remove the isolated Vips BOM consumer repository and project"
    delete(vipsConsumerRepository, vipsConsumerProject)
}

val vipsBomPublicationTasks = listOf(
    ":bluetape4k-image-bom:publishBluetapeImagePublicationToVipsConsumerRepository",
    ":bluetape4k-images-vips-api:publishBluetapeImagePublicationToVipsConsumerRepository",
    ":bluetape4k-images-vips-java21:publishBluetapeImagePublicationToVipsConsumerRepository",
    ":bluetape4k-images-vips-java25:publishBluetapeImagePublicationToVipsConsumerRepository",
)
vipsBomPublicationTasks.forEach { publicationTaskPath ->
    val targetProjectPath = publicationTaskPath.substringBeforeLast(":")
    val targetTaskName = publicationTaskPath.substringAfterLast(":")
    rootProject.project(targetProjectPath).tasks.named(targetTaskName).configure {
        mustRunAfter(cleanVipsBomConsumer)
    }
}

val prepareVipsBomConsumer = tasks.register("prepareVipsBomConsumer") {
    description = "Publish the Vips BOM artifacts and prepare an independent Java 25 consumer project"
    group = "verification"
    dependsOn(cleanVipsBomConsumer)
    dependsOn(vipsBomPublicationTasks)

    doLast {
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
                            mavenPom()
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
                java
            }

            java {
                toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            }

            dependencies {
                implementation(platform("${rootProject.group}:bluetape4k-image-bom:${rootProject.version}"))
                implementation("${rootProject.group}:bluetape4k-images-vips-java21")
                implementation("${rootProject.group}:bluetape4k-images-vips-java25")
            }

            tasks.register("verifyResolvedCoordinates") {
                doLast {
                    val expected = setOf(
                        "bluetape4k-images-vips-api",
                        "bluetape4k-images-vips-java21",
                        "bluetape4k-images-vips-java25",
                    )
                    val resolved = configurations.runtimeClasspath.get()
                        .resolvedConfiguration
                        .resolvedArtifacts
                        .associate { it.moduleVersion.id.name to it.moduleVersion.id.version }
                    require(expected.all { resolved[it] == "${rootProject.version}" }) {
                        "expected versionless Vips coordinates at ${rootProject.version} but resolved ${'$'}resolved"
                    }
                }
            }

            tasks.register<JavaExec>("runJava21Consumer") {
                dependsOn("verifyResolvedCoordinates")
                classpath = sourceSets.main.get().runtimeClasspath
                mainClass.set("consumer.Java21VipsBomConsumer")
            }

            tasks.register<JavaExec>("runJava25Consumer") {
                dependsOn("verifyResolvedCoordinates")
                classpath = sourceSets.main.get().runtimeClasspath
                mainClass.set("consumer.Java25VipsBomConsumer")
                jvmArgs("--enable-native-access=ALL-UNNAMED")
                val homebrewLib = file("/opt/homebrew/lib")
                if (homebrewLib.exists()) {
                    environment("DYLD_LIBRARY_PATH", homebrewLib.absolutePath)
                }
            }
            """.trimIndent() + "\n",
        )
        project.resolve("src/main/java/consumer/Java21VipsBomConsumer.java").writeText(
            """
            package consumer;

            import io.bluetape4k.images.vips.java21.JVipsRuntime;

            import io.bluetape4k.images.vips.VipsImage;
            import io.bluetape4k.images.vips.java21.JVipsImageSupportKt;
            import java.util.Base64;

            public final class Java21VipsBomConsumer {
                private Java21VipsBomConsumer() {
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
                    } finally {
                        JVipsRuntime.INSTANCE.shutdown();
                    }
                }
            }
            """.trimIndent() + "\n",
        )
        project.resolve("src/main/java/consumer/Java25VipsBomConsumer.java").writeText(
            """
            package consumer;

            import io.bluetape4k.images.vips.VipsImage;
            import io.bluetape4k.images.vips.java25.FfmVipsImageSupportKt;
            import io.bluetape4k.images.vips.java25.FfmVipsRuntime;
            import java.util.Base64;

            public final class Java25VipsBomConsumer {
                private Java25VipsBomConsumer() {
                }

                public static void main(String[] args) throws Exception {
                    byte[] png = Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
                    );
                    FfmVipsRuntime.INSTANCE.init(4, 100_000_000L);
                    try (VipsImage image = FfmVipsImageSupportKt.ffmVipsImageOf(png)) {
                        if (image.getWidth() != 1 || image.getHeight() != 1) {
                            throw new IllegalStateException("unexpected FFM consumer image dimensions");
                        }
                    } finally {
                        FfmVipsRuntime.INSTANCE.shutdown();
                    }
                }
            }
            """.trimIndent() + "\n",
        )
    }
}

val verifyVipsJava21BomConsumer = tasks.register<Exec>("verifyVipsJava21BomConsumer") {
    description = "Run the versionless Java 21 JNI consumer from the published Vips BOM"
    group = "verification"
    dependsOn(prepareVipsBomConsumer)
    outputs.upToDateWhen { false }
    commandLine(
        rootProject.file("gradlew").absolutePath,
        "--project-dir",
        vipsConsumerProject.get().asFile.absolutePath,
        "--no-daemon",
        "--no-configuration-cache",
        "--console=plain",
        "runJava21Consumer",
    )
    doLast {
        val report = vipsConsumerReports.get().asFile.resolve("java21-jni-bom-consumer.txt")
        report.parentFile.mkdirs()
        report.writeText("Java 21 JNI BOM consumer: PASS\n")
    }
}

val verifyVipsJava25BomConsumerResolution = tasks.register<Exec>("verifyVipsJava25BomConsumerResolution") {
    description = "Resolve and compile the versionless Java 25 FFM consumer without loading native libvips"
    group = "verification"
    dependsOn(prepareVipsBomConsumer)
    mustRunAfter(verifyVipsJava21BomConsumer)
    outputs.upToDateWhen { false }
    commandLine(
        rootProject.file("gradlew").absolutePath,
        "--project-dir",
        vipsConsumerProject.get().asFile.absolutePath,
        "--no-daemon",
        "--no-configuration-cache",
        "--console=plain",
        "verifyResolvedCoordinates",
        "classes",
    )
    doLast {
        val report = vipsConsumerReports.get().asFile.resolve("java25-ffm-bom-resolution.txt")
        report.parentFile.mkdirs()
        report.writeText("Java 25 FFM BOM resolution and compile: PASS\n")
    }
}

val verifyVipsJava25BomConsumer = tasks.register<Exec>("verifyVipsJava25BomConsumer") {
    description = "Run the versionless Java 25 FFM consumer from the published Vips BOM"
    group = "verification"
    dependsOn(prepareVipsBomConsumer)
    mustRunAfter(verifyVipsJava25BomConsumerResolution)
    outputs.upToDateWhen { false }
    commandLine(
        rootProject.file("gradlew").absolutePath,
        "--project-dir",
        vipsConsumerProject.get().asFile.absolutePath,
        "--no-daemon",
        "--no-configuration-cache",
        "--console=plain",
        "runJava25Consumer",
    )
    doLast {
        val report = vipsConsumerReports.get().asFile.resolve("java25-ffm-bom-consumer.txt")
        report.parentFile.mkdirs()
        report.writeText("Java 25 FFM BOM consumer: PASS\n")
    }
}

val verifyVipsBomPublicationMetadata = tasks.register("verifyVipsBomPublicationMetadata") {
    description = "Verify Vips BOM POM, Gradle variants, coordinates, JARs, and Java 25 bytecode"
    group = "verification"
    dependsOn(prepareVipsBomConsumer)
    mustRunAfter(verifyVipsJava25BomConsumer)

    doLast {
        val repository = vipsConsumerRepository.get().asFile
        val group = rootProject.group.toString()
        val version = rootProject.version.toString()
        val groupPath = group.replace('.', '/')
        val modules = setOf(
            "bluetape4k-images-vips-api",
            "bluetape4k-images-vips-java21",
            "bluetape4k-images-vips-java25",
        )
        val publishedJars = modules.associateWith { module ->
            repository.resolve("$groupPath/$module/$version/$module-$version.jar")
        }
        publishedJars.forEach { (module, jar) ->
            require(jar.isFile) { "expected published $module main jar at $jar" }
        }

        val bomDirectory = repository.resolve("$groupPath/bluetape4k-image-bom/$version")
        val bomPom = bomDirectory.resolve("bluetape4k-image-bom-$version.pom")
        val bomModule = bomDirectory.resolve("bluetape4k-image-bom-$version.module")
        require(bomPom.isFile && bomModule.isFile) {
            "expected published BOM POM and Gradle module metadata in $bomDirectory"
        }

        val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val pomDocument = documentBuilderFactory.newDocumentBuilder().parse(bomPom)
        val dependencyManagementNodes = pomDocument.getElementsByTagName("dependencyManagement")
        require(dependencyManagementNodes.length == 1) {
            "expected exactly one dependencyManagement element in $bomPom"
        }
        val dependencyManagement = dependencyManagementNodes.item(0)
        val managedDependencies = (0 until dependencyManagement.childNodes.length)
            .map { dependencyManagement.childNodes.item(it) }
            .singleOrNull { it.nodeName == "dependencies" }
            ?: error("missing direct dependencyManagement/dependencies element in $bomPom")
        val pomCoordinates = buildSet {
            for (index in 0 until managedDependencies.childNodes.length) {
                val dependency = managedDependencies.childNodes.item(index)
                if (dependency.nodeName != "dependency") continue
                val children = dependency.childNodes
                var dependencyGroup: String? = null
                var dependencyModule: String? = null
                var dependencyVersion: String? = null
                var dependencyScope: String? = null
                for (childIndex in 0 until children.length) {
                    val child = children.item(childIndex)
                    when (child.nodeName) {
                        "groupId" -> dependencyGroup = child.textContent.trim()
                        "artifactId" -> dependencyModule = child.textContent.trim()
                        "version" -> dependencyVersion = child.textContent.trim()
                        "scope" -> dependencyScope = child.textContent.trim()
                    }
                }
                if (dependencyGroup != null && dependencyModule != null && dependencyVersion != null) {
                    require(dependencyScope == null) {
                        "platform constraint $dependencyGroup:$dependencyModule must not declare scope=$dependencyScope"
                    }
                    add("$dependencyGroup:$dependencyModule:$dependencyVersion")
                }
            }
        }
        val expectedCoordinates = modules.mapTo(mutableSetOf()) { "$group:$it:$version" }
        require(pomCoordinates.containsAll(expectedCoordinates)) {
            "BOM POM is missing Vips dependency-management entries: ${expectedCoordinates - pomCoordinates}"
        }

        fun parseJsonObject(file: File): Map<*, *> =
            requireNotNull(JsonSlurper().parse(file) as? Map<*, *>) { "invalid JSON object in $file" }

        val bomJson = parseJsonObject(bomModule)
        val bomConstraints = (bomJson["variants"] as? List<*>).orEmpty()
            .flatMap { variant -> ((variant as? Map<*, *>)?.get("dependencyConstraints") as? List<*>).orEmpty() }
            .mapNotNull { constraint ->
                val entry = constraint as? Map<*, *> ?: return@mapNotNull null
                val constraintGroup = entry["group"] as? String ?: return@mapNotNull null
                val constraintModule = entry["module"] as? String ?: return@mapNotNull null
                val constraintVersion = ((entry["version"] as? Map<*, *>)?.get("requires") as? String)
                    ?: return@mapNotNull null
                "$constraintGroup:$constraintModule:$constraintVersion"
            }
            .toSet()
        require(bomConstraints.containsAll(expectedCoordinates)) {
            "BOM Gradle metadata is missing Vips constraints: ${expectedCoordinates - bomConstraints}"
        }

        modules.forEach { module ->
            val moduleFile = repository.resolve("$groupPath/$module/$version/$module-$version.module")
            val pomFile = repository.resolve("$groupPath/$module/$version/$module-$version.pom")
            require(moduleFile.isFile && pomFile.isFile) {
                "expected published POM and Gradle metadata for $module"
            }
            val moduleJson = parseJsonObject(moduleFile)
            val component = moduleJson["component"] as? Map<*, *>
                ?: error("missing component metadata in $moduleFile")
            require(component["group"] == group && component["module"] == module && component["version"] == version) {
                "unexpected component coordinates in $moduleFile: $component"
            }
            val variants = (moduleJson["variants"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
            for (variantName in listOf("apiElements", "runtimeElements")) {
                val variant = variants.singleOrNull { it["name"] == variantName }
                    ?: error("missing $variantName in $moduleFile")
                val attributes = variant["attributes"] as? Map<*, *>
                    ?: error("missing $variantName attributes in $moduleFile")
                require((attributes["org.gradle.jvm.version"] as? Number)?.toInt() == 25) {
                    "$module $variantName must declare Java 25 compatibility: $attributes"
                }
                val files = (variant["files"] as? List<*>).orEmpty().mapNotNull {
                    (it as? Map<*, *>)?.get("name") as? String
                }
                require("$module-$version.jar" in files) {
                    "$module $variantName does not publish the expected main JAR: $files"
                }
            }
        }

        publishedJars.values.forEach { jarFile ->
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

        val report = vipsConsumerReports.get().asFile.resolve("publication-metadata.txt")
        report.parentFile.mkdirs()
        report.writeText(
            "Vips BOM POM, Gradle variants, coordinates, and API/JNI/FFM JAR bytecode: PASS\n"
        )
        println("Validated Vips BOM POM, Gradle variants, API/JNI/FFM coordinates, and class major <= 69")
    }
}

tasks.register("verifyVipsBomConsumer") {
    description = "Verify Java 21 JNI and Java 25 FFM consumers plus published Vips BOM metadata"
    group = "verification"
    dependsOn(verifyVipsJava21BomConsumer)
    dependsOn(verifyVipsJava25BomConsumerResolution)
    dependsOn(verifyVipsJava25BomConsumer)
    dependsOn(verifyVipsBomPublicationMetadata)
}
