# Issue #492 다중 페이지 TIFF OCR 릴리스 gate

## 대상

- Issue: #492
- Epic: #508
- Feature branch: `feat/issue-492-tiff-multipage-ocr`
- PR: 미정
- Merge base: `develop`
- Feature HEAD SHA: 미정
- Merge commit SHA: 미정

## 필수 CI 증적

- [ ] PR의 exact head SHA를 live GitHub에서 확인했다.
- [ ] `test-images-ocr` workflow URL: 미정
- [ ] `test-images-ocr` exact run ID/status: 미정
- [ ] `-Docr.container.enabled=true` 결과: 미정
- [ ] JUnit XML/artifact URL: 미정
- [ ] skipped matrix job이 없는지 확인했다.
- [ ] CI가 검증한 commit과 PR head가 일치한다.

## 로컬 검증

- [x] `./gradlew :bluetape4k-images-ocr:test --no-build-cache` — 29 passing, 6 pending
- [x] `./gradlew :bluetape4k-images:test --no-build-cache` — 675 passing, 18 pending
- [x] `./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.TiffMultiPageTesseractContainerOcrTest' -Docr.container.enabled=true --no-build-cache` — 1 passing
- [x] `./gradlew detekt --no-build-cache` — root task `NO-SOURCE`, successful
- [x] `git diff --check`
- [x] Java compile fixture와 `javap` public API gate — `build/issue-492-public-api.javap`
- [x] 테스트 결과 XML 경로와 pass/skip 수를 기록했다.

## Native release candidate gate

- [ ] 실행 SHA: 미정
- [ ] 명령:
  `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true --no-daemon`
- [ ] host Tesseract/훈련 데이터 버전: 미정
- [ ] 결과와 artifact 경로: 미정
- [ ] 실패 시 publish를 중단했다.

Native gate는 GitHub CI container gate와 별개의 수동 release-candidate 증적이다.
환경이 없으면 성공으로 추정하지 않고 `PENDING`으로 남긴다.

## Rollback 및 caller fallback

- [ ] failure-rate 또는 ABI gate 실패 시 이전 catalog/artifact pin을 확인했다.
- [ ] 기존 single-image `extractText`/`extractOcr` API로 되돌리는 caller 경로를 확인했다.
- [ ] 새 API의 `TiffMultiPageOcrFailureReason`을 retry/HTTP 정책에 매핑했다.
- [ ] payload, 파일 경로, tessdata 경로, native cause가 public error에 없는지 확인했다.

## 완료 판정

- [ ] exact-head CI success
- [ ] 독립 6-lens code review P0/P1 = 0
- [ ] README EN/KO 구조 parity
- [ ] assignee `debop`, milestone `0.5.0`, issue/PR label parity
- [ ] merge 전 fresh approval 확보
