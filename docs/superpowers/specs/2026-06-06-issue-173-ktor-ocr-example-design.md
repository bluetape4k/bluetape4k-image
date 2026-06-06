# Issue 173 Ktor OCR Example Design

## 문제

Issue #173은 `images-ocr`를 Ktor 애플리케이션에서 사용하는 최소 OCR API 예제를
요구한다. 이미 순수 `images-ocr` quickstart와 Spring Boot OCR API 예제가 있으나,
Ktor 사용자는 multipart upload, language option, native Tesseract 설정, route test를
한 번에 볼 수 있는 runnable example이 없다.

## 현재 근거

- GitHub issue #173: Ktor 기반 OCR 예제, image-to-text route, Tesseract 설정 문서,
  신규 OCR backend 제외.
- Ktor 공식 문서: Ktor 3 multipart upload는 `receiveMultipart()`와
  `PartData.FileItem.provider()`를 사용하고, route tests는 `testApplication`으로
  구성한다.
- `examples/ktor-image-api`: Ktor example은 `application` plugin, Netty server,
  `testApplication` pattern을 보여준다. 이 OCR example은 신규 Examples matrix에서
  `bluetape4k-ktor-core` snapshot을 직접 resolve하지 않도록 official Ktor
  ContentNegotiation과 local error DTO를 사용한다.
- `images-ktor/ImageThumbnailKtorRoutes.kt`: streamed multipart part는
  `provider().readRemaining(limit).readByteArray()`로 읽고 `part.release()`를 보장한다.
- `examples/spring-boot-ocr-api`: request-level tessdata path를 받지 않고,
  app config와 injectable `OcrEngine`으로 실제 native OCR 없이 route wiring을 검증한다.
- `docs/lessons/2026-06-06-issue-172-spring-boot-ocr-example.md`: native OCR
  quickstart tests는 fake engine을 주입하고, host path는 request가 아니라 app config에
  둔다.

## 목표

- 새 non-published example module `examples/ktor-ocr-api`를 추가한다.
- `POST /api/ocr` route가 multipart field `file`을 받고 query `languages`를
  기본값 `eng`로 해석한다.
- `languages=eng+kor`와 `languages=eng,kor`를 모두 지원한다.
- `example.ocr.tessdata-path`에 해당하는 host 설정은 request parameter가 아니라
  environment/configuration에서만 들어오게 한다.
- 정상 CI는 실제 Tesseract 설치를 요구하지 않는다.
- README는 영어/한국어 locale set으로 작성하고 native Tesseract/traineddata 설치와
  local run/test command를 설명한다.
- README diagram은 English label PNG를 embed하고, matching SVG/DOT/plain/Graphviz
  evidence를 생성한다.

## 비목표

- 새 OCR backend 추가.
- production-grade auth, rate limiting, queueing, persistence, batch OCR, OpenAPI 문서.
- native Tesseract smoke test를 Ktor example CI에 강제.
- request-level `tessdataPath` 지원.

## 설계 대안

### A. Ktor OCR example을 독립 module로 추가

`examples/ktor-ocr-api`를 추가하고 `images-ocr`와 Ktor runtime만 직접 연결한다.
Spring Boot OCR 예제와 같은 API shape를 유지하되 Ktor route/test idiom을 따른다.

채택한다. issue가 Ktor 사용자를 위한 별도 quickstart를 요구하고, root examples
목록에서도 framework별 example이 독립 module로 유지된다.

### B. 기존 `examples/ktor-image-api`에 OCR route를 추가

하나의 Ktor example에 CAPTCHA, thumbnail, OCR을 모두 넣는다.

거절한다. 기존 example은 `images-ktor` route helper composition을 보여주는 목적이고,
OCR은 native Tesseract 요구사항과 failure mode가 다르다. 섞으면 README와 CI 의도가
흐려진다.

### C. `images-ktor`에 reusable OCR route helper를 추가

published library에 `bluetape4kOcrRoutes` helper를 만든다.

거절한다. #173은 example scope이며 새 public API는 API design, compatibility,
production policy를 넓힌다. reusable helper가 필요하면 별도 follow-up issue로
다룬다.

## API 계약

- `GET /ready` returns `200 OK` plain text `OK`.
- `POST /api/ocr?languages=eng` consumes multipart form data.
- multipart field name은 `file`.
- 허용 content type: `image/jpeg`, `image/png`, `image/webp`, `image/gif`.
- 성공 응답:

```json
{
  "text": "recognized text",
  "languages": ["eng"],
  "characterCount": 15
}
```

- caller validation failure: `400 bad_request` with local `OcrApiErrorResponse`.
- `OcrException`: `503 ocr_unavailable`.

## 구성

- `PORT`: server port, default `8080`.
- `TESSDATA_PREFIX`: host Tesseract default lookup.
- `EXAMPLE_OCR_TESSDATA_PATH`: optional app-level tessdata path passed to
  `OcrOptions.tessdataPath`.

## 보안 / 신뢰 경계

- request는 host path를 지정할 수 없다.
- multipart upload는 configured byte limit을 넘으면 거절한다.
- empty file, missing file, wrong field name, unsupported content type은
  `400 bad_request`.
- example은 auth/rate limiting을 제공하지 않는 local quickstart임을 README에 명시한다.

## 테스트 수용 기준

- `:ktor-ocr-api:test`가 host Tesseract 없이 통과한다.
- fake `OcrEngine`으로 성공 응답, language parsing, tessdata config propagation,
  unsupported content type, OCR failure mapping을 검증한다.
- `./gradlew projects`가 `:ktor-ocr-api` 등록을 보여준다.
- `.github/workflows/Examples.yml` matrix가 `:ktor-ocr-api:test`를 실행한다.
- workflow 변경은 `actionlint`와 escaped quote guard를 통과한다.
- README locale set과 root examples list가 갱신된다.
- generated diagrams는 `manual_exceptions=0`, XML parse, README PNG-only embed,
  visual inspection을 통과한다.

## 위험과 대응

- Native OCR dependency가 CI를 깨뜨릴 수 있다.
  - 대응: example tests는 injected fake `OcrEngine`을 사용한다.
- Ktor multipart resource leak.
  - 대응: every part is released in `finally`, byte limit은 `readRemaining(limit + 1)`로
    확인한다.
- Spring example과 API drift.
  - 대응: endpoint shape와 response DTO를 의도적으로 맞추고 README에 framework별 차이만
    설명한다.
- Diagram routing defect.
  - 대응: generator validation, Graphviz evidence, SVG XML parse, rendered PNG visual
    inspection을 Step 6에 포함한다.

## DoD

- Spec/plan/review artifacts exist under `docs/superpowers` and `docs/review`.
- `examples/ktor-ocr-api` compiles and tests pass.
- Root and module README locale set are updated.
- Diagrams are generated and visually inspected.
- Examples workflow includes the new module.
- Step 6-R shows P0=0 and P1=0.
- PR body ends with `## DoD Status`.
