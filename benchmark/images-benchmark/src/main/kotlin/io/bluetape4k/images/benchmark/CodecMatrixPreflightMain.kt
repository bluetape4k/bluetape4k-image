package io.bluetape4k.images.benchmark

import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal data class CodecMatrixPreflightArguments(
    val backend: CodecMatrixBackend,
    val runId: CodecMatrixRunId,
)

internal fun parseCodecMatrixPreflightArguments(arguments: Array<String>): CodecMatrixPreflightArguments {
    require(arguments.size == 4 && arguments[0] == "--backend" && arguments[2] == "--run-id") {
        "usage: CodecMatrixPreflightMain --backend <java21|java25> --run-id <run-id>"
    }
    return CodecMatrixPreflightArguments(
        backend = CodecMatrixBackend.parse(arguments[1]),
        runId = CodecMatrixRunId(arguments[3]),
    )
}

internal object CodecMatrixPreflightMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val parsed = parseCodecMatrixPreflightArguments(arguments)
        val repositoryRoot = Path.of("").toAbsolutePath().normalize()
        requireSafeRegularFile(repositoryRoot.resolve("settings.gradle.kts"), "repository settings")
        requireSafeRegularFile(
            repositoryRoot.resolve("benchmark/images-benchmark/build.gradle.kts"),
            "benchmark module build",
        )
        val manifest = preflightCodecMatrix(
            runId = parsed.runId,
            backend = parsed.backend,
            probes = systemPreflightProbes(repositoryRoot, parsed.backend),
        )
        val target = repositoryRoot.resolve(
            "benchmark/images-benchmark/build/codec-matrix/${parsed.runId.value}/preflight-${parsed.backend.selector}.json",
        )
        CodecMatrixJson.write(target, manifest)
        check(manifest.status != CodecMatrixCellStatus.ERROR) { "codec matrix preflight failed" }
    }
}

private fun systemPreflightProbes(
    repositoryRoot: Path,
    backend: CodecMatrixBackend,
): CodecMatrixPreflightProbes = CodecMatrixPreflightProbes(
    host = {
        CodecMatrixHostFacts(
            osName = System.getProperty("os.name", "unknown"),
            kernelVersion = System.getProperty("os.version", "unknown"),
            architecture = CodecMatrixArchitecture.parse(System.getProperty("os.arch", "unknown")),
            cpuModel = probeCpuModel(),
        )
    },
    jdk = {
        CodecMatrixJdkFacts(
            vendor = System.getProperty("java.vendor", "unknown"),
            version = System.getProperty("java.runtime.version", System.getProperty("java.version", "unknown")),
            major = Runtime.version().feature(),
        )
    },
    jniBinaryArchitecture = { backend -> detectJniBinaryArchitecture(backend) },
    git = { probeGit(repositoryRoot) },
    diskAvailableBytes = { Files.getFileStore(repositoryRoot).usableSpace },
    nativeAccessEnabled = {
        ManagementFactory.getRuntimeMXBean().inputArguments.any { argument ->
            argument == "--enable-native-access=ALL-UNNAMED" || argument == "--enable-native-access"
        }
    },
    loaderPathAvailable = {
        val bundledJniLibvips = backend.requiresJniBinary &&
                CodecMatrixPreflightMain::class.java.classLoader.getResource("libvips.so") != null
        libvipsLoaderPathAvailable() || bundledJniLibvips
    },
)

private fun detectJniBinaryArchitecture(backend: CodecMatrixBackend): CodecMatrixArchitecture? {
    if (!backend.requiresJniBinary) return null
    val resource = when {
        System.getProperty("os.name", "").contains("mac", ignoreCase = true) -> "libJVips.dylib"
        System.getProperty("os.name", "").contains("win", ignoreCase = true) -> "JVips.dll"
        else -> "libJVips.so"
    }
    val header = CodecMatrixPreflightMain::class.java.classLoader.getResourceAsStream(resource)
        ?.use { stream -> stream.readNBytes(4096) }
        ?: return null
    return codecMatrixBinaryArchitecture(header)
}

