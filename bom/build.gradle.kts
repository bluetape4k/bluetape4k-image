plugins {
    `java-platform`
    `maven-publish`
    signing
}

fun Project.isNonPublishedModule(): Boolean {
    val relativePath = rootProject.rootDir.toPath()
        .relativize(projectDir.toPath())
        .toString()
        .replace(File.separatorChar, '/')

    return relativePath == "examples" ||
            relativePath.startsWith("examples/") ||
            relativePath == "benchmark" ||
            relativePath.startsWith("benchmark/") ||
            name.contains("-demo") ||
            name.endsWith("-benchmark")
}

dependencies {
    constraints {
        rootProject.subprojects {
            if (name != "bluetape4k-image-bom" && !isNonPublishedModule()) {
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
}

configurePublishingSigning("BluetapeImage")
