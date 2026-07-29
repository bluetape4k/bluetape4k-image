# Issue 172 설계 — Spring Boot OCR 예제

## 배경

Issue #172는 기존 `images-ocr` OCR API를 application endpoint로 노출하는 Spring Boot 예제를
요구한다. 이는 core OCR 작업의 후속이며, 새 OCR backend 추가가 아니라 application wiring에 초점을
유지해야 한다.

현재 repository 증거:

- `examples/spring-boot-image-api`는 이미 기대되는 example 형태를 정의한다. non-published Gradle module,
  compact Spring Boot 4 application, MockMvc integration test, bilingual README file, README diagram,
  `.github/workflows/Examples.yml` coverage가 포함된다.
- `images-ocr`는 `OcrEngine`, `OcrOptions`, `OcrResult`, `OcrException`,
  `ImmutableImage.extractText`, `ImmutableImage.suspendExtractText`를 노출한다.
- `images-ocr` native test는 `-Docr.enabled=true` 또는 `-Docr.container.enabled=true`로 gate되므로,
  항상 실행되는 example test는 host Tesseract를 요구하면 안 된다.
- 기존 example diagram은 `docs/scripts/generate-example-readme-diagrams.py`가
  `docs/images/readme-diagrams` 아래에 생성한다.

## 목표

1. `examples/spring-boot-ocr-api` 아래에 실행 가능한 Spring Boot 4 OCR quickstart를 추가한다.
2. `images-ocr`를 통해 text를 추출하는 multipart image endpoint를 제공한다.
3. test에서 fake `OcrEngine`을 주입해 native Tesseract 없이도 local test를 deterministic하게 유지한다.
4. 실제 local 실행에 필요한 native Tesseract와 traineddata 요구사항을 문서화한다.
5. 예제를 Gradle, root docs, repo guidance, Examples workflow에 등록한다.
6. scenario, top-down layered architecture, request sequence를 설명하는 README diagram asset을 추가한다.

## 비목표

- 새 OCR backend를 추가하지 않는다.
- PaddleOCR 또는 external service를 평가하지 않는다.
- Ktor 예제는 추가하지 않는다. issue #173이 해당 후속 작업을 소유한다.
- production authentication, rate limiting, file persistence, queueing을 추가하지 않는다.
- 일반 CI에서 실제 host-native OCR을 요구하지 않는다.

## 설계 선택지

### 선택지 A — 새 Spring Boot OCR API 예제 module

`examples/spring-boot-ocr-api`를 만들고 다음 항목을 포함한다.

- `POST /api/ocr` multipart endpoint.
- comma 또는 plus로 구분된 Tesseract language code를 받고 기본값이 `eng`인 query parameter
  `languages`.
- local traineddata override를 위한 optional application property `example.ocr.tessdata-path`.
- `Dispatchers.IO`에서 multipart bytes를 읽고, 이를 `ImmutableImage`로 변환하며, `OcrOptions`를
  만든 뒤 `suspendExtractText`를 호출하는 `SpringBootOcrService`.
- 기본값이 `TesseractOcrEngine`인 `OcrEngine` bean.
- fake engine으로 `OcrEngine`을 override하는 test.

결정: 채택한다. 이 방식은 OCR 구현을 `images-ocr`에 유지하면서 issue와 기존 Spring Boot image example에
가장 잘 맞는다.

### 선택지 B — 기존 `spring-boot-image-api` 확장

`examples/spring-boot-image-api`에 `/api/ocr`을 추가한다.

결정: 거부한다. 기존 예제는 local storage와 thumbnailing에 초점이 있다. OCR을 섞으면
`images-ocr` quickstart가 묻히고 native setup 문서의 초점도 약해진다.

### 선택지 C — upload 대신 file path endpoint 제공

`path` parameter를 제공하고 server가 local image file을 읽게 한다.

결정: 기본 예제로는 거부한다. multipart endpoint가 web quickstart에 더 안전하며 path exposure를
가르치지 않는다. file-path OCR은 나중에 필요하면 local CLI 또는 advanced workshop topic으로 다룰 수 있다.

## API 계약

### `POST /api/ocr`

요청:

- content type: `multipart/form-data`.
- part `file`: image bytes.
- query `languages`: optional string이며 `,` 또는 `+`로 split한다. 기본값은 `eng`이다.

