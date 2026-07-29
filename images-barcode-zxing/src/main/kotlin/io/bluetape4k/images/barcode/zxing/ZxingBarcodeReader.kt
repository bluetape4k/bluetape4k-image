package io.bluetape4k.images.barcode.zxing

import com.google.zxing.BarcodeFormat as ZxingFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.barcode.BarcodeBoundingBox
import io.bluetape4k.images.barcode.BarcodeCoordinateSpace
import io.bluetape4k.images.barcode.BarcodeException
import io.bluetape4k.images.barcode.BarcodeFailureReason
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeOptions
import io.bluetape4k.images.barcode.BarcodePoint
import io.bluetape4k.images.barcode.BarcodeProviderIdentity
import io.bluetape4k.images.barcode.BarcodeReader
import io.bluetape4k.images.barcode.BarcodeRegion
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.immutableImageOf

/**
 * ZXing 기반 barcode reader입니다.
 *
 * ## 동작/계약
 * 이 provider는 ZXing dependency를 `images-barcode-zxing` 안에만 유지하고
 * provider-neutral barcode API model만 반환합니다. barcode가 없는 이미지는 빈 list를
 * 반환하고, 지원하지 않는 요청 format과 decode failure는 [BarcodeException]으로 정규화합니다.
 *
 * ```kotlin
 * val reader = ZxingBarcodeReader()
 * val results = image.extractBarcodes(reader, BarcodeOptions(formats = setOf(BarcodeFormat.QR_CODE)))
 * ```
 */
class ZxingBarcodeReader(
    private val provider: BarcodeProviderIdentity = zxingProviderIdentity(),
): BarcodeReader {

    override fun readBarcodes(image: ImmutableImage, options: BarcodeOptions): List<BarcodeResult> {
        val hints = options.toZxingHints()
        val bitmap = image.toBinaryBitmap()

        val result = try {
            MultiFormatReader()
                .apply { setHints(hints) }
                .decodeWithState(bitmap)
        } catch (e: NotFoundException) {
            return emptyList()
        } catch (e: ChecksumException) {
            throw decodeException(e)
        } catch (e: FormatException) {
            throw decodeException(e)
        } catch (e: ReaderException) {
            throw decodeException(e)
        } catch (e: RuntimeException) {
            throw decodeException(e)
        }

        return options.filter(listOf(result.toBarcodeResult(options)))
    }

    /**
     * 인코딩 이미지 [bytes]에서 barcode를 읽고 malformed input을 정규화합니다.
     */
    fun readBarcodes(
        bytes: ByteArray,
        options: BarcodeOptions = BarcodeOptions(),
    ): List<BarcodeResult> =
        try {
            readBarcodes(immutableImageOf(bytes), options)
        } catch (e: BarcodeException) {
            throw e
        } catch (e: Exception) {
            throw BarcodeException(
                reason = BarcodeFailureReason.MALFORMED_INPUT,
                message = "ZXing barcode input could not be decoded as an image.",
                cause = e,
            )
        }

    private fun Result.toBarcodeResult(options: BarcodeOptions): BarcodeResult {
        val payload = text.takeIf { it.isNotBlank() }
            ?: throw decodeException(IllegalStateException("ZXing returned a blank barcode payload."))

        return BarcodeResult(
            text = payload,
            format = barcodeFormat.toBarcodeFormat(),
            provider = provider,
            region = toBarcodeRegion(),
            rawBytes = rawBytes?.takeIf { options.includeRawBytes && it.isNotEmpty() },
            rawBackendFormat = barcodeFormat.name,
            metadata = resultMetadata.toStringMetadata(),
        )
    }
}

/**
 * ZXing barcode 추출에 사용할 기본 provider identity를 반환합니다.
 */
fun zxingProviderIdentity(): BarcodeProviderIdentity =
    BarcodeProviderIdentity(
        name = "ZXing",
        version = zxingVersion(),
        backend = "zxing-core",
        metadata = mapOf("decoder" to "MultiFormatReader"),
    )

private fun BarcodeOptions.toZxingHints(): Map<DecodeHintType, Any> {
    val requestedFormats = formats
        .mapNotNull { it.toZxingFormat() }
        .distinct()

    if (formats.isNotEmpty() && requestedFormats.isEmpty()) {
        throw BarcodeException(
            reason = BarcodeFailureReason.UNSUPPORTED_FORMAT,
            message = "ZXing does not support the requested barcode formats.",
        )
    }

    return buildMap {
        if (requestedFormats.isNotEmpty()) {
            put(DecodeHintType.POSSIBLE_FORMATS, requestedFormats)
        }
        if (tryHarder) {
            put(DecodeHintType.TRY_HARDER, true)
        }
    }
}

