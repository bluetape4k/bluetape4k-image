import org.gradle.api.Project
import java.io.File

/**
 * Repository-relative module classification shared by the root build and the BOM.
 *
 * Keep this policy in buildSrc so publication, BOM constraints, and ABI gates
 * cannot silently drift into different module sets.
 */
fun Project.repositoryRelativePath(): String = rootProject.rootDir.toPath()
    .relativize(projectDir.toPath())
    .toString()
    .replace(File.separatorChar, '/')

fun Project.isNonPublishedModule(): Boolean {
    val relativePath = repositoryRelativePath()
    return relativePath == "examples" ||
        relativePath.startsWith("examples/") ||
        relativePath == "benchmark" ||
        relativePath.startsWith("benchmark/") ||
        name.contains("-demo") ||
        name.endsWith("-benchmark")
}

/** Published JVM modules participate in ABI validation; the platform BOM does not. */
fun Project.isPublishedJvmModule(): Boolean = name != "bluetape4k-image-bom" && !isNonPublishedModule()
