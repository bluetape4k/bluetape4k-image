package io.bluetape4k.images.vips.java25.internal

import app.photofox.vipsffm.VipsHelper
import app.photofox.vipsffm.jextract.VipsRaw
import java.lang.foreign.Arena

/**
 * Adapter for native libvips codec capability inspection.
 */
internal interface FfmVipsCodecProbe {
    fun supportsOperation(name: String): Boolean
    fun libvipsVersion(): String? = null
}

/**
 * Default vips-ffm codec probe backed by `vips_type_find`.
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
