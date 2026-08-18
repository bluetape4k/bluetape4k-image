package io.bluetape4k.images.vips.java21.internal

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JVips가 JAR에 포함한 Linux용 libtiff를 libvips보다 먼저 로드합니다.
 *
 * JVips 8.12.2는 `libvips.so`와 함께 `libtiff.so`를 배포하지만 자체 로더 목록에는
 * libtiff를 포함하지 않습니다. Ubuntu 24.04처럼 시스템에 `libtiff.so.6`만 있는
 * 환경에서는 libvips의 `libtiff.so.5` 의존성을 충족하지 못하므로, JAR의 SONAME을
 * 가진 라이브러리를 먼저 로드해 시스템 패키지 버전에 대한 의존성을 제거합니다.
 */
internal object JVipsNativeLibrarySupport {

    private const val LINUX_NAME = "Linux"
    private const val LIBTIFF_RESOURCE = "libtiff.so"

    private val libTiffLoaded = AtomicBoolean(false)

    fun loadBundledLibTiffIfNeeded() {
        if (!isLinux() || libTiffLoaded.get()) return

        synchronized(this) {
            if (libTiffLoaded.get()) return

            val resource = findResource() ?: return
            val temporaryLibrary = Files.createTempFile("jvips-libtiff-", ".so")
            temporaryLibrary.toFile().deleteOnExit()

            resource.use { input ->
                Files.copy(input, temporaryLibrary, StandardCopyOption.REPLACE_EXISTING)
            }
            System.load(temporaryLibrary.toAbsolutePath().toString())
            libTiffLoaded.set(true)
        }
    }

    private fun isLinux(): Boolean =
        System.getProperty("os.name").equals(LINUX_NAME, ignoreCase = true)

    private fun findResource() =
        Thread.currentThread().contextClassLoader?.getResourceAsStream(LIBTIFF_RESOURCE)
            ?: JVipsNativeLibrarySupport::class.java.classLoader?.getResourceAsStream(LIBTIFF_RESOURCE)
}
