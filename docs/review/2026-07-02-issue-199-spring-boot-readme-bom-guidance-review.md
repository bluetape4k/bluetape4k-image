# Issue #199 Spring Boot README BOM Guidance 검토

## 범위

- 이슈: #199, `docs: add BOM or versioned dependency guidance to images-spring-boot README pair`
- 검토 파일:
  - `images-spring-boot/README.md`
  - `images-spring-boot/README.ko.md`
- 검토일: 2026-07-02

## 발견 사항

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## 근거

- EN/KO Spring Boot README dependency snippets now import `bluetape4k-image-bom:<version>` before declaring the versionless `bluetape4k-images-spring-boot` artifact.
- EN/KO README files also show the non-BOM fallback with `bluetape4k-images-spring-boot:<version>`.
- Artifact names were checked against `settings.gradle.kts`:
  - `bluetape4k-images-spring-boot`
  - `bluetape4k-image-bom`
- BOM usage was checked against `bom/README.md` and `bom/README.ko.md`.
- `git diff --check`: PASS.
- `rg -n '^implementation\\("io\\.github\\.bluetape4k\\.image:bluetape4k-images-spring-boot"\\)' images-spring-boot/README.md images-spring-boot/README.ko.md`: no standalone top-level dependency declarations remain.
- `rg -n "bluetape4k-image-bom:<version>|bluetape4k-images-spring-boot:<version>|bluetape4k-images-spring-boot\\\"\\)" images-spring-boot/README.md images-spring-boot/README.ko.md`: BOM-managed and explicit-version snippets are present in both README files.

## 남은 위험

- 이 PR은 root README의 명시적 `0.3.0` release example을 다시 쓰지 않는다. 그보다 넓은 release-line documentation concern은 별도 문서 issue의 책임이다.
