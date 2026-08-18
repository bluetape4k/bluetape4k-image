package io.bluetape4k.images.vips.java25.internal

import app.photofox.vipsffm.Vips

/**
 * vips-ffm 네이티브 런타임 어댑터 인터페이스.
 *
 * 테스트에서 실제 FFM 호출을 대체할 수 있는 seam 역할을 합니다.
 */
internal interface FfmVipsNativeRuntime {
    fun nativeInit(concurrency: Int)
    fun nativeShutdown()
}

/**
 * vips-ffm FFM 바인딩을 직접 호출하는 기본 구현체.
 *
 * **보안**: vips-ffm 1.9.6은 기본적으로 untrusted 로더(SVG, PDF, EPS 등)를 차단합니다.
 * `Vips.allowUntrustedOperations()`를 호출하지 않는 한 안전합니다.
 *
 * **concurrency**: vips-ffm 1.9.6은 concurrency 설정 API를 노출하지 않습니다.
 * 호출자는 backend default를 사용해야 하며, `FfmVipsRuntime`이 non-default 요청을
 * native 초기화 전에 명시적으로 거부합니다.
 */
internal object DefaultFfmVipsNativeRuntime : FfmVipsNativeRuntime {
    override fun nativeInit(concurrency: Int) {
        Vips.init()
    }

    override fun nativeShutdown() {
        Vips.shutdown()
    }
}
