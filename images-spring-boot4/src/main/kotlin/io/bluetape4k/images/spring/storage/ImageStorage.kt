package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageUploadResult
import io.bluetape4k.images.spring.UploadOptions
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

/**
 * Abstraction over image object storage backends.
 *
 * ## Behavior
 * - All operations are suspend and safe to call from a coroutine context.
 * - Upload operations are atomic: partial writes must not be visible to readers.
 * - [download] throws [io.bluetape4k.images.spring.ImageStorageException.NotFoundException] if the key is absent.
 * - [delete] is idempotent — no exception is thrown when the key does not exist.
 * - [exists] throws [io.bluetape4k.images.spring.ImageStorageException.AccessDeniedException] on
 *   permission errors, not `false`.
 * - [list] returns a cold [Flow]; cancellation is propagated correctly.
 * - All implementations must rethrow [kotlinx.coroutines.CancellationException] before any broad catch.
 */
interface ImageStorage {

    /**
     * Uploads image bytes.
     *
     * ## Behavior
     * - Throws [io.bluetape4k.images.spring.ImageStorageException.ValidationException] if
     *   `bytes.size` exceeds the configured maximum or if [options] is invalid.
     */
    suspend fun upload(key: ImageObjectKey, bytes: ByteArray, options: UploadOptions): ImageUploadResult

    /**
     * Uploads image from a file path.
     *
     * ## Behavior
     * - Streams the file content; suitable for large images.
     * - Throws [io.bluetape4k.images.spring.ImageStorageException.ValidationException] if the file
     *   size exceeds the configured maximum.
     */
    suspend fun upload(key: ImageObjectKey, source: Path, options: UploadOptions): ImageUploadResult

    /**
     * Downloads image bytes.
     *
     * ## Behavior
     * - Throws [io.bluetape4k.images.spring.ImageStorageException.NotFoundException] if key is absent.
     * - Throws [io.bluetape4k.images.spring.ImageStorageException.ValidationException] if the object
     *   size exceeds the configured download limit.
     */
    suspend fun download(key: ImageObjectKey): ByteArray

    /**
     * Downloads image to a destination path.
     *
     * ## Behavior
     * - Streams the content; suitable for large images.
     * - Throws [io.bluetape4k.images.spring.ImageStorageException.NotFoundException] if key is absent.
     */
    suspend fun download(key: ImageObjectKey, destination: Path)

    /**
     * Deletes an image.
     *
     * ## Behavior
     * - No-op if key does not exist (idempotent).
     */
    suspend fun delete(key: ImageObjectKey)

    /**
     * Returns true if the image exists.
     *
     * ## Behavior
     * - Throws [io.bluetape4k.images.spring.ImageStorageException.AccessDeniedException] on
     *   permission errors rather than returning false.
     */
    suspend fun exists(key: ImageObjectKey): Boolean

    /**
     * Lists all image keys under [prefix].
     *
     * ## Behavior
     * - Returns a cold [Flow] — iteration starts when collected.
     * - Cancellation is propagated: collecting coroutine cancellation stops the listing.
     * - [prefix] is typed as [ImageObjectKey] to prevent path-traversal bypass via string injection.
     */
    fun list(prefix: ImageObjectKey): Flow<ImageObjectKey>
}
