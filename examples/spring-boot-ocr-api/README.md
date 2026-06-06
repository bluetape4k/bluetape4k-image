# Spring Boot OCR API Quickstart

English | [한국어](./README.ko.md)

Compact Spring Boot 4 example for extracting OCR text from multipart image
uploads with `bluetape4k-images-ocr`.

## What It Shows

- Multipart upload endpoint at `POST /api/ocr`
- Tesseract language parsing from `languages=eng`, `eng+kor`, or `eng,kor`
- `ImmutableImage.suspendExtractText` wiring through an injectable `OcrEngine`
- Optional `example.ocr.tessdata-path` configuration for host traineddata
- Error mapping for request validation and unavailable native OCR runtime
- Controller tests with a fake `OcrEngine`, so normal CI does not require
  Tesseract

This is the small repo-owned quickstart. Production concerns such as
authentication, rate limiting, request queues, file persistence, and batch OCR
belong in a larger application or follow-up issue.

## Diagrams

### Example Scenario

![Spring Boot OCR API Scenario](../../docs/images/readme-diagrams/examples-spring-boot-ocr-api-scenario-01.png)

### Architecture

![Spring Boot OCR API Architecture](../../docs/images/readme-diagrams/examples-spring-boot-ocr-api-architecture-01.png)

### Sequence

![Spring Boot OCR API Sequence](../../docs/images/readme-diagrams/examples-spring-boot-ocr-api-sequence-01.png)

## Native OCR Requirements

The example uses Tess4J through `bluetape4k-images-ocr`. Real OCR runs require
host Tesseract plus the traineddata packages for the requested language codes.

```bash
# macOS
brew install tesseract tesseract-lang

# Ubuntu / Debian
sudo apt-get install tesseract-ocr tesseract-ocr-eng tesseract-ocr-kor tesseract-ocr-jpn fonts-noto-cjk

tesseract --list-langs
```

If Tesseract cannot find traineddata, either set `TESSDATA_PREFIX` in the shell
that starts the application or configure:

```yaml
example:
  ocr:
    tessdata-path: /opt/homebrew/share/tessdata
```

The endpoint intentionally does not accept a request-level tessdata path.

## Run

```bash
./gradlew :spring-boot-ocr-api:bootRun
```

Extract text from an uploaded image:

```bash
curl -F "file=@images/src/test/resources/images/cafe.jpg;type=image/jpeg" \
  "http://localhost:8080/api/ocr?languages=eng"
```

Example response:

```json
{
  "text": "recognized text",
  "languages": ["eng"],
  "characterCount": 15
}
```

For multiple installed language packs:

```bash
curl -F "file=@sample-ko.png;type=image/png" \
  "http://localhost:8080/api/ocr?languages=eng+kor"
```

## Test

```bash
./gradlew :spring-boot-ocr-api:test
```

The tests use MockMvc and a fake `OcrEngine`. They verify multipart OCR
success, language parsing, unsupported content type rejection, and native OCR
failure mapping without requiring host Tesseract.
