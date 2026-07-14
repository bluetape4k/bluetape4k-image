---
manualId: "ktor-image-api"
id: "ktor-image-api"
title: "Ktor 이미지 API 워크숍"
locale: "ko"
kind: "example"
gradlePath: ":ktor-image-api"
sourceDir: "examples/ktor-image-api"
releaseRef: "0.3.0"
artifact: null
---

# Ktor 이미지 API 워크숍

> 실행 가능한 예제

## 제공하는 기능 {#problem}

저장소가 제공하는 CAPTCHA와 썸네일 라우트 도우미를 하나의 Ktor 3 애플리케이션에 연결합니다. 스토리지, Docker, S3, CDN, libvips 없이 라우트 계약, 멀티파트 경계, JSON 오류 형식, 테스트 호스트 연동을 익힐 수 있습니다.

## 사용하기 좋은 경우 {#when-to-use}

`bluetape4k-images-ktor`를 평가하거나 로컬 CAPTCHA 흐름을 만들 때, multipart 이미지가 제한된 PNG thumbnail로 바뀌는 과정을 배우고 싶을 때 사용하세요. 스토리지 서비스의 완성형 예제는 아닙니다.

## 의존성 좌표 {#coordinates}

이 워크숍은 배포되지 않습니다. 애플리케이션에서는 `bluetape4k-dependencies` 버전 하나를 선택하고 `bluetape4k-images-ktor`와 필요한 Ktor 모듈을 추가하세요. 이미지 라이브러리 버전을 따로 고정하지 않습니다.

## 핵심 개념 {#concepts}

- `installBluetape4kKtorCore`가 공통 serialization과 API 오류 형식을 설치합니다.
- `bluetape4kCaptchaRoutes`가 challenge 발급과 일회성 답 검증을 처리합니다.
- `bluetape4kImageThumbnailRoutes`가 multipart 입력을 검사하고 PNG bytes를 반환합니다.
- `testApplication`으로 실제 포트를 열지 않고 같은 application module을 검증합니다.

## 빠르게 시작하기 {#quick-start}

JDK 21 이상이면 되고 외부 서비스는 필요하지 않습니다.

```bash
./gradlew :ktor-image-api:run
PORT=9090 ./gradlew :ktor-image-api:run
```

```bash
curl "http://localhost:8080/api/captcha?length=6"
curl -F "file=@images/src/test/resources/images/cafe.jpg;type=image/jpeg" \
  "http://localhost:8080/api/images/thumbnail?maxSide=320" -o thumbnail.png
```

## 작업별 API {#api-by-task}

| 작업 | 엔드포인트 또는 API | 확인할 결과 |
| --- | --- | --- |
| 준비 상태 | `GET /ready` | `200 OK`, 본문 `OK` |
| CAPTCHA 발급 | `GET /api/captcha?length=6` | id, Base64 PNG, 콘텐츠 타입, 만료 시각 |
| 답 검증 | `POST /api/captcha/{id}/verify` | 일회성 검증 결과 |
| thumbnail 생성 | `POST /api/images/thumbnail?maxSide=320` | `maxSide` 이하 PNG bytes |

## 권장 패턴 {#patterns}

재사용 route는 애플리케이션이 정한 명시적인 경로에 연결하고 readiness는 분리하세요. 요청 경계에서 이미지 크기를 제한하고, 저장이 필요 없는 경우에는 결과 bytes를 바로 반환하세요. 성공 응답과 공통 오류 payload를 함께 테스트해야 합니다.

## 연동 {#integrations}

[`bluetape4k-images-ktor`](./bluetape4k-images-ktor.md), [`bluetape4k-images-captcha`](./bluetape4k-images-captcha.md), [`bluetape4k-images`](./bluetape4k-images.md)를 함께 사용합니다. 저장이 필요하면 thumbnail route가 암묵적으로 저장하게 만들지 말고 애플리케이션 서비스로 분리하세요.

## 설정 {#configuration}

기본 포트는 `8080`이며 `PORT`로 바꿀 수 있습니다. 0.3.0 소스는 CAPTCHA를 `/api/captcha`, 이미지 route를 `/api/images`에 연결합니다.

## 실패 유형과 해결 방법 {#failures}

- 멀티파트 필드 누락 또는 이름 오류: 필드 이름은 `file`이며 `400 bad_request`가 반환됩니다.
- 지원하지 않는 입력 또는 잘못된 `maxSide`: 이미지 decoder보다 JSON 오류 메시지를 먼저 확인하세요.
- CAPTCHA 검증 실패: 오답, 만료, 이미 소비한 challenge를 구분하세요.
- 포트 충돌: `PORT`를 다른 값으로 지정하세요.

## 운영 {#operations}

챌린지 상태는 프로세스 로컬이고 썸네일은 저장하지 않습니다. 재시작하면 챌린지가 사라집니다. 운영 환경에서는 영속화, 요청률 제한, 오용 방지, 요청 크기, 공개 URL 정책을 별도로 정해야 합니다.

## 테스트 {#testing}

```bash
./gradlew :ktor-image-api:test
```

준비 상태, 디코딩 가능한 PNG CAPTCHA, 요청 크기 이하 썸네일, 파일 필드 누락 오류를 검증합니다.

## 학습 경로와 예제 {#workshops}

1. [`basic-processing`](./basic-processing.md)에서 이미지 연산을 먼저 익힙니다.
2. 이 워크숍에서 JSON과 binary 응답을 모두 확인합니다.
3. 위에 연결한 Ktor, CAPTCHA, 이미지 모듈 매뉴얼을 읽습니다.
4. [`ktor-ocr-api`](./ktor-ocr-api.md)에서 직접 작성한 streamed multipart route와 네이티브 오류 매핑을 배웁니다.

## 제약 사항 {#limitations}

로컬 학습용 예제입니다. 영속화, 공개 URL, S3/CDN, 인증, 분산 CAPTCHA 상태, 네이티브 가속은 포함하지 않습니다.

## 근거 자료 {#sources}

- [0.3.0 README](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/ktor-image-api/README.ko.md)
- [애플리케이션 소스](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/ktor-image-api/src/main/kotlin/io/bluetape4k/images/examples/ktor/KtorImageApiApplication.kt)
- [Route 테스트](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/ktor-image-api/src/test/kotlin/io/bluetape4k/images/examples/ktor/KtorImageApiApplicationTest.kt)
- [Gradle 빌드 파일](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/ktor-image-api/build.gradle.kts)
