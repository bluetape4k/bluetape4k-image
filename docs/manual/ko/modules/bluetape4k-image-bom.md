---
manualId: "bluetape4k-image-bom"
id: "bluetape4k-image-bom"
title: "Image BOM"
locale: "ko"
kind: "library"
gradlePath: ":bluetape4k-image-bom"
sourceDir: "bom"
releaseRef: "0.4.0"
artifact: io.github.bluetape4k.image:bluetape4k-image-bom
---

# Image BOM

> 라이브러리 모듈

## 제공하는 기능 {#problem}

이미지 저장소는 JVM 요구 사항과 네이티브 실행 환경이 서로 다른 라이브러리 8개를 배포합니다. Image BOM은 API와 구현체가 서로 다른 배포본으로 섞이지 않도록 이 아티팩트의 버전을 맞춥니다.

일반 사용자는 Image BOM 버전을 따로 고를 필요가 없습니다. 생태계 전체 버전을 조율하는 `bluetape4k-dependencies` 하나만 가져오면 이미지, AWS, Ktor, Spring Boot 등 관련 라이브러리의 호환 버전이 함께 정해집니다.

## 사용하기 좋은 경우 {#when-to-use}

이미지 라이브러리만 독립적으로 사용하는 특별한 프로젝트나 버전 정렬을 검증하는 테스트에서만 Image BOM을 직접 사용하세요. 여러 bluetape4k 저장소를 함께 쓴다면 중앙 BOM이 더 알맞습니다.

## 의존성 좌표 {#coordinates}

Maven 좌표: `io.github.bluetape4k.image:bluetape4k-image-bom`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
}
```

## 핵심 개념 {#concepts}

이 모듈은 실행 코드가 없는 Gradle `java-platform`입니다. 배포 빌드는 BOM 자신과 예제, 벤치마크를 제외한 모든 배포 모듈에 제약을 겁니다.

대상은 `bluetape4k-images`, `-captcha`, `-ocr`, `-ktor`, `-spring-boot`, `-vips-api`, `-vips-java21`, `-vips-java25`입니다.

## 빠르게 시작하기 {#quick-start}

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images")
    implementation("io.github.bluetape4k.image:bluetape4k-images-vips-api")
    runtimeOnly("io.github.bluetape4k.image:bluetape4k-images-vips-java25")
}
```

libvips 백엔드는 Java 21 JNI와 Java 25 FFM 가운데 실행 환경에 맞는 하나만 선택합니다.

## 작업별 API {#api-by-task}

- 순수 JVM 처리에는 `bluetape4k-images`를 추가합니다.
- CAPTCHA, OCR, Ktor, Spring Boot 어댑터는 필요한 것만 고릅니다.
- 네이티브 처리에는 공통 `vips-api`와 백엔드 하나를 조합합니다.
- 버전이 섞였는지는 Gradle `dependencyInsight`로 확인합니다.

## 권장 패턴 {#patterns}

개별 이미지 의존성에는 버전을 쓰지 마세요. 중앙 플랫폼이 Kotlin, Spring Boot, AWS와 이미지 모듈의 호환 조합을 한꺼번에 결정하게 두는 편이 안전합니다.

## 연동 {#integrations}

Image BOM은 `bluetape4k-dependencies`에 포함됩니다. 단, Maven BOM은 libvips, Tesseract, 언어 데이터, JVM 실행 옵션 같은 운영 환경을 설치하거나 검증하지 않습니다.

## 설정 {#configuration}

BOM 자체에는 런타임 설정이 없습니다. 안정판 `0.4.0`은 Maven Central과 중앙 BOM으로 사용합니다. 스냅샷을 쓸 때만 Sonatype Central snapshots 저장소가 별도로 필요합니다.

## 실패 유형과 해결 방법 {#failures}

플랫폼을 빠뜨리면 버전을 찾지 못하거나 서로 다른 버전이 선택됩니다. 이때 개별 모듈을 하나씩 고정하지 말고 플랫폼 선언부터 바로잡으세요. 의존성 해석 성공은 네이티브 라이브러리 준비 완료를 뜻하지 않습니다.

## 운영 {#operations}

애플리케이션에는 `bluetape4k-dependencies` 버전만 기록하세요. 업그레이드할 때는 의존성 그래프를 확인하고 실제로 선택한 OCR/libvips 백엔드의 스모크 테스트를 실행합니다.

## 테스트 {#testing}

dependency lock이나 해석 테스트로 이미지 아티팩트가 한 버전으로 맞춰졌는지 확인합니다. BOM에는 실행 코드가 없으므로 기능 테스트는 각 라이브러리에서 수행합니다.

## 학습 경로와 예제 {#workshops}

먼저 `bluetape4k-images`로 시작하고, 필요한 서비스 어댑터나 libvips 백엔드를 하나씩 추가하세요. 의존성 정렬을 끝낸 뒤 저장소 예제와 벤치마크로 학습 범위를 넓히면 됩니다.

## 제약 사항 {#limitations}

BOM은 버전만 맞춥니다. Java 25 바이트코드를 Java 21에서 실행하게 만들거나 네이티브 코덱을 제공하지 않습니다. `0.4.0`의 BOM README 모듈 표에는 일부 아티팩트가 빠져 있으므로 실제 제약 목록은 `bom/build.gradle.kts`를 기준으로 봐야 합니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.4.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### bom 아키텍처

[![bom 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/bom-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/bom-architecture-01.svg)

_배포본 README: [`bom/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/bom/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [0.4.0 BOM 빌드](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/bom/build.gradle.kts)
- [0.4.0 모듈 등록 정보](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/settings.gradle.kts)
