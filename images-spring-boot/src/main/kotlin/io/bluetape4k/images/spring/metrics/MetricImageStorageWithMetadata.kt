package io.bluetape4k.images.spring.metrics

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageObjectMetadata
import io.bluetape4k.images.spring.storage.ImageObjectMetadataReader
import io.bluetape4k.images.spring.storage.ImageStorage
import io.micrometer.core.instrument.MeterRegistry

/**
 * metadata capability를 보존하는 [MetricImageStorage] decorator입니다.
 *
 * metadata read에는 아직 별도 metric을 추가하지 않고 원본 capability에 그대로 위임합니다.
 * capability를 지원하지 않는 delegate에는 이 wrapper를 만들지 않아 unsupported backend를
 * 실수로 광고하지 않습니다.
 */
class MetricImageStorageWithMetadata(
    delegate: ImageStorage,
    registry: MeterRegistry,
    private val metadataDelegate: ImageObjectMetadataReader,
) : MetricImageStorage(delegate, registry), ImageObjectMetadataReader {

    override suspend fun readMetadata(key: ImageObjectKey): ImageObjectMetadata =
        metadataDelegate.readMetadata(key)
}
