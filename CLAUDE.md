# CLAUDE.md — bluetape4k-image

이미지 처리 라이브러리. scrimage(Java2D) + libvips(JNI/FFM Panama) 이중 백엔드.

- **Group**: `io.github.bluetape4k.image` · **Base version**: `0.1.0-SNAPSHOT`

## Repository Layout

| Module | Description |
|-----------------------|-------------------------------------------------------------------------------|
| `images` | Scrimage-based image processing; coroutine writers; filters; analysis; similarity |
| `images-vips-api` | Binding-neutral `VipsImage` / `VipsRuntime` interfaces |
| `images-vips-java21` | JVips JNI backend — Java 21 toolchain; system libvips required on macOS |
| `images-vips-java25` | vips-ffm FFM backend — Java 25 toolchain; `--enable-native-access=ALL-UNNAMED` required |
| `images-benchmark` | JMH benchmarks (scrimage vs libvips: resize/encode/thumbnail) |

## Build Commands

```bash
./gradlew clean build
./gradlew build -x test
./gradlew :images:build
./gradlew :images:test
./gradlew :images-vips-java21:test
./gradlew :images-vips-java25:test
./gradlew test --tests "io.bluetape4k.images.ImmutableImageSupportTest"
./gradlew detekt
./gradlew publishAggregationToCentralPortalSnapshots   # SNAPSHOT
./gradlew publishAggregationToCentralPortal            # RELEASE
```

### libvips 선행 조건

```bash
brew install vips                       # macOS
sudo apt-get install libvips-dev        # Ubuntu/Debian
```

macOS: `images-vips-java25` 테스트 시 `/opt/homebrew/lib` 존재하면 자동으로 `DYLD_LIBRARY_PATH` 설정.
Gradle 외부 실행 시 수동 설정 필요:

```bash
export DYLD_LIBRARY_PATH=/opt/homebrew/lib
```

## 이미지 모듈 특이사항

### `images` — ImmutableImage

- `immutableImageOf(bytes/file/path/stream)` 팩토리 함수 사용
- 모든 연산은 새 인스턴스 반환 — 원본 절대 변경 안 됨
- `withGraphics { }` 사용 (`useGraphics { }` deprecated)

### `images-vips-*` — Native Memory

`VipsImage` 구현체는 native memory 보유. 반드시 `use { }` 또는 `close()` 호출:

```kotlin
vipsImageOf(file).use { image ->
    val thumb = image.thumbnail(800)
    thumb.writeTo(outputPath, VipsImageFormat.WEBP)
}
```

### `@IncubatingImageApi`

`images` 의 AVIF/HEIC interface 는 incubating. 구현체는 vips 모듈에 있음.
신규 incubating API → `@IncubatingImageApi` 어노테이션 필수.

### `images-vips-java25` 필수 설정

- **`atomicfu transformJvm = false`** — vips-ffm 은 Java 25 class file (v66.0). atomicfu bytecode transformer 가 Java 21 빌드 JVM 에서 실행되어 Java 25 class 로드 불가. 이 설정 제거 금지.
- **Java 25 toolchain** — `java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }` + `kotlin { jvmToolchain(25) }` 모두 설정
- **`--enable-native-access=ALL-UNNAMED`** — FFM API 필수. 테스트 태스크에서 자동 설정됨. 소비자 앱은 JVM 인수에 추가 필요.
- 클래스명: `FfmVipsImage` / `FfmVipsRuntime`

### `images-vips-java21` 특이사항

- JVips JNI 바인딩 (`jvips` artifact) 사용
- 각 테스트 클래스가 별도 JVM fork 에서 실행 (`forkEvery = 1`, `maxParallelForks = 1`) — JNI 네이티브 라이브러리 격리
- 클래스명: `JVipsImage` / `JVipsRuntime`

## PR 생성 전 추가 항목 (vips 모듈)

- [ ] macOS: `brew install vips` 확인; Linux: `apt-get install libvips-dev` 확인
- [ ] `images-vips-java25`: Gradle 외부 실행 시 `DYLD_LIBRARY_PATH` / `LD_LIBRARY_PATH` 설정 확인
- [ ] `images-vips-java25`: 앱 JVM args 에 `--enable-native-access=ALL-UNNAMED` 추가 확인
