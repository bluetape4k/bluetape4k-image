# Issue 216 Structured OCR

## 배경

Issue #216은 기존 plain-text `extractText` helper의 source compatibility를 유지하면서
`bluetape4k-images-ocr`에 structured OCR extraction을 추가했다.

## 결정

- Tess4J type은 internal adapter boundary 뒤에 둔다.
- page, block, line, word metadata는 serializable bluetape4k API value로 model한다.
- missing/invalid confidence와 bounding box는 `null`로 다루고 placeholder value로 강제하지
  않는다.
- PaddleOCR/GPU/model-download adoption은 이 module 밖에 두고 #169를 통해 계속 추적한다.

## 검증

- always-on test는 host Tesseract가 아니라 deterministic fake `TesseractClient` fixture를
  사용하므로 일반 CI가 structured output과 region filtering을 검증할 수 있다.
- Host/native와 container Tesseract smoke test는 opt-in gate로 유지한다.

## 향후 방지책

OCR structure를 확장할 때는 `README.md`와 `README.ko.md`를 함께 갱신하고 public bluetape4k
API에서 Tess4J class를 노출하지 않는다.
