package io.bluetape4k.images.batch

import io.bluetape4k.io.writeAtomically
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.file.Path
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
): Long = withContext(ioDispatcher) {
    val coroutineContext = currentCoroutineContext()
    output.writeAtomically { stream ->
        writer(stream)
        coroutineContext.ensureActive()
    }
}
