# Issue #1 OCR research refresh

- 이슈: [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
- 날짜: 2026-06-05
- 범위: implementation spec 작성 전 기존 OCR handoff refresh.

## 결정

issue #1은 새 optional `bluetape4k-images-ocr` module의 Tesseract/Tess4J baseline에 집중한다. `bluetape4k-images`에는 OCR dependency를 추가하지 않는다.

PaddleOCR은 issue #1 범위 밖이다. PaddleOCR은 별도 runtime, model packaging, serving, hardware, CI concern을 가진 더 넓은 Python/model/document-AI stack이다. 후속 작업은 [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169)에서 추적한다.

## 업데이트된 source evidence

- 기존 repo-local research: `docs/superpowers/research/2026-05-29-issue-83-ocr-dependency-model-packaging-research.md`.
- 기존 lesson: `docs/lessons/2026-05-29-image-ai-research-gates.md`.
- GitHub issue #83은 research handoff를 연결한 comment와 함께 closed 상태다.
- Tess4J Maven metadata checked on 2026-06-05: latest/release `5.19.0`, last updated `20260527033916`.
- Tess4J GitHub release checked on 2026-06-05: `tess4j-5.19.0`, Apache-2.0, published 2026-05-27.
- Tesseract GitHub release checked on 2026-06-05: `5.5.2`, Apache-2.0, published 2025-12-26.
- Tesseract installation docs는 engine package와 language `traineddata` package를 별도로 설치한다고 설명한다.
- `tesseract-ocr/tessdata`는 Apache-2.0이며 trained model을 제공한다.
- PaddleOCR GitHub release checked on 2026-06-05: `v3.6.0`, Apache-2.0, published 2026-05-28.

## repository 적합성

- 기존 optional native/runtime dependency는 이미 core module 밖에 있다. 예시는 `bluetape4k-images-vips-java21`, `bluetape4k-images-vips-java25`다.
- 새 module이 추가되면 root README와 repo-local `AGENTS.md` module list를 갱신해야 한다.
- root README visual asset에는 현재 module overview, module chart, architecture diagram이 포함되어 있다. OCR 추가는 이 asset을 stale하게 만들므로 diagram 작업은 issue #1 범위에 포함한다.
- `settings.gradle.kts`, BOM constraint, CI path filter, CI job, Nightly job, coverage artifact, README module table이 모두 새 module을 포함해야 한다.

## API evidence

Tess4J 5.19.0은 첫 구현에 필요한 surface를 제공한다:

- `ITesseract.doOCR(BufferedImage)`
- `ITesseract.setDatapath(String)`
- `ITesseract.setLanguage(String)`
- `ITesseract.setOcrEngineMode(int)`
- `ITesseract.setPageSegMode(int)`
- `Tesseract`는 기본적으로 `TESSDATA_PREFIX`를 읽고, missing datapath 또는 language data를 initialization 중 validate한다.

이 surface는 common single-image path에서 temporary file 없이 `ImmutableImage.awt()` 기반 구현을 지원한다.

## test 및 CI 전략

2026-06-06 issue #175 업데이트: host-native CI lane은 더 이상 기본 GitHub Actions 전략이 아니다. Ubuntu 24.04는 현재 Lept4J/Tess4J native symbol surface보다 오래된 Leptonica package line을 제공한다. CI와 Nightly는 이제 portable gate인 `-Docr.container.enabled=true`를 사용하고, `-Docr.enabled=true`는 local/manual host-native check로 남긴다.

세 test level을 사용한다:

1. API validation, option validation, suspend wrapper behavior를 위한 fake `OcrEngine` 기반 unit test. 이는 일반 local 및 CI test path에서 실행한다.
2. `-Docr.enabled=true`로 gate하는 host-native Tess4J integration test. CI가 이 lane을 실행할 때는 `tesseract-ocr`, `tesseract-ocr-eng`, `tesseract-ocr-kor`, `tesseract-ocr-jpn`을 먼저 설치할 수 있다.
3. `-Docr.container.enabled=true`로 gate하는 Testcontainers OCR CLI smoke test. 이는 containerized Tesseract runtime과 language data를 검증하지만, 별도 container가 host JVM에 native library를 load할 수 없으므로 host-native Tess4J integration test를 대체하지 않는다.

현재 agent environment에서는 local Docker를 사용할 수 없다. 따라서 Testcontainers lane은 CI-capable하고 local에서는 명시적인 skip reason과 함께 skip 가능하도록 설계해야 한다.

## follow-up scope guard

PaddleOCR backend evaluation은 [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169)로 생성했다. issue #1을 PaddleOCR integration으로 확장하지 않는다.

Testcontainers image build/runtime reliability가 예상보다 커지면 API/module baseline을 지연하지 말고, host-native Tess4J baseline을 유지한 채 별도 CI hardening issue를 등록한다.

## Step 1-R DoD input for spec

- `bluetape4k-images-ocr`를 published module로 추가한다.
- repo-local version catalog에 `tess4j = "5.19.0"`와 `tess4j = { module = "net.sourceforge.tess4j:tess4j" }`를 추가한다.
- public API는 `OcrEngine`, `TesseractOcrEngine`, `OcrOptions`, `OcrResult`, `extractText`, `suspendExtractText`를 노출해야 한다.
- `traineddata`는 기본적으로 external로 유지하고 `TESSDATA_PREFIX`와 명시적인 `tessdataPath`를 문서화한다.
- consumer가 OCR artifact를 추가해야 opt in되도록 extension function은 OCR module package에 둔다.
- README/README.ko, module README/README.ko, root diagram/chart, CI, Nightly, BOM, repo-local `AGENTS.md`를 갱신한다.
