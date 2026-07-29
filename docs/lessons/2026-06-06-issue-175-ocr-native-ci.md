# Issue 175 OCR Native CI

## 배경

PR #174는 `images-ocr` host-native test가 GitHub Actions `ubuntu-24.04`에서 portable하지
않다는 점을 보여줬다. runner package set은 현재 Lept4J/Tess4J가 기대하는 native API
surface보다 오래된 Leptonica 계열을 제공한다.

## 결정

GitHub CI와 Nightly는 container-backed OCR gate로 유지하고, host-native OCR은
local/manual check로 문서화한다. library-name symlink로 문제를 덮지 않는다. 그것은
실패 위치를 load-time에서 symbol lookup으로 옮길 뿐이다.

## 결과

OCR job이 `-Docr.container.enabled=true`를 실행하므로 workflow는 더 이상 OCR job에 host
Tesseract를 설치하지 않는다. module README pair는 이제 `-Docr.enabled=true`가 호환되는
host Tesseract, language pack, Leptonica를 요구하고 CI/Nightly는 portable container gate를
사용한다고 명시한다.

## 유지할 검증

- workflow 수정 후 `actionlint`를 실행한다.
- push 전 `rg -n "\\'" .github/workflows`를 실행한다.
- `./gradlew :bluetape4k-images-ocr:test`를 실행한다.
- `./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true`를 실행한다.
- host-native `-Docr.enabled=true`는 Tesseract와 Leptonica가 호환된다고 확인된 장비에서만
  실행한다.

## 향후 방지책

나중에 host-native OCR CI를 복원한다면 먼저 runner의 Leptonica symbol set이 선택한
Lept4J/Tess4J 계열과 맞는지 증명한다. native runtime library를 source build하는 일은
빠른 workflow tweak이 아니라 별도 CI maintenance issue로 다룬다.
