package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageObjectMetadata
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.ImageUploadResult
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessDeniedException as NioAccessDeniedException
import java.nio.file.DirectoryStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.SecureDirectoryStream
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.UUID

/**
 * local filesystem 기반 [ImageStorage]입니다.
 *
 * ## 동작/계약
 * - 모든 suspend method는 [Dispatchers.IO]로 이동합니다.
 * - path traversal과 root 내부 symbolic link 우회를 방지합니다. 모든 key는 real root 아래로
 *   resolve되고 기존 경로 segment에 symbolic link가 있으면
 *   [ImageStorageException.ValidationException]을 던집니다.
 *   실제 파일 열기·임시 파일 생성·교체·삭제는 [SecureDirectoryStream]의 descriptor-relative
 *   operation으로 수행해 검사와 사용 사이의 symbolic-link 교체 경합을 차단합니다.
 * - storage root 아래 parent directory는 trusted bootstrap 단계에서 미리 생성되어 있어야 합니다.
 *   JDK [SecureDirectoryStream]에 `mkdirat`가 없으므로 operation 중 missing parent를 생성하지
 *   않고 [ImageStorageException.ValidationException]으로 fail closed합니다.
 * - [maxSizeBytes]보다 큰 upload는 byte를 쓰기 전에 거부합니다.
 * - [maxSizeBytes]보다 큰 object download는 byte를 읽기 전에 거부합니다.
 * - [ImageObjectMetadataReader.readMetadata]는 body를 열지 않고 regular-file attributes만 반환합니다.
 *   Local backend가 보장하지 않는 ETag과 content type은 null입니다.
 * - [delete]는 idempotent입니다. missing key는 예외를 일으키지 않습니다.
 * - [list]는 storage root 기준 상대 경로로 resolve된 [ImageObjectKey]의 cold [Flow]를 반환합니다.
 *   cancellation은 underlying directory walk를 중단합니다.
 * - 모든 catch block은 [CancellationException]을 먼저 다시 던집니다. permission 오류는
 *   [ImageStorageException.AccessDeniedException]으로, 일반 [IOException]은
 *   [ImageStorageException.TransientException]으로, [NoSuchFileException]은
 *   [ImageStorageException.NotFoundException]으로 wrap합니다.
 */
