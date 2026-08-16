package io.bluetape4k.images.spring.storage

import java.io.IOException
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SecureDirectoryStream
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFileAttributeView
import java.util.UUID

/**
 * Local storage가 의존하는 provider capability를 테스트에서만 확인합니다.
 *
 * 이 probe는 production fallback을 선택하지 않습니다. `SecureDirectoryStream`이 없거나
 * 기존 target 교체가 지원되지 않으면 해당 provider를 unsupported로 기록하고, contract test가
 * 명시적인 N/A 사유 또는 fail-closed 결과를 검증하도록 합니다.
 */
internal object LocalFileSystemCapabilityProbe {

    fun inspect(root: Path): LocalFileSystemCapabilities {
        val provider = root.fileSystem.provider()
        val supportsPosix = runCatching {
            Files.getFileStore(root).supportsFileAttributeView(PosixFileAttributeView::class.java)
        }.getOrDefault(false)

        var streamType = DirectoryStream::class.java.name
        var supportsSecure = false
        var supportsAtomicReplace = false
        Files.newDirectoryStream(root).use { stream ->
            streamType = stream::class.java.name
            supportsSecure = stream is SecureDirectoryStream<*>
            if (supportsSecure) {
                @Suppress("UNCHECKED_CAST")
                supportsAtomicReplace = probeAtomicExistingTargetReplace(stream as SecureDirectoryStream<Path>)
            }
        }

        return LocalFileSystemCapabilities(
            providerScheme = provider.scheme,
            directoryStreamType = streamType,
            supportsSecureDirectoryStream = supportsSecure,
            supportsAtomicExistingTargetReplace = supportsAtomicReplace,
            supportsPosixAttributes = supportsPosix,
        )
    }

    private fun probeAtomicExistingTargetReplace(directory: SecureDirectoryStream<Path>): Boolean {
        val suffix = UUID.randomUUID().toString()
        val sourceName = Path.of(".capability-source-$suffix")
        val targetName = Path.of(".capability-target-$suffix")
        return try {
            write(directory, sourceName, NEW_CONTENT)
            write(directory, targetName, OLD_CONTENT)

            directory.move(sourceName, directory, targetName)

            read(directory, targetName) == NEW_CONTENT && !exists(directory, sourceName)
        } catch (_: IOException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        } finally {
            runCatching { directory.deleteFile(sourceName) }
            runCatching { directory.deleteFile(targetName) }
        }
    }

    private fun read(directory: SecureDirectoryStream<Path>, name: Path): String =
        directory.newByteChannel(name, setOf(StandardOpenOption.READ)).use { channel ->
            val buffer = ByteBuffer.allocate(NEW_CONTENT.toByteArray(StandardCharsets.UTF_8).size)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) break
                // Continue until the bounded probe payload is consumed.
            }
            String(buffer.array(), StandardCharsets.UTF_8)
        }

    private fun write(directory: SecureDirectoryStream<Path>, name: Path, value: String) {
        val buffer = ByteBuffer.wrap(value.toByteArray(StandardCharsets.UTF_8))
        directory.newByteChannel(
            name,
            setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
        ).use { channel ->
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
        }
    }

    private fun exists(directory: SecureDirectoryStream<Path>, name: Path): Boolean =
        try {
            directory.newByteChannel(name, setOf(StandardOpenOption.READ)).use { }
            true
        } catch (_: NoSuchFileException) {
            false
        }

    private const val OLD_CONTENT = "old"
    private const val NEW_CONTENT = "new"
}

internal data class LocalFileSystemCapabilities(
    val providerScheme: String,
    val directoryStreamType: String,
    val supportsSecureDirectoryStream: Boolean,
    val supportsAtomicExistingTargetReplace: Boolean,
    val supportsPosixAttributes: Boolean,
) : Serializable {

    val unsupportedReason: String
        get() = when {
            !supportsSecureDirectoryStream ->
                "N/A: provider '$providerScheme' does not expose SecureDirectoryStream"
            !supportsAtomicExistingTargetReplace ->
                "N/A: provider '$providerScheme' cannot atomically replace an existing target"
            !supportsPosixAttributes ->
                "N/A: provider '$providerScheme' does not expose POSIX attributes"
            else -> "supported provider: $providerScheme"
        }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}
