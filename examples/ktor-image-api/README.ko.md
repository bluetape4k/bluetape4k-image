# Ktor Image API Quickstart

[English](./README.md) | 한국어

`bluetape4k-images-ktor` route helper를 로컬에서 실행해 보는 작은 Ktor 3
예제입니다. CAPTCHA 발급/검증 route와 multipart image thumbnail route를 제공하며
S3, CDN, 외부 서비스, Docker, native libvips가 필요하지 않습니다.

## 보여주는 내용

- `/api/captcha`에 `bluetape4kCaptchaRoutes` 연결
- `/api/images`에 `bluetape4kImageThumbnailRoutes` 연결
- Ktor `ContentNegotiation` 기반 JSON serialization
- `bluetape4k-images`를 통한 multipart thumbnail 생성
- Ktor test host 기반 route test

이 quickstart는 local-only 흐름을 의도합니다. Persistence, public URL, S3/CDN,
운영 정책 결정이 필요하면 더 큰 workshop 또는 서비스 예제를 사용하세요.

## 실행

```bash
./gradlew :ktor-image-api:run
```

기본 포트는 `8080`입니다. `PORT`로 바꿀 수 있습니다.

```bash
PORT=9090 ./gradlew :ktor-image-api:run
```

## CAPTCHA Routes

CAPTCHA challenge 발급:

```bash
curl "http://localhost:8080/api/captcha?length=6"
```

응답 예:

```json
{
  "id": "4f3sGvA9tPq1LmN2",
  "imageBase64": "iVBORw0KGgo...",
  "contentType": "image/png",
  "expiresAt": "2026-05-29T07:00:00Z"
}
```

정답 검증:

```bash
curl -X POST "http://localhost:8080/api/captcha/4f3sGvA9tPq1LmN2/verify" \
  -H "Content-Type: application/json" \
  -d '{"answer":"123456"}'
```

## Thumbnail Route

Multipart image upload로 PNG thumbnail 생성:

```bash
curl -X POST \
  -F "file=@images/src/test/resources/images/cafe.jpg;type=image/jpeg" \
  "http://localhost:8080/api/images/thumbnail?maxSide=320" \
  -o thumbnail.png
```

이 route는 PNG bytes를 반환합니다. 원본 이미지를 저장하지 않으며 S3 또는 CDN
설정이 필요하지 않습니다.

## 테스트

```bash
./gradlew :ktor-image-api:test
```

테스트는 ready endpoint, CAPTCHA 발급, thumbnail 생성, multipart file field 누락
bad request 처리를 검증합니다.