private fun ImmutableImage.toBinaryBitmap(): BinaryBitmap {
    val source = BufferedImageLuminanceSource(awt())
    return BinaryBitmap(HybridBinarizer(source))
}

private fun Result.toBarcodeRegion(): BarcodeRegion? {
    val points = resultPoints
        ?.mapNotNull { point ->
            val x = point.x.toDouble()
            val y = point.y.toDouble()
            if (x.isFinite() && y.isFinite() && x >= 0.0 && y >= 0.0) {
                BarcodePoint(x, y)
            } else {
                null
            }
        }
        ?.takeIf { it.isNotEmpty() }
        ?: return null

    val boundingBox = points.toBoundingBox()
    return BarcodeRegion(
        points = points,
        coordinateSpace = BarcodeCoordinateSpace.PIXEL,
        boundingBox = boundingBox,
    )
}

private fun List<BarcodePoint>.toBoundingBox(): BarcodeBoundingBox? {
    if (size < 2) {
        return null
    }

    val minX = minOf { it.x }
    val maxX = maxOf { it.x }
    val minY = minOf { it.y }
    val maxY = maxOf { it.y }
    val width = maxX - minX
    val height = maxY - minY

    return if (width > 0.0 && height > 0.0) {
        BarcodeBoundingBox(
            x = minX,
            y = minY,
            width = width,
            height = height,
            coordinateSpace = BarcodeCoordinateSpace.PIXEL,
        )
    } else {
        null
    }
}

private fun Map<*, *>?.toStringMetadata(): Map<String, String> =
    orEmpty()
        .mapNotNull { (key, value) ->
            val metadataKey = key?.toString()?.takeIf { it.isNotBlank() }
            val metadataValue = value?.toString()?.takeIf { it.isNotBlank() }
            if (metadataKey != null && metadataValue != null) {
                metadataKey to metadataValue
            } else {
                null
            }
        }
        .toMap()

private fun BarcodeFormat.toZxingFormat(): ZxingFormat? =
    when (this) {
        BarcodeFormat.QR_CODE -> ZxingFormat.QR_CODE
        BarcodeFormat.CODE_128 -> ZxingFormat.CODE_128
        BarcodeFormat.CODE_39 -> ZxingFormat.CODE_39
        BarcodeFormat.EAN_13 -> ZxingFormat.EAN_13
        BarcodeFormat.EAN_8 -> ZxingFormat.EAN_8
        BarcodeFormat.UPC_A -> ZxingFormat.UPC_A
        BarcodeFormat.UPC_E -> ZxingFormat.UPC_E
        BarcodeFormat.DATA_MATRIX -> ZxingFormat.DATA_MATRIX
        BarcodeFormat.AZTEC -> ZxingFormat.AZTEC
        BarcodeFormat.PDF_417 -> ZxingFormat.PDF_417
        BarcodeFormat.CODABAR -> ZxingFormat.CODABAR
        BarcodeFormat.ITF -> ZxingFormat.ITF
        BarcodeFormat.UNKNOWN -> null
    }

private fun ZxingFormat.toBarcodeFormat(): BarcodeFormat =
    when (this) {
        ZxingFormat.QR_CODE -> BarcodeFormat.QR_CODE
        ZxingFormat.CODE_128 -> BarcodeFormat.CODE_128
        ZxingFormat.CODE_39 -> BarcodeFormat.CODE_39
        ZxingFormat.EAN_13 -> BarcodeFormat.EAN_13
        ZxingFormat.EAN_8 -> BarcodeFormat.EAN_8
        ZxingFormat.UPC_A -> BarcodeFormat.UPC_A
        ZxingFormat.UPC_E -> BarcodeFormat.UPC_E
        ZxingFormat.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        ZxingFormat.AZTEC -> BarcodeFormat.AZTEC
        ZxingFormat.PDF_417 -> BarcodeFormat.PDF_417
        ZxingFormat.CODABAR -> BarcodeFormat.CODABAR
        ZxingFormat.ITF -> BarcodeFormat.ITF
        else -> BarcodeFormat.UNKNOWN
    }

private fun zxingVersion(): String =
    MultiFormatReader::class.java.`package`?.implementationVersion
        ?.takeIf { it.isNotBlank() }
        ?: "3.5.4"

private fun decodeException(cause: Throwable): BarcodeException =
    BarcodeException(
        reason = BarcodeFailureReason.DECODE_FAILED,
        message = "ZXing barcode decoding failed.",
        cause = cause,
    )