설정:

- `example.ocr.tessdata-path`: local run을 위해 `OcrOptions.tessdataPath`에 전달하는 optional host path.
- request는 caller-controlled tessdata path를 받지 않는다.

검증:

- `file`은 비어 있으면 안 된다.
- `file.contentType`은 `image/jpeg`, `image/png`, `image/webp`, `image/gif` 중 하나여야 한다.
- parsing된 모든 language token은 blank가 아니어야 한다.

응답:

```json
{
  "text": "recognized text",
  "languages": ["eng"],
  "characterCount": 15
}
```

오류 매핑:

- caller validation failure는 HTTP 400과 `{ "error": "bad_request", "message": "..." }`를 반환한다.
- `OcrException` failure는 HTTP 503과 `{ "error": "ocr_unavailable", "message": "..." }`를 반환한다.
  이렇게 native Tesseract misconfiguration을 request validation과 명확히 분리한다.

## 아키텍처

README architecture diagram은 top-down layered 구조여야 한다.

1. Client Layer: curl 또는 MockMvc.
2. Spring Web Layer: `OcrApiController`.
3. Application Layer: `SpringBootOcrService`.
4. OCR Library Layer: `ImmutableImage`와 `suspendExtractText`.
5. Native Runtime Layer: Tess4J와 host Tesseract traineddata.

diagram은 source-derived relationship을 보여야 하며, 예제가 새 OCR engine을 구현한다고 암시하지 않아야 한다.

## 테스트 전략

- `examples/spring-boot-image-api`와 맞춰 `@SpringBootTest`, `@AutoConfigureMockMvc`를 사용한다.
- test에서 in-memory PNG 또는 JPEG fixture를 생성한다.
- 요청된 `OcrOptions`를 기록하고 deterministic `OcrResult`를 반환하는 fake bean으로 `OcrEngine`을 override한다.
- 다음을 검증한다.
  - successful multipart request가 recognized text, parsed languages, character count를 반환한다.
  - unsupported content type이 400을 반환한다.
  - fake `OcrException`이 503을 반환한다.
- 기본 test task에는 `tesseract`나 traineddata가 필요하지 않아야 한다.

## 문서화와 등록

- 새 예제용 `README.md`와 `README.ko.md`를 추가한다.
- PNG diagram만 embed한다. 대응되는 SVG, Graphviz `.dot`, `.plain`, `-graphviz.svg`,
  `-graphviz.png` evidence는 유지한다.
- root `README.md`와 `README.ko.md`의 examples list를 업데이트한다.
- repo-local `AGENTS.md` examples table을 업데이트한다.
- `settings.gradle.kts`에 새 module을 추가한다.
- `.github/workflows/Examples.yml` matrix와 path를 업데이트해 `:spring-boot-ocr-api:test`가
  다른 example과 함께 실행되게 한다.

## 인수 기준

- `./gradlew projects`가 `:spring-boot-ocr-api`를 보여준다.
- `./gradlew :spring-boot-ocr-api:test`가 host-native OCR setup 없이 통과한다.
- `.github/workflows/Examples.yml`이 `actionlint`를 통과한다.
- README diagram generator가 새 asset을 render하고 deterministic gate evidence를 출력한다.
- README link가 대응 SVG source를 가진 PNG asset으로 resolve된다.
- `git diff --check`가 통과한다.
- Step 6-R 7-tier review에서 `P0 = 0`, `P1 = 0`이다.
- PR body가 local validation, review evidence, issue #172, milestone `0.3.0`, Step DoD status를 기록한다.

## 위험과 완화

- CI에서 native Tesseract를 사용할 수 없다: test는 fake `OcrEngine`을 주입하고, README는 실제 runtime 설치를 설명한다.
- 예제가 production 형태로 커진다: auth, persistence, queue, rate limit은 scope 밖으로 유지하고,
  production concern이 필요하면 follow-up issue를 만든다.
- OCR failure 의미가 불명확하다: request validation은 400으로, OCR runtime/configuration failure는 503으로 매핑한다.
- README diagram이 시각적으로 regress한다: 기존 generator를 확장하고 PR 전 rendered PNG를 개별 검토한다.
