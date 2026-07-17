---
manualId: "bluetape4k-images-ktor"
id: "bluetape4k-images-ktor"
title: "Ktor 이미지 라우트"
locale: "ko"
kind: "library"
gradlePath: ":bluetape4k-images-ktor"
sourceDir: "images-ktor"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-ktor
---

# Ktor 이미지 라우트

> 라이브러리 모듈

## 제공하는 기능 {#problem}

CAPTCHA 발급·검증과 multipart 썸네일 생성에 필요한 작은 Ktor 라우트 어댑터를 제공합니다. 입력 검증과 HTTP 응답 형식을 맞추되 인증, JSON 설치, 저장소와 요청 제한은 애플리케이션에 남겨 둡니다.

## 사용하기 좋은 경우 {#when-to-use}

간단한 Ktor 서비스에서 release CAPTCHA 계약이나 이미지 한 장을 줄이는 엔드포인트가 필요할 때 적합합니다. 업로드를 객체 저장소로 스트리밍하거나 libvips를 사용하고 복잡한 도메인 흐름과 연결해야 한다면 직접 라우트를 구성하세요.

## 의존성 좌표 {#coordinates}

Maven 좌표: `io.github.bluetape4k.image:bluetape4k-images-ktor`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-ktor")
}
```

## 핵심 개념 {#concepts}

`bluetape4kCaptchaRoutes`는 `CaptchaVerificationService` 위에 발급 GET과 검증 POST를 등록합니다. `bluetape4kImageThumbnailRoutes`는 multipart 파일 하나를 읽어 Scrimage로 디코딩하고 긴 변을 줄인 뒤 인코딩한 바이트를 응답합니다.

## 빠르게 시작하기 {#quick-start}

```kotlin
install(ContentNegotiation) { json() }

routing {
    bluetape4kCaptchaRoutes()
    bluetape4kImageThumbnailRoutes(
        ImageThumbnailKtorRoutesConfig(maxInputBytes = 5L * 1024 * 1024),
    )
}
```

## 작업별 API {#api-by-task}

- `GET /captcha?length=6`은 id, base64 PNG, content type과 만료 시각을 반환합니다.
- `POST /captcha/{id}/verify`는 challenge를 소비하고 성공/오답/만료/없음을 200/400/410/404로 구분합니다.
- `POST /images/thumbnail?maxSide=320`은 multipart `file` 필드에서 이미지를 읽고 기본 PNG를 반환합니다.
- config로 경로, 생성기, 저장소, id 생성, writer와 응답 타입을 바꿀 수 있습니다.

## 권장 패턴 {#patterns}

CAPTCHA 라우트보다 먼저 JSON 직렬화를 설치하세요. 클러스터에서는 분산 one-shot 저장소와 예측하기 어려운 id 생성기를 제공해야 합니다. 인증, CSRF, MIME 검증, 할당량과 rate limit은 API 경계에서 적용합니다.

## 연동 {#integrations}

`bluetape4k-ktor-core`, `bluetape4k-images`, `bluetape4k-images-captcha`를 사용합니다. Spring 저장소, S3, CDN, OCR, libvips는 이 라우트에 자동으로 연결되지 않습니다.

## 설정 {#configuration}

CAPTCHA 기본 경로는 `/captcha`입니다. 썸네일은 `/images/thumbnail`, 필드 `file`, 최대 입력 10MiB, 기본 긴 변 320, 최대 2,048, PNG 출력입니다. 검사한 multipart part는 성공 여부와 관계없이 해제합니다.

## 실패 유형과 해결 방법 {#failures}

잘못된 파라미터, 누락되거나 이름이 다른 파일 필드, 빈 파일과 제한 초과는 공통 400 오류 응답으로 바뀝니다. 손상된 이미지 I/O도 400입니다. CAPTCHA 결과는 별도 상태 코드를 사용하며 예상하지 못한 런타임 오류는 서버 오류로 남습니다.

## 운영 {#operations}

Scrimage 디코딩·인코딩은 `Dispatchers.IO`에서 실행하지만 업로드는 제한된 바이트 배열로 버퍼링합니다. 답이나 이미지 내용을 기록하지 말고 요청 크기, 디코딩 시간, 상태 코드, CAPTCHA 발급/검증 비율을 관찰하세요.

## 테스트 {#testing}

`CaptchaKtorRoutesTest`와 `ImageThumbnailKtorRoutesTest`가 라우트 동작을 검증합니다. 애플리케이션에서는 JSON 설치, 인증, 분산 저장소, 본문 크기 제한, 손상된 요청 데이터 테스트를 추가하세요.

## 학습 경로와 예제 {#workshops}

라우트 테스트를 읽은 뒤 `examples/ktor-image-api`, `examples/ktor-ocr-api`를 실행하세요. 운영에 배포하기 전에 기본 인메모리 저장소와 보안 경계를 교체해야 합니다.

## 제약 사항 {#limitations}

썸네일 엔드포인트는 스트리밍 변환이 아닙니다. `maxInputBytes + 1`까지 읽고 Scrimage를 사용하며 썸네일 하나만 만들어 응답할 뿐 저장하지 않습니다. CAPTCHA 라우트도 JSON과 남용 방지 기능을 설치하지 않습니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.3.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Images Ktor 아키텍처

[![Images Ktor 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-ktor-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-ktor-architecture-01.svg)

_배포본 README: [`images-ktor/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-ktor/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [CAPTCHA 라우트](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/CaptchaKtorRoutes.kt)
- [썸네일 라우트와 제한](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/ImageThumbnailKtorRoutes.kt)
