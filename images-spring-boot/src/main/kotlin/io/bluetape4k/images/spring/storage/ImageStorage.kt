package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageUploadResult
import io.bluetape4k.images.spring.UploadOptions
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

/**
 * image object storage backend에 대한 abstraction입니다.
 *
 * ## 동작
 * - 모든 operation은 suspend이며 coroutine context에서 호출해도 안전합니다.
 * - upload operation은 atomic해야 합니다. partial write가 reader에게 보여서는 안 됩니다.
 *   구현체는 동일한 디렉터리의 임시 파일에 기록하고 flush한 뒤 최종 object를 교체해야 합니다.
 * - key가 없으면 [download]는 [io.bluetape4k.images.spring.ImageStorageException.NotFoundException]을 던집니다.
 * - [delete]는 idempotent입니다. key가 없어도 예외를 던지지 않습니다.
 * - permission error에서 [exists]는 `false`가 아니라
 *   [io.bluetape4k.images.spring.ImageStorageException.AccessDeniedException]을 던집니다.
 * - [list]는 cold [Flow]를 반환하며 cancellation을 올바르게 전파합니다.
 * - 모든 구현체는 broad catch보다 먼저 [kotlinx.coroutines.CancellationException]을 다시 던져야 합니다.
 */
interface ImageStorage {

    /**
     * image byte를 upload합니다.
     *
     * ## 동작
     * - `bytes.size`가 configured maximum을 초과하거나 [options]가 invalid이면
     *   [io.bluetape4k.images.spring.ImageStorageException.ValidationException]을 던집니다.
     */
    suspend fun upload(key: ImageObjectKey, bytes: ByteArray, options: UploadOptions): ImageUploadResult

    /**
     * file path에서 image를 upload합니다.
     *
     * ## 동작
     * - file content를 stream하므로 large image에 적합합니다. 구현체는 전체 파일을
     *   `ByteArray`로 적재해서는 안 됩니다.
     * - file size가 configured maximum을 초과하면
     *   [io.bluetape4k.images.spring.ImageStorageException.ValidationException]을 던집니다.
     */
    suspend fun upload(key: ImageObjectKey, source: Path, options: UploadOptions): ImageUploadResult

    /**
     * image byte를 download합니다.
     *
     * ## 동작
     * - key가 없으면 [io.bluetape4k.images.spring.ImageStorageException.NotFoundException]을 던집니다.
     * - object size가 configured download limit을 초과하면
     *   [io.bluetape4k.images.spring.ImageStorageException.ValidationException]을 던집니다.
     */
    suspend fun download(key: ImageObjectKey): ByteArray

    /**
     * image를 destination path로 download합니다.
     *
     * ## 동작
     * - content를 stream하므로 large image에 적합합니다. 구현체는 destination을
     *   partial file로 노출하지 않아야 합니다.
     * - key가 없으면 [io.bluetape4k.images.spring.ImageStorageException.NotFoundException]을 던집니다.
     */
    suspend fun download(key: ImageObjectKey, destination: Path)

    /**
     * image를 삭제합니다.
     *
     * ## 동작
     * - key가 없으면 no-op입니다(idempotent).
     */
    suspend fun delete(key: ImageObjectKey)

    /**
     * image가 존재하면 `true`를 반환합니다.
     *
     * ## 동작
     * - permission error에서는 `false`를 반환하지 않고
     *   [io.bluetape4k.images.spring.ImageStorageException.AccessDeniedException]을 던집니다.
     */
    suspend fun exists(key: ImageObjectKey): Boolean

    /**
     * [prefix] 아래의 모든 image key를 나열합니다.
     *
     * ## 동작
     * - cold [Flow]를 반환합니다. collection 시점에 iteration이 시작됩니다.
     * - cancellation을 전파합니다. collecting coroutine이 취소되면 listing을 중단합니다.
     * - string injection을 통한 path-traversal 우회를 막기 위해 [prefix]는 [ImageObjectKey] type입니다.
     */
    fun list(prefix: ImageObjectKey): Flow<ImageObjectKey>
}
