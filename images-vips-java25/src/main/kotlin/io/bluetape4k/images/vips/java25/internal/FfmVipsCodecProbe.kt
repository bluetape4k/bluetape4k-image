package io.bluetape4k.images.vips.java25.internal

import app.photofox.vipsffm.VipsHelper
import app.photofox.vipsffm.jextract.VipsRaw
import java.lang.foreign.Arena

/**
 * native libvips codec capability inspection용 adapter입니다.
 */
internal sealed interface FfmVipsCodecProbeResult {
    /** native operation을 확인했고 사용할 수 있습니다. */
    data object Available : FfmVipsCodecProbeResult

    /** native operation을 확인했지만 libvips에 등록되어 있지 않습니다. */
    data object Unavailable : FfmVipsCodecProbeResult

    /** native operation 탐색 자체가 실패해 support 상태를 판단할 수 없습니다. */
    data class Failed(val diagnostic: String) : FfmVipsCodecProbeResult

    companion object {
        const val SAFE_FAILURE_REASON =
            "Codec operation probe failed; verify libvips runtime and native linkage."
    }
}

internal interface FfmVipsCodecProbe {
    fun inspectOperation(name: String): FfmVipsCodecProbeResult

    /** operation 실행 경로는 probe 실패를 안전하게 fail-closed 처리합니다. */
    fun supportsOperation(name: String): Boolean =
        inspectOperation(name) is FfmVipsCodecProbeResult.Available

    fun libvipsVersion(): String? = null
}

/**
 * `vips_type_find`를 기반으로 하는 기본 vips-ffm codec probe입니다.
 */
internal object DefaultFfmVipsCodecProbe : FfmVipsCodecProbe {
    override fun libvipsVersion(): String? = try {
        VipsHelper.version_string().trim().takeIf(String::isNotEmpty)
    } catch (_: Exception) {
        null
    }

    override fun inspectOperation(name: String): FfmVipsCodecProbeResult = try {
        val available =
            Arena.ofConfined().use { arena ->
                VipsRaw.vips_type_find(
                    arena.allocateFrom("VipsOperation"),
                    arena.allocateFrom(name),
                ) != 0L
            }
        if (available) {
            FfmVipsCodecProbeResult.Available
        } else {
            FfmVipsCodecProbeResult.Unavailable
        }
    } catch (_: Exception) {
        FfmVipsCodecProbeResult.Failed(FfmVipsCodecProbeResult.SAFE_FAILURE_REASON)
    }
}
