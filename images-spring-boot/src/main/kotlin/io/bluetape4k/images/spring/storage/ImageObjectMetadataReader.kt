package io.bluetape4k.images.spring.storage

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageObjectMetadata

/**
 * image storage가 제공하는 선택적 object metadata capability입니다.
 *
 * [ImageStorage] 자체에는 이 method를 추가하지 않아 외부 구현체와 decorator의
 * source/ABI를 깨뜨리지 않습니다. 소비자는 capability를 탐색한 뒤 지원하지 않는
 * backend를 명시적으로 처리해야 합니다.
 */
interface ImageObjectMetadataReader {

    /**
     * body를 열지 않고 [key]의 metadata snapshot을 반환합니다.
     */
    suspend fun readMetadata(key: ImageObjectKey): ImageObjectMetadata
}
