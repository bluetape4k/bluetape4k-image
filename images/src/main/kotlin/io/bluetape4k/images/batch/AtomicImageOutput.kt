package io.bluetape4k.images.batch

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * 대상 파일과 같은 디렉터리에 임시 파일을 만든 뒤 닫고 원자적으로 교체합니다.
 *
 * writer가 실패하거나 코루틴이 취소되면 임시 파일만 제거하고 기존 대상 파일은
 * 보존합니다. 원자적 교체를 지원하지 않는 파일 시스템에서는 대상 파일을 직접
 * 덮어쓰는 fallback을 사용하지 않고 실패합니다.
 */
internal suspend fun writeAtomically(
    output: Path,
    ioDispatcher: CoroutineContext,
    writer: (OutputStream) -> Unit,
    deleteTemporary: (Path) -> Unit = { path -> Files.deleteIfExists(path) },
): Long {
    val cleanupFailure = AtomicReference<Throwable?>()
    try {
        val result = withContext(ioDispatcher) {
            val parent = output.parent ?: Path.of(".").toAbsolutePath().normalize()
            val fileName = output.fileName?.toString()?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("출력 파일명을 확인할 수 없습니다: $output")
            Files.createDirectories(parent)
            val temporary = Files.createTempFile(parent, ".$fileName.", ".tmp")

            try {
                Files.newOutputStream(temporary).use(writer)
                currentCoroutineContext().ensureActive()
                Files.move(temporary, output, ATOMIC_MOVE, REPLACE_EXISTING)
                Files.size(output)
            } finally {
                try {
                    deleteTemporary(temporary)
                } catch (error: Throwable) {
                    cleanupFailure.set(error)
                }
            }
        }

        cleanupFailure.get()?.let { throw it }
        return result
    } catch (error: Throwable) {
        cleanupFailure.get()?.takeUnless { it === error }?.let(error::addSuppressed)
        throw error
    }
}
