# bluetape4k-images-ocr

[English](./README.md) | 한국어

`ImmutableImage`용 Tess4J/Tesseract OCR 확장 모듈입니다.

## 기능

- Blocking OCR용 `ImmutableImage.extractText(...)`
- 코루틴 호출자를 위한 `ImmutableImage.suspendExtractText(...)`; blocking OCR은 기본적으로
  `Dispatchers.IO`에서 실행합니다.
- 언어, `tessdataPath`, engine mode, page segmentation mode, variables, configs를
  설정하는 `OcrOptions`
- `TesseractOcrEngine`은 호출마다 새 Tess4J 인스턴스를 만들어 mutable OCR 상태를
  호출자끼리 공유하지 않습니다.

## Architecture

![images-ocr Architecture](docs/assets/readme-diagrams/images-ocr-architecture-01.png)

## Class Diagram

![images-ocr Class Diagram](docs/assets/readme-diagrams/images-ocr-class-diagram-01.png)

## Sequence Diagram

![images-ocr Recognition Sequence](docs/assets/readme-diagrams/images-ocr-sequence-diagram-01.png)

## 실행 요구사항

요청할 언어에 맞는 Tesseract와 traineddata 패키지를 설치하세요.

```bash
# macOS
brew install tesseract tesseract-lang

# Ubuntu / Debian
sudo apt-get install tesseract-ocr tesseract-ocr-eng tesseract-ocr-kor tesseract-ocr-jpn fonts-noto-cjk

tesseract --list-langs
```

런타임이 traineddata 파일을 찾지 못하면 `TESSDATA_PREFIX`를 설정하거나
`OcrOptions(tessdataPath = "...")`를 전달하세요.

## 의존성

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-ocr:<version>")
}
```

## 사용 예시

```kotlin
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.TesseractPageSegmentationMode
import io.bluetape4k.images.ocr.extractText
import io.bluetape4k.images.ocr.suspendExtractText
import java.io.File

val image = immutableImageOf(File("receipt.png"))

val text = image.extractText(
    OcrOptions(languages = listOf("eng", "kor")),
)

val suspendText = image.suspendExtractText(
    OcrOptions(
        languages = listOf("eng"),
        pageSegmentationMode = TesseractPageSegmentationMode.SINGLE_LINE,
    ),
)
```

Tesseract 튜닝이 필요하면 `variables`를 사용하세요.

```kotlin
val digits = image.extractText(
    OcrOptions(
        languages = listOf("eng"),
        variables = mapOf("tessedit_char_whitelist" to "0123456789"),
    ),
)
```

## 테스트

항상 실행되는 테스트는 options, enum mapping, serialization, engine delegation,
호출별 Tess4J 설정, sanitized exception, coroutine cancellation을 검증합니다.

```bash
./gradlew :bluetape4k-images-ocr:test
```

Native OCR 테스트는 호스트 Tesseract와 언어팩이 필요하므로 gate로 분리합니다.

```bash
./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true
```

Container smoke 테스트는 Docker가 필요하므로 gate로 분리합니다.

```bash
./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true
```