internal fun codecMatrixBinaryArchitecture(header: ByteArray): CodecMatrixArchitecture? {
    if (header.size < 20) return null
    if (header[0] == 0x7F.toByte() && header.copyOfRange(1, 4).contentEquals("ELF".toByteArray())) {
        val order = if (header[5].toInt() == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        return when (ByteBuffer.wrap(header, 18, 2).order(order).short.toInt() and 0xFFFF) {
            0x3E -> CodecMatrixArchitecture.X86_64
            0xB7 -> CodecMatrixArchitecture.ARM64
            else -> CodecMatrixArchitecture.UNKNOWN
        }
    }
    if (header[0] == 'M'.code.toByte() && header[1] == 'Z'.code.toByte() && header.size >= 64) {
        val peOffset = ByteBuffer.wrap(header, 0x3C, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (peOffset >= 0 && header.size >= peOffset + 6 &&
            header.copyOfRange(peOffset, peOffset + 4).contentEquals("PE\u0000\u0000".toByteArray())
        ) {
            return when (ByteBuffer.wrap(header, peOffset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF) {
                0x8664 -> CodecMatrixArchitecture.X86_64
                0xAA64 -> CodecMatrixArchitecture.ARM64
                else -> CodecMatrixArchitecture.UNKNOWN
            }
        }
    }
    val magic = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.BIG_ENDIAN).int
    val order = when (magic) {
        0xCFFAEDFE.toInt(), 0xCEFAEDFE.toInt() -> ByteOrder.LITTLE_ENDIAN
        0xFEEDFACF.toInt(), 0xFEEDFACE.toInt() -> ByteOrder.BIG_ENDIAN
        else -> return null
    }
    return when (ByteBuffer.wrap(header, 4, 4).order(order).int) {
        0x01000007 -> CodecMatrixArchitecture.X86_64
        0x0100000C -> CodecMatrixArchitecture.ARM64
        else -> CodecMatrixArchitecture.UNKNOWN
    }
}

private fun probeCpuModel(): String {
    if (System.getProperty("os.name", "").contains("linux", ignoreCase = true)) {
        val cpuInfo = Path.of("/proc/cpuinfo")
        if (Files.isRegularFile(cpuInfo)) {
            Files.newBufferedReader(cpuInfo).useLines { lines ->
                lines.firstOrNull { line -> line.startsWith("model name") }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let { return it }
            }
        }
    }
    if (System.getProperty("os.name", "").contains("mac", ignoreCase = true)) {
        runCatching {
            val process = ProcessBuilder("/usr/sbin/sysctl", "-n", "machdep.cpu.brand_string")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.use { it.readNBytes(257) }
            if (output.size <= 256 && process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                output.toString(Charsets.UTF_8).trim().takeIf(String::isNotBlank)?.let { return it }
            }
            process.destroyForcibly()
        }
    }
    return "${System.getProperty("os.arch", "unknown")} ${Runtime.getRuntime().availableProcessors()} processors"
}

private fun probeGit(repositoryRoot: Path): CodecMatrixGitFacts {
    val sha = runGit(repositoryRoot, "rev-parse", "HEAD").trim()
    val dirty = runGit(repositoryRoot, "status", "--porcelain", "--untracked-files=normal").isNotBlank()
    return CodecMatrixGitFacts(sha = sha, dirty = dirty)
}

private fun runGit(repositoryRoot: Path, vararg arguments: String): String {
    val outputFile = Files.createTempFile("codec-matrix-git-", ".out")
    try {
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(repositoryRoot.toFile())
            .redirectErrorStream(true)
            .redirectOutput(outputFile.toFile())
            .start()
        require(process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            "git probe timed out"
        }
        require(process.exitValue() == 0) { "git probe returned a non-zero exit status" }
        require(Files.size(outputFile) <= 4096) { "git probe output exceeds the accepted bound" }
        return Files.readString(outputFile)
    } finally {
        Files.deleteIfExists(outputFile)
    }
}

private fun libvipsLoaderPathAvailable(): Boolean {
    val libraryNames = listOf("libvips.dylib", "libvips.so", "libvips.so.42", "vips.dll")
    val standardDirectories = listOf(Path.of("/opt/homebrew/lib"), Path.of("/usr/local/lib"), Path.of("/usr/lib"))
    val configuredDirectories = System.getProperty("java.library.path", "")
        .split(System.getProperty("path.separator"))
        .filter(String::isNotBlank)
        .map(Path::of)
    return (standardDirectories + configuredDirectories).any { directory ->
        libraryNames.any { name -> Files.isRegularFile(directory.resolve(name)) }
    }
}
