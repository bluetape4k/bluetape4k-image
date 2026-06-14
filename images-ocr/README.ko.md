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

![images-ocr Architecture](../docs/images/readme-diagrams/images-ocr-architecture-01.png)

## Class Diagram

![images-ocr Class Diagram](../docs/images/readme-diagrams/images-ocr-class-diagram-01.png)

## Sequence Diagram

![images-ocr Recognition Sequence](../docs/images/readme-diagrams/images-ocr-sequence-diagram-01.png)

## 실행 요구사항

요청할 언어에 맞는 Tesseract와 traineddata 패키지를 설치하세요.

```bash
# macOS / Homebrew
brew install tesseract tesseract-lang

# Ubuntu / Debian
sudo apt-get install tesseract-ocr tesseract-ocr-eng tesseract-ocr-kor tesseract-ocr-jpn fonts-noto-cjk

tesseract --list-langs
```

런타임이 traineddata 파일을 찾지 못하면 `TESSDATA_PREFIX`를 설정하거나
`OcrOptions(tessdataPath = "...")`를 전달하세요.

Host-native OCR은 Tess4J/Lept4J를 JNA로 사용하므로 JVM이 호스트의 Leptonica와
Tesseract shared library를 로드합니다. 현재 런타임은 Homebrew Tesseract 5.5 /
Leptonica 1.87 조합에서 로컬 검증했습니다. Ubuntu 24.04 패키지는 Leptonica 1.82를
제공하며, 현재 Lept4J/Tess4J 라인이 기대하는 native symbol set을 만족하지 못합니다.
따라서 GitHub CI와 Nightly는 portable container-backed OCR gate를 실행하고,
`-Docr.enabled=true`는 local/manual host-native check로 둡니다.

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

## 실행 가능한 Quickstart

이 모듈에는 파일 기반 quickstart 테스트가 포함됩니다. 테스트는 작은 이미지를
생성하고, `immutableImageOf(File)`로 읽은 뒤, 명시적인 언어와 page segmentation
옵션으로 OCR을 실행합니다. Tess4J가 traineddata 파일을 자동으로 찾지 못하면
`TESSDATA_PREFIX`를 설정하세요.

```bash
export TESSDATA_PREFIX="$(brew --prefix)/share/tessdata"  # macOS / Homebrew
./gradlew :bluetape4k-images-ocr:test \
  --tests 'io.bluetape4k.images.ocr.OcrQuickstartExampleTest' \
  -Docr.enabled=true
```

예상 출력 형태:

```text
BLUETAPE OCR 123
```

Quickstart는 `eng`를 사용합니다. macOS에서는 `brew install tesseract
tesseract-lang`으로 Tesseract 엔진과 bundled language pack set을 함께 설치합니다.
Ubuntu에서는 `tesseract-ocr-eng`, `tesseract-ocr-kor`, `tesseract-ocr-jpn`처럼
요청할 traineddata 패키지를 명시적으로 설치하세요. 테스트는 `TESSDATA_PREFIX`가
없을 때도 일반적인 Homebrew와 Ubuntu tessdata 경로를 확인합니다.

## 테스트

항상 실행되는 테스트는 options, enum mapping, serialization, engine delegation,
호출별 Tess4J 설정, sanitized exception, coroutine cancellation을 검증합니다.

```bash
./gradlew :bluetape4k-images-ocr:test
```

Native OCR 테스트는 호스트 Tesseract, 언어팩, 그리고 classpath의 Tess4J/Lept4J
버전과 호환되는 Leptonica 런타임이 필요하므로 gate로 분리합니다.

```bash
./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true
```

Container smoke 테스트는 Docker가 필요하므로 gate로 분리합니다. GitHub CI와
Nightly가 사용하는 OCR gate입니다.

```bash
./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true
```
