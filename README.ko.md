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
- **libvips 추상화** — binding-neutral `VipsImage`, `VipsRuntime` 계약
- **두 native backend** — Java 21 JVips/JNI와 Java 25 FFM/Panama 선택지
- **Benchmark lane** — scrimage와 libvips resize/encode 경로를 비교하는 JMH 벤치마크

## 모듈 구성

| 모듈                   | Artifact ID                          | 설명                                                      |
|-----------------------|--------------------------------------|----------------------------------------------------------|
| `images`              | `images`                             | Scrimage 기반 처리: 로드, 리사이즈, 필터, 변환, 분석, 배치 처리 |
| `images-spring-boot`  | `images-spring-boot`                 | Spring Boot 4 자동 구성: 스토리지, CDN, 헬스, 메트릭          |
| `images-vips-api`     | `images-vips-api`                    | 공유 `VipsImage` / `VipsRuntime` 인터페이스 (바인딩 중립)     |
| `images-vips-java21`  | `images-vips-java21`                 | JVips JNI 백엔드 — Java 21+, 시스템 libvips 필요           |
| `images-vips-java25`  | `images-vips-java25`                 | vips-ffm FFM 백엔드 — Java 25+, `--enable-native-access` |
| `images-benchmark`    | `images-benchmark`                   | JMH 벤치마크: scrimage vs libvips                         |

## 아키텍처

![image Architecture diagram](docs/assets/readme-diagrams/bluetape4k-image-architecture-01.png)

## 요구사항

| 모듈                   | JDK    | libvips | JVM 플래그                          |
|-----------------------|--------|---------|-------------------------------------|
| `images`              | 21+    | —       | —                                   |
| `images-vips-api`     | 21+    | —       | —                                   |
| `images-vips-java21`  | 21+    | 필요    | —                                   |
| `images-vips-java25`  | 25+    | 필요    | `--enable-native-access=ALL-UNNAMED` |

### libvips 설치

```bash
# macOS
brew install vips

# Ubuntu / Debian
sudo apt-get install libvips-dev
```

macOS에서 `images-vips-java25`를 사용하는 애플리케이션은 다음 환경변수도 설정해야 합니다.

```bash
export DYLD_LIBRARY_PATH=/opt/homebrew/lib
```

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
> 추가해야 합니다.

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
- [`images-spring-boot/README.md`](images-spring-boot/README.md) — Spring Boot 4 자동 구성
- [`images-vips-api/README.md`](images-vips-api/README.md) — VipsImage 인터페이스 API
- [`images-vips-java21/README.md`](images-vips-java21/README.md) — JVips JNI 백엔드
- [`images-vips-java25/README.md`](images-vips-java25/README.md) — vips-ffm FFM 백엔드
- [`images-benchmark/README.md`](images-benchmark/README.md) — JMH 벤치마크 결과

## 라이선스

[MIT License](LICENSE)
