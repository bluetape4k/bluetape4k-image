---
manualId: "bluetape4k-images-vips-api"
id: "bluetape4k-images-vips-api"
title: "libvips 공통 API"
locale: "ko"
kind: "library"
gradlePath: ":bluetape4k-images-vips-api"
sourceDir: "images-vips-api"
releaseRef: "0.4.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-vips-api
---

# libvips 공통 API

> 라이브러리 모듈

## 제공하는 기능 {#problem}

libvips 바인딩에 의존하지 않는 공통 계약입니다. 애플리케이션 코드는 `VipsImage`, `VipsRuntime`만 바라보고 실제 실행 환경에서 JDK 25 JVips JNI 또는 JDK 25 FFM 백엔드를 선택할 수 있습니다. JNI 구현은 호환성을 위해 `java21` 모듈 이름으로 배포됩니다.

## 사용하기 좋은 경우 {#when-to-use}

네이티브 실행 환경을 감수하고 리사이즈, 썸네일, 크롭, 인코딩 처리량을 높이고 싶을 때 사용하세요. Java2D 그리기와 다양한 필터 DSL, 네이티브 없는 배포가 더 중요하면 Scrimage 모듈이 낫습니다.

## 의존성 좌표 {#coordinates}

Maven 좌표: `io.github.bluetape4k.image:bluetape4k-images-vips-api`

중앙 BOM과 이 API, 실행 백엔드 하나를 조합합니다.

## 핵심 개념 {#concepts}

`VipsImage`는 네이티브 자원을 소유하며 한 스레드에서 사용하는 계약입니다. 모든 변환은 닫아야 하는 새 이미지를 반환합니다. `VipsRuntime`은 프로세스 전체 libvips 초기화와 되돌릴 수 없는 종료를 관리합니다.

## 빠르게 시작하기 {#quick-start}

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-vips-api")
    runtimeOnly("io.github.bluetape4k.image:bluetape4k-images-vips-java25")
}
```

실제 이미지는 백엔드 팩토리로 만들며 반드시 `use` 또는 `close()`로 수명을 관리합니다.

## 작업별 API {#api-by-task}

- `width`, `height`, `bands`로 기본 정보를 읽습니다.
- `resize`, `thumbnail`, `crop`으로 새 이미지를 만듭니다.
- `toBytes`, `writeTo(Path|OutputStream)`로 인코딩합니다.
- 기존 Okio/코루틴 경계에는 suspend write extension을 사용합니다.
- `VipsRuntime.init(concurrency, maxPixels)`와 상태 프로퍼티로 런타임을 관리합니다.

## 권장 패턴 {#patterns}

프로세스 시작 시 한 번 초기화하고 JVM shutdown hook에서만 종료하세요. Spring devtools를 쓰는 애플리케이션의 `@PreDestroy`에 `shutdown()`을 연결하면 context 재시작 뒤 다시 초기화할 수 없습니다. 하나의 이미지를 여러 코루틴이 동시에 공유하지 마세요.

## 연동 {#integrations}

`images-vips-java21`, `images-vips-java25`가 이 계약을 구현합니다. API 모듈은 백엔드 기준 테스트용 이미지도 제공하며 실험적 애너테이션과 비교 도구를 위해 핵심 이미지 모듈을 사용합니다.

## 설정 {#configuration}

압축 입력은 최대 50MiB, 디코딩 결과는 기본 150,000,000 `width × height × bands`로 제한합니다. 기본 libvips 동시성은 4입니다. 인코딩 품질은 0..100, effort는 1..9이며 기본값은 85/4, metadata 제거입니다.

## 실패 유형과 해결 방법 {#failures}

실패는 `VipsDecodeException`, `VipsEncodeException`, `VipsOperationException`, `VipsInitializationException`으로 구분합니다. 공개 메시지는 정리되어 있고 네이티브 상세 원인은 cause에 남습니다. 종료 후 다시 초기화하려면 JVM을 재시작해야 합니다.

## 운영 {#operations}

입력 거부 이유, 네이티브 초기화, 연산·인코딩 시간, 프로세스 네이티브 메모리를 관찰하세요. Java 힙 벤치마크만으로 libvips 메모리를 판단하면 안 됩니다. `writeTo` 경로는 라이브러리가 루트 디렉터리를 검증하지 않으므로 호출자가 확인해야 합니다.

## 테스트 {#testing}

공통 API 테스트는 옵션과 Okio 소유권을 검증합니다. 기준 이미지, 속성, 작성기, 런타임 동시성은 각 백엔드 테스트에서 확인합니다. 네이티브 테스트는 호환되는 libvips 실행 환경에서 순차 실행하세요.

## 학습 경로와 예제 {#workshops}

공통 API를 읽은 뒤 선택한 백엔드의 자원 수명 문서를 살펴보고, 마지막으로 벤치마크를 비교하세요. JDK 25 JVips와 FFM 팩토리 이름이 달라 구성 시점에 선택이 분명하게 드러나며, JNI artifact는 호환성을 위해 `java21` 이름을 유지합니다.

## 제약 사항 {#limitations}

공통 API가 백엔드를 자동 탐색하거나 생성하지 않습니다. AVIF/HEIC는 백엔드와 호스트 코덱을 모두 갖춰야 합니다. JNI와 FFM의 변환 결과 수명 규칙이 다르므로 중첩 `use`를 작성하기 전에 백엔드 문서를 확인하세요.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.4.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### images vips api 아키텍처 2 다이어그램

[![images vips api 아키텍처 2 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-vips-api-architecture-02.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-vips-api-architecture-02.svg)

_배포본 README: [`images-vips-api/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-api/README.ko.md)_

### images vips api 클래스 구조도

[![images vips api 클래스 구조도](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-vips-api-class-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-vips-api-class-01.svg)

_배포본 README: [`images-vips-api/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-api/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [VipsImage 계약](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImage.kt)
- [런타임 종료 계약](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsRuntime.kt)
- [입력 제한](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsLimits.kt)
- [예외 정책](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsExceptions.kt)
