---
manualId: "ocr-integration"
title: "OCR Integration"
locale: "en"
releaseRef: "0.3.0"
---

# OCR Integration

<code>bluetape4k-images-ocr</code> adds Tesseract text extraction to the core <code>ImmutableImage</code> model. It is a separate module because OCR brings native packages, language data, latency, and operational failure modes that normal image processing does not need.

## API model

<code>OcrOptions</code> selects language and engine behavior. <code>OcrResult</code> carries recognized text and result data. <code>TesseractOcrEngine</code> implements the engine, and <code>ImmutableImage.extractText</code> is the convenient entry point for an existing image value.

Configure language and data path explicitly in controlled deployments. Normalize or resize input only when it improves the document class being processed; aggressive filters can remove characters as easily as noise.

## Service design

Bound upload bytes, decoded dimensions, OCR concurrency, and request time. OCR is a good candidate for a worker queue when documents are large or latency is unpredictable. Apply the same data-protection and retention policy to original media and extracted text; recognized text can be more searchable than the image.

Distinguish configuration failure, engine failure, empty recognized text, and request validation. Do not retry malformed or unsupported input indefinitely.

## Learn with runnable paths

Complete [OCR setup](../guides/ocr-setup.md) first. Then run the [Ktor OCR](../modules/ktor-ocr-api.md) or [Spring Boot OCR](../modules/spring-boot-ocr-api.md) workshop and read its tests with the application code.

## Sources

- [OCR contracts](https://github.com/bluetape4k/bluetape4k-image/tree/a571c30004f571fe8cfcddc29670c1404d212ec6/images-ocr/src/main/kotlin/io/bluetape4k/images/ocr)
- [OCR module reference](../modules/bluetape4k-images-ocr.md)
