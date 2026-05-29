# bluetape4k-image

[![CI](https://github.com/bluetape4k/bluetape4k-image/actions/workflows/ci.yml/badge.svg)](https://github.com/bluetape4k/bluetape4k-image/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-21-ED8B00?logo=openjdk)](https://openjdk.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[English](./README.md) | 한국어

![bluetape4k 이미지 처리 작업대 일러스트](./docs/assets/image-workbench.png)

Kotlin/JVM 이미지 처리 라이브러리 — [bluetape4k](https://github.com/bluetape4k) 생태계의 일부입니다.
두 가지 백엔드를 제공합니다: 코루틴 비동기 I/O를 갖춘 순수 JVM [scrimage](https://github.com/sksamuel/scrimage)
경로(Java2D)와, JNI(Java 21) 및 Panama 외부 함수 & 메모리 API(Java 25)를 통해 제공되는 고성능
[libvips](https://www.libvips.org/) 경로입니다.

## 프로젝트 목적

`bluetape4k-image`는 Kotlin 서비스가 순수 JVM scrimage 처리로 시작하고, 처리량·메모리·
native codec이 중요해질 때 libvips 백엔드로 확장할 수 있는 단일 이미지 처리 표면을 제공합니다.

## 제공 기능

- **순수 JVM 처리** — scrimage/Java2D 기반 로드, 리사이즈, 크롭, 필터, 분석, 배치, 인코딩
- **Coroutine I/O** — 웹 이미지 워크플로우에 맞는 suspend reader/writer/byte encoder
- **CAPTCHA 생성** — native runtime 없이 Java2D로 bounded option 기반 이미지 챌린지 생성
- **Ktor 통합** — Ktor 서비스에서 CAPTCHA 이미지 발급과 one-shot 답변 검증을 처리하는 route helper
- **libvips 추상화** — binding-neutral `VipsImage`, `VipsRuntime` 계약
- **두 native backend** — Java 21 JVips/JNI와 Java 25 FFM/Panama 선택지
- **Benchmark lane** — scrimage와 libvips resize/encode 경로를 비교하는
  `kotlinx-benchmark` 벤치마크

<!-- README_VISUAL_OVERVIEW:START -->
## Overview Diagram

![Bluetape4k Image overview diagram](docs/assets/readme-diagrams/root-readme-overview-01.png)

## Module Composition Chart

![Bluetape4k Image module composition chart](docs/assets/readme-charts/root-readme-module-chart-01.png)
<!-- README_VISUAL_OVERVIEW:END -->

## 모듈 구성

| 모듈                   | Artifact ID                          | 설명                                                      |
|-----------------------|--------------------------------------|----------------------------------------------------------|
| `bom`                 | `bluetape4k-image-bom`               | 이미지 아티팩트 버전 정렬용 소비자 BOM                    |
| `images`              | `bluetape4k-images`                  | Scrimage 기반 처리: 로드, 리사이즈, 필터, 변환, 분석, 배치 처리 |
| `images-captcha`      | `bluetape4k-images-captcha`          | Java2D CAPTCHA 이미지 챌린지 생성                         |
| `images-ktor`         | `bluetape4k-images-ktor`             | 썸네일 생성과 CAPTCHA 검증을 위한 Ktor route helper        |
| `images-spring-boot`  | `bluetape4k-images-spring-boot`      | Spring Boot 4 자동 구성: 스토리지, CDN, 헬스, 메트릭          |
| `images-vips-api`     | `bluetape4k-images-vips-api`         | 공유 `VipsImage` / `VipsRuntime` 인터페이스 (바인딩 중립)     |
| `images-vips-java21`  | `bluetape4k-images-vips-java21`      | JVips JNI 백엔드 — Java 21+, 시스템 libvips 필요           |
| `images-vips-java25`  | `bluetape4k-images-vips-java25`      | vips-ffm FFM 백엔드 — Java 25+, `--enable-native-access` |
| `images-benchmark`    | `bluetape4k-images-benchmark`        | `kotlinx-benchmark`: scrimage vs libvips                  |

## 아키텍처

![image Architecture diagram](docs/assets/readme-diagrams/bluetape4k-image-architecture-01.png)

## 요구사항

| 모듈                   | JDK    | libvips | JVM 플래그                          |
|-----------------------|--------|---------|-------------------------------------|
| `images`              | 21+    | —       | —                                   |
| `images-captcha`      | 21+    | —       | —                                   |
| `images-ktor`         | 21+    | —       | —                                   |
| `images-vips-api`     | 21+    | —       | —                                   |
| `images-vips-java21`  | 21+    | 필요    | —                                   |
| `images-vips-java25`  | 25+    | 필요    | `--enable-native-access=ALL-UNNAMED` |

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

### libvips 시작 문제 해결

- `FFM API requires --enable-native-access` 또는 `UnsupportedOperationException`:
  `images-vips-java25`를 `--enable-native-access=ALL-UNNAMED`와 함께 시작하세요.
- `libvips not found`, `Cannot find vips library`, 또는 `UnsatisfiedLinkError`:
  libvips를 설치하고 `vips --version`을 확인한 뒤, Homebrew macOS에서는 JVM 시작 전에
  `DYLD_LIBRARY_PATH=/opt/homebrew/lib`를 export하세요.
- vips 테스트가 예상과 다르게 skip됨: libvips가 설치되어 있고 로드 가능한 환경에서만
  `-Dvips.enabled=true`를 전달하세요. 명시적으로 제외하려면 `-Dvips.enabled=false`를
  전달하세요.

## 의존성 추가

이 라이브러리는 Sonatype Central Portal에 SNAPSHOT으로 배포됩니다.
스냅샷 저장소를 추가하고 필요한 모듈을 선언하세요.

```kotlin
// build.gradle.kts
repositories {
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

dependencies {
    // Scrimage 기반 이미지 처리 (Java 21+)
    implementation("io.github.bluetape4k.image:bluetape4k-images:<version>")

    // Java2D CAPTCHA 생성 (Java 21+)
    implementation("io.github.bluetape4k.image:bluetape4k-images-captcha:<version>")

    // CAPTCHA 발급과 검증을 위한 Ktor route helper (Java 21+)
    implementation("io.github.bluetape4k.image:bluetape4k-images-ktor:<version>")

    // Spring Boot 4 자동 구성 (스토리지, CDN, 헬스, 메트릭)
    implementation("io.github.bluetape4k.image:bluetape4k-images-spring-boot:<version>")

    // libvips — 공유 API (두 vips 구현체 모두에 필요)
    implementation("io.github.bluetape4k.image:bluetape4k-images-vips-api:<version>")

    // 아래 vips 백엔드 중 하나를 선택:
    // Java 21 JNI 백엔드
    runtimeOnly("io.github.bluetape4k.image:bluetape4k-images-vips-java21:<version>")
    // 또는 Java 25 FFM 백엔드
    runtimeOnly("io.github.bluetape4k.image:bluetape4k-images-vips-java25:<version>")
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

### Java 21 JNI 백엔드 (`images-vips-java21`)

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
- [`images-captcha/README.md`](images-captcha/README.md) — Java2D CAPTCHA 생성
- [`images-ktor/README.md`](images-ktor/README.md) — Ktor 썸네일 및 CAPTCHA route helper
- [`images-spring-boot/README.md`](images-spring-boot/README.md) — Spring Boot 4 자동 구성
- [`images-vips-api/README.md`](images-vips-api/README.md) — VipsImage 인터페이스 API
- [`images-vips-java21/README.md`](images-vips-java21/README.md) — JVips JNI 백엔드
- [`images-vips-java25/README.md`](images-vips-java25/README.md) — vips-ffm FFM 백엔드
- [`images-benchmark/README.md`](images-benchmark/README.md) — `kotlinx-benchmark` 결과

## 예제

순수 JVM quickstart는 [`examples/basic-processing`](examples/basic-processing/README.ko.md)부터
보면 됩니다. 번들된 `cafe.jpg`, `landscape.jpg` fixture와 루트 README 대표 이미지로
썸네일, smart crop, PNG 변환, 워터마크가 들어간 JPEG, README visual preview를 생성하고
결과는 `build/tmp/basic-processing` 아래에 둡니다.

Spring Boot 4 local-storage API 예제는
[`examples/spring-boot-image-api`](examples/spring-boot-image-api/README.ko.md)를
사용하세요. Multipart upload를 받고, `LocalImageStorage`로 원본을 저장하고, PNG
thumbnail을 만든 뒤 S3/CDN 설정 없이 storage key와 local read URL을 반환합니다.

Ktor quickstart는 [`examples/ktor-image-api`](examples/ktor-image-api/README.ko.md)를
사용하세요. `images-ktor`의 CAPTCHA와 thumbnail route helper를 하나의 local-only
API로 묶고, challenge 발급과 multipart thumbnail 생성 curl 예제를 제공합니다.

## 라이선스

[MIT License](LICENSE)
