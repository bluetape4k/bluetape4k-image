package io.bluetape4k.images.vips.java25.internal

import app.photofox.vipsffm.VipsHelper
import app.photofox.vipsffm.jextract.VipsRaw
import java.lang.foreign.Arena

/**
 * native libvips codec capability inspection용 adapter입니다.
 */
internal interface FfmVipsCodecProbe {
    fun supportsOperation(name: String): Boolean
    fun libvipsVersion(): String? = null
}

/**
 * `vips_type_find`를 기반으로 하는 기본 vips-ffm codec probe입니다.
 */
internal object DefaultFfmVipsCodecProbe : FfmVipsCodecProbe {
    override fun libvipsVersion(): String? =
        runCatching { VipsHelper.version_string().trim() }
            .getOrNull()
            ?.takeIf(String::isNotEmpty)

    override fun supportsOperation(name: String): Boolean =
        runCatching {
            Arena.ofConfined().use { arena ->
                VipsRaw.vips_type_find(
                    arena.allocateFrom("VipsOperation"),
                    arena.allocateFrom(name),
                ) != 0L
            }
        }.getOrDefault(false)
}
