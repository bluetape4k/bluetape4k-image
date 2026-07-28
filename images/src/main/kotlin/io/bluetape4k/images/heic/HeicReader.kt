package io.bluetape4k.images.heic

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.IncubatingImageApi
import java.io.InputStream

/**
 * HEIC/HEIF 읽기 옵션입니다.
 *
 * ## 동작/계약
 * - `pageIndex`는 다중 프레임 HEIC에서 읽을 페이지 인덱스입니다 (0-based).
 * - `applyOrientation`이 `true`이면 EXIF 회전 정보를 자동 적용합니다.
 *
 * ```kotlin
 * val opts = HeicReadOptions(pageIndex = 0, applyOrientation = true)
 * ```
 *
 * @property pageIndex        읽을 페이지 인덱스 (기본값: 0)
 * @property applyOrientation EXIF 회전 자동 적용 여부 (기본값: `true`)
 */
data class HeicReadOptions(
    val pageIndex: Int = 0,
    val applyOrientation: Boolean = true,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative: $pageIndex" }
    }

    companion object {
        @JvmStatic
        val Default = HeicReadOptions()
    }
}

/**
 * HEIC/HEIF 형식의 이미지를 읽습니다.
 *
 * ## 동작/계약
 * - 이 interface는 incubating API입니다([IncubatingImageApi]).
 * - core `images` module은 디코딩 계약만 선언합니다.
 * - 호환되는 backend가 runtime HEIC 지원을 제공합니다.
 * - [input]을 닫는 책임은 호출자에게 있습니다.
 *
 * @see HeicReadOptions
 * @see IncubatingImageApi
 */
@IncubatingImageApi
interface HeicReader {

    /**
     * HEIC/HEIF [input] stream에서 이미지를 읽습니다.
     *
     * @param input   HEIC/HEIF 데이터를 담은 [InputStream]
     * @param options HEIC 읽기 옵션 (기본값: [HeicReadOptions.Default])
     * @return 읽은 [ImmutableImage]
     * @throws java.io.IOException HEIC 파싱 실패 시
     */
    suspend fun suspendRead(
        input: InputStream,
        options: HeicReadOptions = HeicReadOptions.Default,
    ): ImmutableImage
}