class LocalImageStorage(
    rootDir: Path,
    private val maxSizeBytes: Long,
) : ImageStorage, ImageObjectMetadataReader, AutoCloseable {

    companion object : KLogging() {
        /**
         * trusted startup 단계에서 local root와 고정된 bootstrap prefix를 준비합니다.
         *
         * 호출이 완료된 뒤 [LocalImageStorage]는 runtime에 missing parent를 생성하지 않습니다.
         * prefix는 설정 파일에서 고정된 상대 경로로만 받아야 하며, 이 함수는 symbolic link를
         * directory segment로 허용하지 않습니다.
         */
        fun provisionRoot(rootDir: Path, prefixes: Set<String>): Path {
            val normalizedRoot = rootDir.toAbsolutePath().normalize()
            ensureBootstrapRoot(normalizedRoot)
            val realRoot = normalizedRoot.toRealPath()
            prefixes.sorted().map { prefix ->
                prefix to validateBootstrapPrefix(prefix)
            }.forEach { (prefix, relative) ->
                var current = realRoot
                relative.forEach { segment ->
                    current = current.resolve(segment)
                    ensureBootstrapDirectory(current, "bootstrap prefix: $prefix")
                }
            }
            return realRoot
        }

        private fun validateBootstrapPrefix(prefix: String): List<Path> {
            require(prefix.isNotBlank()) { "local.bootstrap-prefixes must not contain blank values" }
            val candidate = ImageObjectKey.of(prefix, ".bootstrap")
            val relative = Path.of(candidate.prefix).normalize()
            require(!relative.isAbsolute && relative.nameCount > 0) {
                "local.bootstrap-prefixes must be relative paths: $prefix"
            }
            return relative.toList()
        }

        private fun ensureBootstrapDirectory(path: Path, description: String) {
            try {
                val existing = Files.readAttributes(
                    path,
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                )
                require(!existing.isSymbolicLink && existing.isDirectory) {
                    "$description must be a real directory: $path"
                }
            } catch (_: NoSuchFileException) {
                try {
                    Files.createDirectory(path)
                } catch (e: FileAlreadyExistsException) {
                    val raced = Files.readAttributes(
                        path,
                        BasicFileAttributes::class.java,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                    require(!raced.isSymbolicLink && raced.isDirectory) {
                        "$description must be a real directory: $path"
                    }
                }
            }
        }

        private fun ensureBootstrapRoot(path: Path) {
            try {
                val existing = Files.readAttributes(
                    path,
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                )
                require(!existing.isSymbolicLink && existing.isDirectory) {
                    "storage root must be a real directory: $path"
                }
            } catch (_: NoSuchFileException) {
                Files.createDirectories(path)
                val created = Files.readAttributes(
                    path,
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                )
                require(!created.isSymbolicLink && created.isDirectory) {
                    "storage root must be a real directory: $path"
                }
            }
        }
    }

    // provider-specific Path를 문자열로 round-trip하지 않아 ZipFS 등 non-default provider를 보존합니다.
    private val normalizedRoot: Path = rootDir.toAbsolutePath().normalize()
    private val realRoot: Path = run {
        val normalized = normalizedRoot
        val attributes = try {
            Files.readAttributes(
                normalized,
                BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
        } catch (e: NoSuchFileException) {
            throw IllegalArgumentException(
                "Storage root must be provisioned before LocalImageStorage construction: $normalized",
                e,
            )
        }
        require(!attributes.isSymbolicLink && attributes.isDirectory) {
            "Storage root must be a real directory: $normalized"
        }
        normalized.toRealPath()
    }

    private val realRootFileKey: String? = Files.readAttributes(
        realRoot,
        BasicFileAttributes::class.java,
        LinkOption.NOFOLLOW_LINKS,
    ).fileKey()?.toString()

    init {
        require(maxSizeBytes > 0) { "maxSizeBytes must be positive: $maxSizeBytes" }
    }

    /**
     * [key]를 [rootDir] 아래로 resolve하고 resolved path가 root 안에 머무는지 확인합니다.
     *
     * traversal attempt에서는 [ImageStorageException.ValidationException]을 던집니다.
     */
    private fun resolveKey(key: ImageObjectKey): Path {
        ensureRootPathAnchored(key)
        val resolved = realRoot.resolve(key.fullKey).normalize()
        if (!resolved.startsWith(realRoot)) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Path traversal detected for key: ${key.fullKey}",
            )
        }
        rejectSymbolicLinks(key, resolved)
        return resolved
    }

    override suspend fun upload(
        key: ImageObjectKey,
        bytes: ByteArray,
        options: UploadOptions,
    ): ImageUploadResult = withContext(Dispatchers.IO) {
        if (bytes.size > maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Upload exceeds maxSizeBytes ($maxSizeBytes): ${bytes.size}",
            )
        }
        val target = resolveKey(key)
        atomicWrite(key, target) { staged, _ ->
            val channel = staged
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            forceChannel(channel)
        }
        ImageUploadResult(
            key = key,
            etag = bytes.size.toString(),
            sizeBytes = bytes.size.toLong(),
            contentType = options.contentType,
            uploadedAt = Instant.now(),
        )
    }

    override suspend fun upload(
        key: ImageObjectKey,
        source: Path,
        options: UploadOptions,
    ): ImageUploadResult = withContext(Dispatchers.IO) {
        val size = try {
            Files.size(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchFileException) {
            throw ImageStorageException.NotFoundException(key, cause = e)
        } catch (e: NioAccessDeniedException) {
            throw ImageStorageException.AccessDeniedException(key = key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
        if (size > maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Upload exceeds maxSizeBytes ($maxSizeBytes): $size",
            )
        }
        val target = resolveKey(key)
        var actualSize = 0L
        atomicWrite(key, target) { staged, _ ->
            Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input ->
                actualSize = copyToChannel(input, staged, key)
            }
        }
        ImageUploadResult(
            key = key,
            etag = actualSize.toString(),
            sizeBytes = actualSize,
            contentType = options.contentType,
            uploadedAt = Instant.now(),
        )
    }

    override suspend fun download(key: ImageObjectKey): ByteArray = withContext(Dispatchers.IO) {
        val path = resolveKey(key)
        val attributes = readObjectAttributes(key, path)
        if (attributes == null) {
            throw ImageStorageException.NotFoundException(key)
        }
        try {
            validateStoredSize(key, attributes.size())
            val bytes = withSecureStorageFile(path, setOf(StandardOpenOption.READ)) { channel ->
                Channels.newInputStream(channel).use { input ->
                    readBoundedBytes(input, key)
                }
            }
            validateStoredSize(key, bytes.size.toLong())
            bytes
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchFileException) {
            throw ImageStorageException.NotFoundException(key, cause = e)
        } catch (e: NioAccessDeniedException) {
            throw ImageStorageException.AccessDeniedException(key = key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    override suspend fun readMetadata(key: ImageObjectKey): ImageObjectMetadata = withContext(Dispatchers.IO) {
        val path = resolveKey(key)
        val attributes = readObjectAttributes(key, path)
            ?: throw ImageStorageException.NotFoundException(key)
        ImageObjectMetadata(
            key = key,
            sizeBytes = attributes.size(),
            lastModified = attributes.lastModifiedTime().toInstant(),
        )
    }

    override suspend fun download(key: ImageObjectKey, destination: Path): Unit =
        withContext(Dispatchers.IO) {
            val path = resolveKey(key)
            val attributes = readObjectAttributes(key, path)
            if (attributes == null) {
                throw ImageStorageException.NotFoundException(key)
            }
            try {
                validateStoredSize(key, attributes.size())
                val target = destination.toAbsolutePath().normalize()
                // Snapshot the source before opening the destination descriptor. A target inside
                // the storage root otherwise requires two live SecureDirectoryStreams in one
                // operation, which some Linux providers reject even when both paths are valid.
                // The snapshot remains bounded streaming and is removed before this method returns.
                val sourceSnapshot = Files.createTempFile("bluetape4k-image-download-", ".tmp")
                try {
                    withSecureStorageFile(path, setOf(StandardOpenOption.READ)) { sourceChannel ->
                        Files.newByteChannel(
                            sourceSnapshot,
                            setOf(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING),
                        ).use { snapshotChannel ->
                            Channels.newInputStream(sourceChannel).use { input ->
                                copyToChannel(input, snapshotChannel, key)
                            }
                        }
                    }
                    atomicWrite(key, target, suffix = "download") { staged, _ ->
                        Files.newInputStream(sourceSnapshot, LinkOption.NOFOLLOW_LINKS).use { input ->
                            copyToChannel(input, staged, key)
                        }
                    }
                } finally {
                    try {
                        Files.deleteIfExists(sourceSnapshot)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        log.warn(e) { "Failed to delete download source snapshot: $sourceSnapshot" }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSuchFileException) {
                throw ImageStorageException.NotFoundException(key, cause = e)
            } catch (e: NioAccessDeniedException) {
                throw ImageStorageException.AccessDeniedException(key = key, cause = e)
            } catch (e: IOException) {
                throw ImageStorageException.TransientException(key = key, cause = e)
            }
        }

    override suspend fun delete(key: ImageObjectKey): Unit = withContext(Dispatchers.IO) {
        try {
            val path = resolveKey(key)
            withSecureDirectory(path) { directory, fileName ->
                directory.deleteFile(fileName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchFileException) {
            // idempotent: a missing parent or object is already deleted.
        } catch (e: NioAccessDeniedException) {
            throw ImageStorageException.AccessDeniedException(key = key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    /** 호출별로 root descriptor를 열고 닫으므로 유지할 런타임 리소스가 없습니다. */
    override fun close() = Unit

    override suspend fun exists(key: ImageObjectKey): Boolean = withContext(Dispatchers.IO) {
        try {
            readObjectAttributes(key, resolveKey(key)) != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImageStorageException.AccessDeniedException) {
            throw e
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    override fun list(prefix: ImageObjectKey): Flow<ImageObjectKey> = flow {
        currentCoroutineContext().ensureActive()
        val prefixPath = try {
            resolveKey(prefix)
        } catch (e: ImageStorageException.ValidationException) {
            throw e
        }
        currentCoroutineContext().ensureActive()
        val prefixAttributes = readSecureAttributes(prefix, prefixPath)
        if (prefixAttributes == null || !prefixAttributes.isDirectory) {
            return@flow
        }
        try {
            withSecureStorageDirectory(prefixPath) { directory ->
                collectSecureFiles(directory, relativeRootSegments(prefixPath)) { key ->
                    currentCoroutineContext().ensureActive()
                    emit(key)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NioAccessDeniedException) {
            throw ImageStorageException.AccessDeniedException(key = prefix, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = prefix, cause = e)
        }
    }.buffer(capacity = 0).flowOn(Dispatchers.IO)

    private fun validateStoredSize(key: ImageObjectKey, size: Long) {
        if (size > maxSizeBytes) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "File exceeds maxSizeBytes ($maxSizeBytes): $size",
            )
        }
    }

    private suspend fun atomicWrite(
        key: ImageObjectKey,
        target: Path,
        suffix: String = "upload",
        write: (SeekableByteChannel, SecureDirectoryStream<Path>?) -> Unit,
    ) {
        val parent = target.parent ?: throw ImageStorageException.ValidationException(
            key = key,
            message = "Storage target has no parent directory: ${key.fullKey}",
        )
        try {
            if (target.startsWith(realRoot)) {
                ensureRootPathAnchored(key)
                requireProvisionedParent(key, parent)
            } else {
                Files.createDirectories(parent)
            }
            withSecureDirectoryContext(target) { rootDirectory, directory, fileName ->
                val stagedName = Path.of(".${target.fileName}.${UUID.randomUUID()}.$suffix")
                var staged = false
                try {
                    directory.newByteChannel(
                        stagedName,
                        setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    ).use { channel ->
                        staged = true
                        write(channel, rootDirectory)
                        forceChannel(channel)
                    }
                    directory.move(stagedName, directory, fileName)
                    staged = false
                } finally {
                    if (staged) {
                        deleteSecurePartialQuietly(directory, stagedName)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NioAccessDeniedException) {
            throw ImageStorageException.AccessDeniedException(key = key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    /**
     * Parent directory는 trusted bootstrap 단계에서 미리 준비되어 있어야 합니다.
     *
     * JDK [SecureDirectoryStream]에는 `mkdirat`에 해당하는 API가 없습니다. 따라서
     * operation 중 missing parent를 path 기반으로 생성하면 root 또는 parent rename 경합에서
     * storage root 밖에 directory를 만들 수 있습니다. 이 경계에서는 생성 대신 descriptor-
     * relative 조회로 parent가 이미 root 안에 존재하는지 확인하고, 없으면 fail closed합니다.
     */
    private suspend fun requireProvisionedParent(key: ImageObjectKey, parent: Path) {
        try {
            withRootDirectory { rootDirectory ->
                if (parent == realRoot) return@withRootDirectory
                withSecureDirectory(
                    rootDirectory,
                    relativeRootSegments(parent),
                    parent.fileName,
                ) { _, _ -> }
            }
        } catch (e: ImageStorageException.ValidationException) {
            throw e
        } catch (e: NoSuchFileException) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Storage parent directory must be provisioned before upload: ${parent}",
                cause = e,
            )
        } catch (e: NioAccessDeniedException) {
            throw ImageStorageException.AccessDeniedException(key = key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Storage parent directory is not a secure directory: ${parent}",
                cause = e,
            )
        }
    }

    private fun forceChannel(channel: SeekableByteChannel) {
        if (channel is FileChannel) {
            channel.force(true)
        }
    }

    private fun copyToChannel(input: java.io.InputStream, output: SeekableByteChannel, key: ImageObjectKey): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            copied += count
            validateStoredSize(key, copied)
            var pending = ByteBuffer.wrap(buffer, 0, count)
            while (pending.hasRemaining()) {
                output.write(pending)
            }
        }
        return copied
    }

    private fun readBoundedBytes(input: java.io.InputStream, key: ImageObjectKey): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            copied += count
            validateStoredSize(key, copied)
            if (copied > Int.MAX_VALUE) {
                throw ImageStorageException.ValidationException(
                    key = key,
                    message = "File cannot be represented as a ByteArray: $copied",
                )
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private suspend fun <T> withSecureStorageFile(
        path: Path,
        options: Set<OpenOption>,
        block: (SeekableByteChannel) -> T,
    ): T = withSecureDirectory(path) { directory, fileName ->
        directory.newByteChannel(fileName, options + LinkOption.NOFOLLOW_LINKS).use(block)
    }

    private suspend fun <T> withSecureStorageFile(
        rootDirectory: SecureDirectoryStream<Path>,
        path: Path,
        options: Set<OpenOption>,
        block: (SeekableByteChannel) -> T,
    ): T {
        val parent = path.parent ?: throw IOException("Storage path has no parent: $path")
        return withSecureDirectory(
            rootDirectory,
            relativeRootSegments(parent),
            path.fileName,
        ) { directory, fileName ->
            directory.newByteChannel(fileName, options + LinkOption.NOFOLLOW_LINKS).use(block)
        }
    }

    private suspend fun <T> withSecureStorageDirectory(
        path: Path,
        block: suspend (SecureDirectoryStream<Path>) -> T,
    ): T = withSecureDirectory(path) { directory, fileName ->
        directory.newDirectoryStream(fileName, LinkOption.NOFOLLOW_LINKS).use { childDirectory ->
            block(childDirectory.asSecureDirectory())
        }
    }

    private suspend fun collectSecureFiles(
        directory: SecureDirectoryStream<Path>,
        relativePrefix: List<Path>,
        emit: suspend (ImageObjectKey) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        val iterator = directory.iterator()
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!iterator.hasNext()) {
                break
            }
            currentCoroutineContext().ensureActive()
            val entry = iterator.next()
            currentCoroutineContext().ensureActive()
            val attributes = directory.getFileAttributeView(
                entry,
                BasicFileAttributeView::class.java,
                LinkOption.NOFOLLOW_LINKS,
            ).readAttributes()
            val relative = relativePrefix + entry.fileName
            when {
                attributes.isDirectory ->
                    directory.newDirectoryStream(entry, LinkOption.NOFOLLOW_LINKS).use { childDirectory ->
                        currentCoroutineContext().ensureActive()
                        collectSecureFiles(childDirectory.asSecureDirectory(), relative, emit)
                    }
                attributes.isRegularFile -> {
                    val parts = relative.map(Path::toString)
                    if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        currentCoroutineContext().ensureActive()
                        emit(ImageObjectKey.of(parts[0], parts.drop(1).joinToString("/")))
                    }
                }
            }
        }
    }

    private suspend fun <T> withSecureDirectory(
        target: Path,
        block: suspend (SecureDirectoryStream<Path>, Path) -> T,
    ): T = withSecureDirectoryContext(target) { _, directory, fileName ->
        block(directory, fileName)
    }

    private suspend fun <T> withSecureDirectoryContext(
        target: Path,
        block: suspend (SecureDirectoryStream<Path>?, SecureDirectoryStream<Path>, Path) -> T,
    ): T {
        val parent = target.parent ?: throw IOException("Target has no parent: $target")
        return if (target.startsWith(realRoot)) {
            withRootDirectory { rootDirectory ->
                withSecureDirectory(
                    rootDirectory,
                    relativeRootSegments(parent),
                    target.fileName,
                ) { directory, fileName ->
                    block(rootDirectory, directory, fileName)
                }
            }
        } else {
            Files.newDirectoryStream(parent).use { parentDirectory ->
                block(null, parentDirectory.asSecureDirectory(), target.fileName)
            }
        }
    }

    /**
     * Root descriptor를 호출마다 열고 닫습니다.
     * descriptor를 캐시하지 않아, 이전 조회 시점에 없었던 child directory가 이후 생성되어도
     * 모든 연산이 최신 디렉터리 상태를 관찰합니다. 열린 descriptor의 file key가 생성 시 root와
     * 다르면 fail closed하여 root 교체·심볼릭 링크 우회를 허용하지 않습니다.
     */
    private suspend fun <T> withRootDirectory(block: suspend (SecureDirectoryStream<Path>) -> T): T {
        val opened = Files.newDirectoryStream(realRoot).asSecureDirectory()
        try {
            val attributes = opened.getFileAttributeView(
                Path.of("."),
                BasicFileAttributeView::class.java,
                LinkOption.NOFOLLOW_LINKS,
            ).readAttributes()
            if (realRootFileKey == null || attributes.fileKey()?.toString() != realRootFileKey) {
                throw IOException("Storage root changed while opening: $realRoot")
            }
            return block(opened)
        } finally {
            opened.close()
        }
    }

    /**
     * Returns descriptor-relative segments below [realRoot].
     *
     * The JDK represents `realRoot.relativize(realRoot)` as an empty path whose
     * [Path.nameCount] is one. Iterating that path therefore yields an empty
     * segment, which Linux `SecureDirectoryStream` rejects as a missing child.
     */
    private fun relativeRootSegments(path: Path): List<Path> =
        if (path == realRoot) {
            emptyList()
        } else {
            realRoot.relativize(path).toList()
        }

    private fun ensureRootPathAnchored(key: ImageObjectKey) {
        try {
            val attributes = Files.readAttributes(
                realRoot,
                BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
            if (attributes.isSymbolicLink ||
                realRootFileKey == null ||
                attributes.fileKey()?.toString() != realRootFileKey
            ) {
                throw ImageStorageException.ValidationException(
                    key = key,
                    message = "Storage root changed or became a symbolic link: $realRoot",
                )
            }
        } catch (e: ImageStorageException.ValidationException) {
            throw e
        } catch (e: NioAccessDeniedException) {
            throw ImageStorageException.AccessDeniedException(key = key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }
    }

    private suspend fun <T> withSecureDirectory(
        current: SecureDirectoryStream<Path>,
        segments: List<Path>,
        fileName: Path,
        block: suspend (SecureDirectoryStream<Path>, Path) -> T,
    ): T {
        if (segments.isEmpty()) {
            return block(current, fileName)
        }
        return current.newDirectoryStream(segments.first(), LinkOption.NOFOLLOW_LINKS).use { childDirectory ->
            withSecureDirectory(childDirectory.asSecureDirectory(), segments.drop(1), fileName, block)
        }
    }

    private fun DirectoryStream<Path>.asSecureDirectory(): SecureDirectoryStream<Path> {
        if (this !is SecureDirectoryStream<*>) {
            throw IOException("Atomic storage operations require SecureDirectoryStream support")
        }
        @Suppress("UNCHECKED_CAST")
        return this as SecureDirectoryStream<Path>
    }

    private fun deleteSecurePartialQuietly(directory: SecureDirectoryStream<Path>, staged: Path) {
        try {
            directory.deleteFile(staged)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            log.warn(e) { "Failed to delete partial upload: $staged" }
        }
    }

    /*
     * The remaining helpers intentionally inspect paths with NOFOLLOW_LINKS before
     * opening descriptor-relative streams. They provide a friendly validation error
     * for ordinary callers while SecureDirectoryStream closes the TOCTOU window.
     */
    private fun rejectSymbolicLinks(key: ImageObjectKey, path: Path) {
        var current = realRoot
        for (segment in relativeRootSegments(path)) {
            current = current.resolve(segment)
            try {
                val attributes = Files.readAttributes(
                    current,
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                )
                if (attributes.isSymbolicLink) {
                    throw ImageStorageException.ValidationException(
                        key = key,
                        message = "Symbolic link is not allowed in storage path: ${key.fullKey}",
                    )
                }
            } catch (_: NoSuchFileException) {
                return
            } catch (e: NioAccessDeniedException) {
                throw ImageStorageException.AccessDeniedException(key = key, cause = e)
            } catch (e: IOException) {
                throw ImageStorageException.TransientException(key = key, cause = e)
            }
        }
    }

    private suspend fun readObjectAttributes(key: ImageObjectKey, path: Path): BasicFileAttributes? {
        val attributes = readSecureAttributes(key, path) ?: return null
        if (attributes.isSymbolicLink) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Symbolic link is not allowed in storage path: ${key.fullKey}",
            )
        }
        if (!attributes.isRegularFile) {
            throw ImageStorageException.ValidationException(
                key = key,
                message = "Storage object is not a regular file: ${key.fullKey}",
            )
        }
        return attributes
    }

    private suspend fun readSecureAttributes(key: ImageObjectKey, path: Path): BasicFileAttributes? =
        try {
            withSecureDirectory(path) { directory, fileName ->
                directory.getFileAttributeView(
                    fileName,
                    BasicFileAttributeView::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                ).readAttributes()
            }
        } catch (_: NoSuchFileException) {
            null
        } catch (e: NioAccessDeniedException) {
            throw ImageStorageException.AccessDeniedException(key = key, cause = e)
        } catch (e: IOException) {
            throw ImageStorageException.TransientException(key = key, cause = e)
        }

}
