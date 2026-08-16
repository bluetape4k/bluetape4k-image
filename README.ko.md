# bluetape4k-image

[![CI](https://github.com/bluetape4k/bluetape4k-image/actions/workflows/ci.yml/badge.svg)](https://github.com/bluetape4k/bluetape4k-image/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-25-ED8B00?logo=openjdk)](https://openjdk.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[English](./README.md) | 한국어

![bluetape4k 이미지 처리 작업대 일러스트](./docs/images/image-workbench.png)

Kotlin/JVM 이미지 처리 라이브러리 — [bluetape4k](https://github.com/bluetape4k) 생태계의 일부입니다.
두 가지 백엔드를 제공합니다: 코루틴 비동기 I/O를 갖춘 순수 JVM [scrimage](https://github.com/sksamuel/scrimage)
경로(Java2D)와, JVips JNI 백엔드(JDK 25, legacy `java21` artifact name) 및 Panama 외부 함수 &
메모리 API(JDK 25)를 통해 제공되는 고성능 [libvips](https://www.libvips.org/) 경로입니다.

## 개요

`bluetape4k-image`는 Kotlin 서비스가 순수 JVM scrimage 처리로 시작하고, 처리량·메모리·
native codec이 중요해질 때 libvips 백엔드로 확장할 수 있는 단일 이미지 처리 표면을 제공합니다.

저장소는 여러 도입 경로를 중심으로 구성됩니다.

- **순수 JVM 우선** — native runtime 설정 없이 안정적인 resize, crop, filter, analysis,
  batch, encode workflow가 필요하면 `images`를 사용합니다.
- **서비스 어댑터** — CAPTCHA challenge, Ktor route, Spring Boot 4 storage/health/metrics
  wiring으로 이미지 처리를 노출해야 할 때 `images-captcha`, `images-ktor`,
  `images-spring-boot`를 추가합니다.
- **OCR 추출** — 기존 `ImmutableImage` 값에서 Tesseract 기반 텍스트 추출이 필요하면
  명시적인 언어와 tessdata 설정을 가진 `images-ocr`를 추가합니다.
- **Barcode 추출** — provider-neutral barcode/QR 결과 contract가 필요하면
  `images-barcode-api`를 추가하고, 순수 JVM ZXing 경로가 필요하면
  `images-barcode-zxing`을 함께 추가합니다.
- **Detector boundary** — face, object, sensitive-region adapter가 OpenCV, ONNX
  Runtime, TensorFlow Lite, MediaPipe, 외부 서비스 중 하나를 선택하기 전에 안정적인
  결과 모델이 필요하면 `images`의 runtime-free detector contract를 사용합니다.
- **Native acceleration** — libvips 처리량, 메모리 동작, AVIF/HEIC 가능 native codec 지원이
  필요하면 `images-vips-api`에 맞춰 작성하고 JDK 25 JVips JNI(legacy `java21` artifact) 또는
  Java 25 FFM backend를 선택합니다.

BOM은 artifact version을 정렬하고, runnable example은 local API 형태를 보여 주며,
benchmark module은 scrimage/libvips trade-off를 추측이 아니라 측정 가능한 증거로 남깁니다.

## 매뉴얼

[Image 0.4 매뉴얼](./docs/manual/ko/index.md)은 학습 경로, 모듈별 계약, 백엔드 선택,
네이티브 자원 수명, OCR·웹 연동, 실행 가능한 예제, 벤치마크 해석을 자세히 설명하는
기준 문서입니다. 애플리케이션에서는 `bluetape4k-dependencies` 버전 하나만 선택하면 되며,
개별 Image 라이브러리 버전은 중앙 BOM이 맞춰 줍니다.

README는 현재 저장소의 모습을 요약합니다. 버전별 매뉴얼은 이와 달리 정확한 `0.4.0`
배포본을 다루며, 각 설명에서 해당 배포 소스로 이동할 수 있습니다.

## 제공 기능

- **순수 JVM 처리** — scrimage/Java2D 기반 로드, 리사이즈, 크롭, 필터, 분석, 배치, 인코딩
- **Coroutine I/O** — 웹 이미지 워크플로우에 맞는 suspend reader/writer/byte encoder
- **CAPTCHA 생성** — native runtime 없이 Java2D로 bounded option 기반 이미지 챌린지 생성
- **OCR 추출** — 다국어 옵션을 지원하는 Tess4J/Tesseract 기반
  `ImmutableImage.extractText`, `suspendExtractText` helper
- **Barcode contract** — provider-neutral barcode/QR 모델과
  `ImmutableImage.extractBarcodes`, `suspendExtractBarcodes` 진입점 제공
- **ZXing provider** — 호출자에게 ZXing 타입을 노출하지 않고 공통 barcode API를
  통해 QR과 주요 1D barcode를 순수 JVM으로 디코딩
- **Detector contract** — model download나 native ML dependency 없이 backend-neutral
  face/object/sensitive-region 결과 모델, detector identity metadata, confidence filtering,
  `ImmutableImage` sync/suspend 진입점 제공
- **Ktor 통합** — Ktor 서비스에서 CAPTCHA 이미지 발급과 one-shot 답변 검증을 처리하는 route helper
- **libvips 추상화** — binding-neutral `VipsImage`, `VipsRuntime` 계약
- **두 native backend** — JDK 25 JVips/JNI(legacy `java21` artifact)와 Java 25 FFM/Panama 선택지
- **Benchmark lane** — scrimage와 libvips resize/encode 경로를 비교하는
  `kotlinx-benchmark` 벤치마크

## 대용량 파일과 Okio I/O

업로드 본문, object storage client, pipe, asynchronous file channel처럼 이미지
바이트가 이미 streaming 경계를 지나고 있다면 `bluetape4k-okio`를 사용하세요.
scrimage 기반 `images` 모듈은 Okio `Source`/`Sink`와
`SuspendedSource`/`SuspendedSink` helper를 받아 lifecycle-safe load/write
통합을 제공합니다.

libvips 경로에서 caller가 local file path를 이미 소유한다면 `Path` 진입점을
사용하세요. 이는 짧은 benchmark snapshot의 처리량이나 메모리 순위가 아니라
API와 lifecycle 선택입니다. 호출자가 이미 non-file stream이나
`bluetape4k-okio` suspended boundary를 소유하고 있을 때 vips Okio
`Source`/`Sink` helper를 사용하세요. 현재 모든 vips 입력 overload는 `Path`를
포함해 50 MiB input guard 안에서 compressed input을 검증하고 버퍼링합니다.
따라서 `Path`가 이 제한을 우회하거나 streaming memory semantics를 제공하지는
않습니다.

벤치마크 근거: [`benchmark/images-benchmark/docs/large-streaming-2026-07-10.md`](benchmark/images-benchmark/docs/large-streaming-2026-07-10.md).

<!-- README_VISUAL_OVERVIEW:START -->
## 개요 다이어그램

![Bluetape4k Image overview diagram](docs/images/readme-diagrams/root-readme-overview-01.png)

색상 의미: 파란색은 API 선택, 초록색은 처리 결과, 주황색은 서비스 검증,
보라색은 native backend 선택, 회색은 benchmark 비교를 나타냅니다.

## 모듈 구성 차트

![Bluetape4k Image module composition chart](docs/images/readme-charts/root-readme-module-chart-01.png)
<!-- README_VISUAL_OVERVIEW:END -->

## 모듈 구성

| 모듈                   | Artifact ID                          | 설명                                                      |
|-----------------------|--------------------------------------|----------------------------------------------------------|
| `bom`                 | `bluetape4k-image-bom`               | 이미지 아티팩트 버전 정렬용 소비자 BOM                    |
| `images`              | `bluetape4k-images`                  | Scrimage 기반 처리와 runtime-free detector 결과 계약 |
| `images-barcode-api`  | `bluetape4k-images-barcode-api`      | Provider-neutral barcode/QR 추출 contract                |
| `images-barcode-zxing` | `bluetape4k-images-barcode-zxing`   | QR과 주요 1D 포맷을 위한 순수 JVM ZXing barcode provider |
| `images-captcha`      | `bluetape4k-images-captcha`          | Java2D CAPTCHA 이미지 챌린지 생성                         |
| `images-ocr`          | `bluetape4k-images-ocr`              | `ImmutableImage`용 Tess4J/Tesseract OCR 텍스트 추출        |
| `images-ktor`         | `bluetape4k-images-ktor`             | 썸네일 생성과 CAPTCHA 검증을 위한 Ktor route helper        |
| `images-spring-boot`  | `bluetape4k-images-spring-boot`      | Spring Boot 4 자동 구성: 스토리지, CDN, 헬스, 메트릭          |
| `images-vips-api`     | `bluetape4k-images-vips-api`         | 공유 `VipsImage` / `VipsRuntime` 인터페이스 (바인딩 중립)     |
| `images-vips-java21`  | `bluetape4k-images-vips-java21`      | JVips JNI 백엔드 — JDK 25+, 시스템 libvips 필요 (legacy artifact name) |
| `images-vips-java25`  | `bluetape4k-images-vips-java25`      | vips-ffm FFM 백엔드 — Java 25+, `--enable-native-access` |
| `benchmark/images-benchmark` | `bluetape4k-images-benchmark`        | `kotlinx-benchmark`: scrimage vs libvips                  |

## 아키텍처

![image Architecture diagram](docs/images/readme-diagrams/bluetape4k-image-architecture-01.png)

## 요구사항

| 모듈                   | JDK    | Native package | JVM 플래그                          |
|-----------------------|--------|----------------|-------------------------------------|
| `images`              | 25+    | —              | —                                   |
| `images-barcode-api`  | 25+    | —              | —                                   |
| `images-barcode-zxing` | 25+   | —              | —                                   |
| `images-captcha`      | 25+    | —              | —                                   |
| `images-ocr`          | 25+    | Tesseract + traineddata | —                          |
| `images-ktor`         | 25+    | —              | —                                   |
| `images-vips-api`     | 25+    | —              | —                                   |
| `images-vips-java21`  | 25+    | libvips        | —                                   |
| `images-vips-java25`  | 25+    | libvips        | `--enable-native-access=ALL-UNNAMED` |

`images-vips-api`와 `images-vips-java21`로 published되는 JVips JNI 구현을
포함한 모든 라이브러리 모듈은 JDK 25를 대상으로 합니다. 기존 artifact/module 및
package 이름은 호환성을 위해 유지하며, 지원 bytecode/runtime 기준만 변경되었습니다.

### OCR용 Tesseract 설치

`images-ocr` 모듈은 Tess4J를 사용하므로 실행 호스트에 Tesseract와
`OcrOptions`에서 요청한 traineddata 언어팩이 설치되어 있어야 합니다. 이 모듈은
traineddata 파일을 번들하지 않습니다.

```bash
# macOS
brew install tesseract tesseract-lang

# Ubuntu / Debian
sudo apt-get install tesseract-ocr tesseract-ocr-eng tesseract-ocr-kor tesseract-ocr-jpn fonts-noto-cjk

# 언어 데이터 확인
tesseract --list-langs
```

Tesseract가 언어 데이터를 찾지 못하면 `TESSDATA_PREFIX`를 설정하거나
`OcrOptions(tessdataPath = "/path/to/tessdata")`를 전달하세요.

### libvips 설치

순수 JVM `images` 모듈은 native library가 필요하지 않습니다. `images-vips-*`
모듈은 JNI 또는 FFM으로 libvips를 로드하므로 호스트에 native package가 있어야 합니다.

```bash
# macOS
brew install vips

# Ubuntu / Debian
sudo apt-get install libvips-tools libvips-dev

# CLI와 공유 라이브러리 확인
vips --version
```

`images-vips-java25` Gradle 테스트는 이미 `--enable-native-access=ALL-UNNAMED`를
추가하고, Homebrew macOS에서 `/opt/homebrew/lib`가 있으면
`DYLD_LIBRARY_PATH=/opt/homebrew/lib`도 설정합니다. 소비자 애플리케이션은 이 설정을
직접 적용해야 합니다.

```bash
export DYLD_LIBRARY_PATH=/opt/homebrew/lib
java --enable-native-access=ALL-UNNAMED -jar my-image-app.jar
```

native-access 플래그는 JVM 옵션이므로 `-jar`, main class, 또는 애플리케이션 시작
명령보다 앞에 와야 합니다.

### AVIF / HEIC native codec 지원

AVIF와 HEIC는 공유 `VipsImageFormat` API에 노출되어 있지만, 실제 지원 여부는 선택한
백엔드와 native libvips 빌드에 따라 달라집니다.

| 백엔드 | AVIF decode | AVIF encode | HEIC decode | HEIC encode | Native dependency |
|--------|-------------|-------------|-------------|-------------|-------------------|
| `images` | N/A | N/A | N/A | N/A | 순수 JVM scrimage 경로. 이 포맷은 `images-vips-*` 사용 |
| `images-vips-java21` | Capability-gated | Capability-gated | Capability-gated | N/A | libheif 포함 libvips. AVIF 출력은 libaom 같은 AV1 인코더도 필요 |
| `images-vips-java25` | Capability-gated | Capability-gated | Capability-gated | Capability-gated | libheif와 AV1/HEVC 인코더 포함 libvips |

Capability-gated는 API가 AVIF/HEIC 헤더나 출력 포맷을 허용한 뒤, 실제 decode/encode 가능
여부를 native libvips 설치 상태가 결정한다는 뜻입니다. 허용되지 않은 magic byte는
`VipsDecodeException`으로 실패하고, 누락되었거나 비활성화된 native HEIF 계열 코덱은
sanitized `VipsDecodeException` 또는 `VipsEncodeException`으로 실패합니다. 운영 호스트에서는
`vips --version`과 작은 AVIF/HEIC decode 또는 encode smoke test로 같은 JVM 실행 환경의
capability를 확인하세요.

각 vips runtime은 구조화된 codec report와 opt-in smoke helper를 제공합니다.
AVIF/HEIC capability surface는 binding 전용이며 `VipsIncubatingApi`로 표시됩니다.

```kotlin
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.images.vips.VipsRuntime

@OptIn(VipsIncubatingApi::class)
fun verifyHeic(runtime: VipsRuntime, heicSampleBytes: ByteArray) {
val report = runtime.codecCapabilityReport()
val heic = report.codec(VipsImageFormat.HEIC)

val smoke = runtime.smokeTestCodec(
    sampleBytes = heicSampleBytes,
    outputFormat = VipsImageFormat.HEIC,
)
}
```

두 JDK 25 백엔드는 `heifload_buffer`, `heifsave_buffer` native operation availability를
보고합니다. JVips binding은 한계를 명시하고, binding이 native libvips 빌드를 직접 검사할
수 없는 항목은 `UNKNOWN`으로 보고합니다.

### libvips 시작 문제 해결

- `FFM API requires --enable-native-access` 또는 `UnsupportedOperationException`:
  `images-vips-java25`를 `--enable-native-access=ALL-UNNAMED`와 함께 시작하세요.
- `libvips not found`, `Cannot find vips library`, 또는 `UnsatisfiedLinkError`:
  libvips를 설치하고 `vips --version`을 확인한 뒤, Homebrew macOS에서는 JVM 시작 전에
  `DYLD_LIBRARY_PATH=/opt/homebrew/lib`를 export하세요.
- vips 테스트가 예상과 다르게 skip됨: libvips가 설치되어 있고 로드 가능한 환경에서만
  `-Dvips.enabled=true`를 전달하세요. 명시적으로 제외하려면 `-Dvips.enabled=false`를
  전달하세요.
- OCR에서 `Error opening data file` 또는 언어 누락 오류가 발생함: 요청한 traineddata
  패키지를 설치하고 `tesseract --list-langs`를 확인한 뒤 `TESSDATA_PREFIX` 또는
  `OcrOptions.tessdataPath`를 설정하세요.
- OCR native loading이 `UnsatisfiedLinkError`로 실패함: 실행 호스트에 Tesseract를
  설치하고 같은 shell에서 `tesseract --version`이 동작하는지 확인하세요.

## 의존성 추가

안정 릴리스는 Maven Central에 배포됩니다. 필요한 모듈을 현재 image
릴리스 버전으로 선언하세요.

```kotlin
// build.gradle.kts
dependencies {
    // 하나의 중앙 BOM 버전만 선택하면 모든 Image artifact 버전이 맞춰집니다.
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))

    // Scrimage 기반 이미지 처리 (Java 25+)
    implementation("io.github.bluetape4k.image:bluetape4k-images")

    // Provider-neutral barcode/QR 추출 contract (Java 25+, 0.4.0+)
    implementation("io.github.bluetape4k.image:bluetape4k-images-barcode-api")

    // ZXing barcode provider (Java 25+, 0.4.0+)
    implementation("io.github.bluetape4k.image:bluetape4k-images-barcode-zxing")

    // Java2D CAPTCHA 생성 (Java 25+)
    implementation("io.github.bluetape4k.image:bluetape4k-images-captcha")

    // Tess4J/Tesseract OCR 추출 (Java 25+)
    implementation("io.github.bluetape4k.image:bluetape4k-images-ocr")

    // CAPTCHA 발급과 검증을 위한 Ktor route helper (Java 25+)
    implementation("io.github.bluetape4k.image:bluetape4k-images-ktor")

    // Spring Boot 4 자동 구성 (스토리지, CDN, 헬스, 메트릭)
    implementation("io.github.bluetape4k.image:bluetape4k-images-spring-boot")

    // libvips — 공유 API (두 vips 구현체 모두에 필요)
    implementation("io.github.bluetape4k.image:bluetape4k-images-vips-api")

    // 아래 vips 백엔드 중 하나를 선택:
    // JVips JNI 백엔드 (JDK 25, legacy java21 artifact)
    runtimeOnly("io.github.bluetape4k.image:bluetape4k-images-vips-java21")
    // 또는 Java 25 FFM 백엔드
    runtimeOnly("io.github.bluetape4k.image:bluetape4k-images-vips-java25")
}
```

## 사용 예시

### Scrimage를 사용한 이미지 로드 및 저장 (`images`)

```kotlin
import io.bluetape4k.images.*
import io.bluetape4k.images.coroutines.*
import java.io.File
import java.nio.file.Paths

// 이미지 로드
val image = immutableImageOf(File("photo.jpg"))

// 코루틴 비동기 로드
val image = suspendImmutableImageOf(File("photo.jpg"))

// WebP로 저장 (코루틴 내부에서 비동기)
image.suspendWrite(SuspendWebpWriter.Default, Paths.get("output.webp"))

// ByteArray로 인코딩
val jpegBytes = image.suspendBytes(SuspendJpegWriter(compression = 85))
```

### 필터 적용 (`images`)

```kotlin
import io.bluetape4k.images.filters.dsl.*
import com.sksamuel.scrimage.ImmutableImage

val result: ImmutableImage = image.applyFilters {
    brightness(1.2f)
    saturation(1.1f)
    gaussianBlur(radius = 2)
    roundedCorners(radius = 20)
}

// 코루틴 비동기 버전
val result = image.suspendApplyFilters {
    sepia()
    vignette()
}
```

### CAPTCHA 챌린지 생성 (`images-captcha`)

```kotlin
import io.bluetape4k.images.captcha.CaptchaDistortion
import io.bluetape4k.images.captcha.CaptchaNoise
import io.bluetape4k.images.captcha.captchaGenerator

val generator = captchaGenerator {
    length(6)
    charSet("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
    imageSize(width = 200, height = 80)
    noise(CaptchaNoise.Medium)
    distortion(CaptchaDistortion.Wave(0.2f))
}

val challenge = generator.generate()

// challenge.text는 서버 측에서 안전하게 보관하세요.
// challenge.image는 Scrimage writer로 인코딩해 클라이언트에 반환하세요.
```

### ZXing Barcode 추출 (`images-barcode-api` + `images-barcode-zxing`)

```kotlin
import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeOptions
import io.bluetape4k.images.barcode.extractBarcodes
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader

fun extractQrCodes(image: ImmutableImage) = image.extractBarcodes(
    reader = ZxingBarcodeReader(),
    options = BarcodeOptions(formats = setOf(BarcodeFormat.QR_CODE)),
)
```

`images-barcode-api`는 의도적으로 decoder dependency를 포함하지 않습니다.
ZXing provider는 `images-barcode-zxing`에만 위치하고, ZXing result point와
backend format label을 `BarcodeResult`로 변환하며, barcode가 없으면 빈 목록을
반환합니다. ZXing은 순수 JVM Apache-2.0 provider지만, 장기적으로 유일한 provider가
아니라 첫 OSS provider 경로로 다룹니다.

HTTP로 직접 실행해 보려면
[`spring-boot-barcode-api` quickstart](examples/spring-boot-barcode-api/README.ko.md)를
사용하세요. Deterministic found/no-result/malformed 시나리오와 제한이 적용된
multipart upload endpoint를 제공합니다.

#### Barcode Provider Capability Matrix

| Provider | 모듈 | 상태 | 포맷과 범위 | Fixture와 문서 근거 |
|----------|------|------|-------------|-------------------|
| API contract | `images-barcode-api` | 사용 가능 | 직접 decoding 없음. `BarcodeReader`, `BarcodeOptions`, `BarcodeResult`, `BarcodeRegion`, input helper 제공 | `BarcodeTestFixtures`가 no-code image, rotated image, malformed bytes, generated-source note를 공유 fixture로 제공 |
| ZXing | `images-barcode-zxing` | 사용 가능 | ZXing 기반 QR Code와 주요 1D/2D 포맷. 테스트는 QR Code와 Code 128을 검증 | ZXing writer가 deterministic in-memory QR/Code 128 이미지를 생성 |
| BoofCV | — | 보류 | Research 기준 QR, Micro QR, Aztec 특화 범위. 0.4.0의 broad 1D barcode backend로는 채택하지 않음 | `docs/superpowers/research/2026-07-03-issue-246-boofcv-provider-research.md` 참고 |
| Commercial SDK | — | 보류 | 산업용 decoding 요구를 위한 유료 또는 closed-source 선택 provider | #248은 license, redistribution, support policy 승인 전까지 구현 issue를 만들지 않도록 권고 |
| Native/JNI SDK | — | 보류 | native packaging, JNI/FFM 설정, platform-specific CI가 필요한 선택 provider | #248은 native runtime과 CI policy 승인 전까지 구현 issue를 만들지 않도록 권고 |

Provider module test는 deterministic code로 QR과 Code 128 fixture를 실행 시점에
생성합니다. Spring Boot quickstart는 HTTP 시나리오를 항상 같은 방식으로 재현할 수
있도록 QR, no-result, malformed resource를 별도로 번들합니다.

### OCR 텍스트 추출 (`images-ocr`)

```kotlin
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.extractText
import io.bluetape4k.images.ocr.suspendExtractText

val text = image.extractText(
    OcrOptions(languages = listOf("eng", "kor")),
)

val suspendText = image.suspendExtractText(
    OcrOptions(
        languages = listOf("eng"),
        tessdataPath = "/opt/homebrew/share/tessdata",
    ),
)
```

문서별 인식 방식이 필요하면 `pageSegmentationMode`, `engineMode`, `variables`,
`configs`를 설정하세요. 기본 엔진은 OCR 호출마다 새 Tess4J 인스턴스를 만들기 때문에
호출자끼리 mutable native OCR 상태를 공유하지 않습니다.

### Detector Boundary 정의 (`images`)

core `images` 모듈은 production ML runtime을 추가하지 않고 detector 결과 계약만
정의합니다. deterministic fake, OpenCV/ONNX/TensorFlow Lite/MediaPipe adapter,
외부 서비스 client 중 무엇을 쓰더라도 `ImageDetector`를 구현하면 face, object, text,
logo, sensitive-region 결과를 같은 모델로 다룰 수 있습니다.

```kotlin
import io.bluetape4k.images.detection.*

val detector = ImageDetector { _, _ ->
    listOf(
        DetectionResult(
            label = "face",
            category = DetectionCategory.FACE,
            confidence = 0.96,
            detector = DetectorIdentity(name = "example-detector", version = "test"),
            region = DetectionRegion(
                geometry = DetectionRectangleRegion(
                    x = 0.1,
                    y = 0.2,
                    width = 0.4,
                    height = 0.3,
                    coordinateSpace = DetectionCoordinateSpace.NORMALIZED,
                ),
            ),
        ),
    )
}

val faces = image.detectRegions(
    detector = detector,
    options = DetectionOptions(
        minimumConfidence = 0.8,
        categories = setOf(DetectionCategory.FACE),
    ),
)
```

Detection region은 sensitive-content geometry model을 재사용하므로 rectangle,
polygon, polyline, raster-mask metadata를 이후 moderation policy나 privacy-safe
derivative pipeline으로 넘길 수 있습니다. moderation policy layer는 detector가 만든
사실만 보고 `ALLOW`, `MOSAIC`, `BLUR`, `SOLID_MASK`, `DROP`, `REJECT`,
`QUARANTINE`, `MANUAL_REVIEW` action을 선택할 수 있으며, pixel rendering은 하지
않습니다. 알 수 없거나 rule에 맞지 않는 민감 category는 quarantine/manual-review
성격의 fail-closed 정책으로 다루고, 애플리케이션은 detector false negative, false
positive, route별 threshold를 별도로 고려해야 합니다.

core 모듈은 model을 다운로드하거나, 큰 fixture를 번들하거나, GPU 지원을 요구하거나,
treatment를 렌더링하거나, production runtime을 선택하지 않습니다. 그런 adapter는 후속
모듈이나 애플리케이션에서 다룹니다.

테스트 suite에는 `images/src/test/resources/detection/samples/` 아래
license-audited internet sample corpus가 포함됩니다. face/person, traffic sign과 text,
Earth/landmark 성격의 이미지, document text를 다룹니다.
`ImageDetectionSampleCorpusTest`를 실행하면 `build/reports/detection-samples.md`에
dimensions, dominant colors, blur score, EXIF 여부, manifest 기반 detector-boundary
category가 기록됩니다.

![Manifest 기반 detection sample 결과](docs/images/detection-samples/sample-detection-results.png)

이 preview 이미지는 같은 manifest를 입력으로
`docs/scripts/generate-detection-sample-overlays.py`가 생성합니다. 따라서 README에
보이는 rectangle은 테스트 suite가 검증하는 annotation과 동일합니다.

### Ktor 이미지와 CAPTCHA 라우트 (`images-ktor`)

```kotlin
import io.bluetape4k.images.ktor.bluetape4kCaptchaRoutes
import io.bluetape4k.images.ktor.bluetape4kImageThumbnailRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.module() {
    routing {
        bluetape4kImageThumbnailRoutes()
        bluetape4kCaptchaRoutes()
    }
}
```

`POST /images/thumbnail?maxSide=320`는 multipart field `file`을 읽어 PNG
썸네일 bytes를 반환합니다. `GET /captcha`는 base64 PNG 챌린지 payload를 반환합니다.
`POST /captcha/{id}/verify`는 챌린지를 소비하고 `SUCCESS`, `WRONG_ANSWER`,
`EXPIRED`, `NOT_FOUND` 중 하나를 반환합니다. 애플리케이션은 자체 Ktor JSON/error
plugin을 설치하면 됩니다. 이 helper는 `bluetape4k-projects`의 공용 Ktor core 모듈이
선택한 release train에 올라오면 함께 사용 가능한 형태로 맞춰 두었습니다.

### libvips를 사용한 고성능 처리 (`images-vips-api`)

`images-vips-java21`(JNI)과 `images-vips-java25`(FFM) 모두 `VipsImage` 인터페이스를 구현합니다.
인터페이스에 대해 프로그래밍하고 런타임에 백엔드를 선택하세요.

```kotlin
import io.bluetape4k.images.vips.*
import io.bluetape4k.images.vips.coroutines.*
import java.nio.file.Path

// VipsImage는 AutoCloseable — 반드시 .use { } 로 사용
vipsImageOf(Path.of("photo.jpg")).use { image ->
    // 리사이즈
    image.resize(1280, 720).use { resized ->
        resized.writeTo(Path.of("output.jpg"), VipsImageFormat.JPEG)
    }

    // 썸네일 (비율 유지)
    image.thumbnail(800).use { thumb ->
        thumb.writeTo(Path.of("thumb.webp"), VipsImageFormat.WEBP)
    }
}

// 코루틴 비동기 — 블로킹 I/O를 Dispatchers.IO에서 실행
vipsImageOf(Path.of("photo.jpg")).use { image ->
    val bytes = image.suspendToBytes(
        format = VipsImageFormat.WEBP,
        options = VipsEncodeOptions(quality = 80, lossless = false),
    )
}
```

### Java 25 FFM 백엔드 (`images-vips-java25`)

```kotlin
import io.bluetape4k.images.vips.java25.*

// 한 번만 초기화 (JVM 종료 훅이 정리를 처리)
FfmVipsRuntime.init(concurrency = 4)

FfmVipsImageSupport.ffmVipsImageOf(Path.of("photo.jpg")).use { image ->
    image.thumbnail(800).use { thumb ->
        thumb.writeTo(Path.of("thumb.webp"), VipsImageFormat.WEBP)
    }
}
```

> **참고**: `images-vips-java25` 사용 시 JVM 시작 플래그에 `--enable-native-access=ALL-UNNAMED`를
> 추가해야 합니다. `java -jar`에서는 `-jar` 앞에 배치하세요.

### JVips JNI 백엔드 (JDK 25, `images-vips-java21`)

```kotlin
import io.bluetape4k.images.vips.java21.*

JVipsRuntime.init(concurrency = 4)

JVipsImageSupport.jvipsImageOf(Path.of("photo.jpg")).use { image ->
    image.thumbnail(800).use { thumb ->
        thumb.writeTo(Path.of("thumb.webp"), VipsImageFormat.WEBP)
    }
}
```

## 모듈별 README

각 모듈에는 API 레퍼런스, 아키텍처 다이어그램, 사용 예시를 담은 상세 README가 있습니다.

- [`images/README.md`](images/README.md) — Scrimage 기반 처리
- [`images-barcode-api/README.md`](images-barcode-api/README.md) — Provider-neutral barcode contract
- [`images-barcode-zxing/README.md`](images-barcode-zxing/README.md) — 순수 JVM ZXing barcode provider
- [`images-captcha/README.md`](images-captcha/README.md) — Java2D CAPTCHA 생성
- [`images-ocr/README.md`](images-ocr/README.md) — Tess4J/Tesseract OCR 추출
- [`images-ktor/README.md`](images-ktor/README.md) — Ktor 썸네일 및 CAPTCHA route helper
- [`images-spring-boot/README.md`](images-spring-boot/README.md) — Spring Boot 4 자동 구성
- [`images-vips-api/README.md`](images-vips-api/README.md) — VipsImage 인터페이스 API
- [`images-vips-java21/README.md`](images-vips-java21/README.md) — JVips JNI 백엔드
- [`images-vips-java25/README.md`](images-vips-java25/README.md) — vips-ffm FFM 백엔드
- [`benchmark/images-benchmark/README.md`](benchmark/images-benchmark/README.md) — `kotlinx-benchmark` 결과

## 예제

순수 JVM quickstart는 [`examples/basic-processing`](examples/basic-processing/README.ko.md)부터
보면 됩니다. 번들된 `cafe.jpg`, `landscape.jpg` fixture와 루트 README 대표 이미지로
썸네일, smart crop, PNG 변환, 워터마크가 들어간 JPEG, README visual preview를 생성하고
결과는 `build/tmp/basic-processing` 아래에 둡니다.

Spring Boot 4 local-storage API 예제는
[`examples/spring-boot-image-api`](examples/spring-boot-image-api/README.ko.md)를
사용하세요. Multipart upload를 받고, `LocalImageStorage`로 원본을 저장하고, PNG
thumbnail을 만든 뒤 S3/CDN 설정 없이 storage key와 local read URL을 반환합니다.

Spring Boot 4 barcode API 예제는
[`examples/spring-boot-barcode-api`](examples/spring-boot-barcode-api/README.ko.md)를
사용하세요. Deterministic found, no-result, malformed endpoint와 PNG, JPEG, WebP
이미지용 bounded multipart upload endpoint를 제공합니다.

Spring Boot 4 OCR API 예제는
[`examples/spring-boot-ocr-api`](examples/spring-boot-ocr-api/README.ko.md)를
사용하세요. Multipart image upload를 받고, Tesseract language code를 해석한 뒤
`images-ocr`를 호출하며, 실제 OCR 실행에 필요한 local Tesseract와 traineddata
설정을 문서화합니다.

Spring Boot 4 통합 이미지 분석 예제는
[`examples/spring-boot-image-intelligence-api`](examples/spring-boot-image-intelligence-api/README.ko.md)를
사용하세요. 한 이미지를 한 번 검증하고 디코딩한 뒤 OCR, 객체 검출, 실제 ZXing
바코드 분석을 병렬로 실행하고, 부분 실패를 보존해 교체 가능한 방문증 정책에
전달합니다.

Ktor quickstart는 [`examples/ktor-image-api`](examples/ktor-image-api/README.ko.md)를
사용하세요. `images-ktor`의 CAPTCHA와 thumbnail route helper를 하나의 local-only
API로 묶고, challenge 발급과 multipart thumbnail 생성 curl 예제를 제공합니다.

Ktor OCR API 예제는 [`examples/ktor-ocr-api`](examples/ktor-ocr-api/README.ko.md)를
사용하세요. Multipart image upload를 받고, Tesseract language code를 해석한 뒤
`images-ocr`를 호출하며, host Tesseract/traineddata 설정은 local application
configuration에 둡니다.

## 라이선스

[MIT License](LICENSE)
