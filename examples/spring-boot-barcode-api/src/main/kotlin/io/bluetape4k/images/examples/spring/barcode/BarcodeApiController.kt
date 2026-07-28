package io.bluetape4k.images.examples.spring.barcode

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * multipart extraction endpoint와 deterministic fixture scenario를 노출합니다.
 *
 * fixture-backed `GET` route는 response contract를 보여주는 용도이며 production data API가 아닙니다.
 */
@RestController
@RequestMapping("/api/barcodes")
internal class BarcodeApiController(
    private val extractionService: BarcodeExtractionService,
    private val fixtures: BarcodeExampleFixtures,
) {

    @PostMapping("/extract", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun extract(@RequestParam("file") file: MultipartFile): BarcodeExtractionResponse =
        extractionService.extract(file)

    @GetMapping("/sample")
    suspend fun sample(): BarcodeExtractionResponse =
        extractionService.extract(fixtures.bytes(BarcodeExampleFixture.SAMPLE))

    @GetMapping("/no-result")
    suspend fun noResult(): BarcodeExtractionResponse =
        extractionService.extract(fixtures.bytes(BarcodeExampleFixture.NO_RESULT))

    @GetMapping("/malformed")
    suspend fun malformed(): BarcodeExtractionResponse =
        extractionService.extract(fixtures.bytes(BarcodeExampleFixture.MALFORMED))
}
