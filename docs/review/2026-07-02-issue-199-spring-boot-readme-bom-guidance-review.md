# Issue #199 Spring Boot README BOM Guidance Review

## Scope

- Issue: #199, `docs: add BOM or versioned dependency guidance to images-spring-boot README pair`
- Files reviewed:
  - `images-spring-boot/README.md`
  - `images-spring-boot/README.ko.md`
- Review date: 2026-07-02

## Findings

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## Evidence

- EN/KO Spring Boot README dependency snippets now import `bluetape4k-image-bom:<version>` before declaring the versionless `bluetape4k-images-spring-boot` artifact.
- EN/KO README files also show the non-BOM fallback with `bluetape4k-images-spring-boot:<version>`.
- Artifact names were checked against `settings.gradle.kts`:
  - `bluetape4k-images-spring-boot`
  - `bluetape4k-image-bom`
- BOM usage was checked against `bom/README.md` and `bom/README.ko.md`.
- `git diff --check`: PASS.
- `rg -n '^implementation\\("io\\.github\\.bluetape4k\\.image:bluetape4k-images-spring-boot"\\)' images-spring-boot/README.md images-spring-boot/README.ko.md`: no standalone top-level dependency declarations remain.
- `rg -n "bluetape4k-image-bom:<version>|bluetape4k-images-spring-boot:<version>|bluetape4k-images-spring-boot\\\"\\)" images-spring-boot/README.md images-spring-boot/README.ko.md`: BOM-managed and explicit-version snippets are present in both README files.

## Residual Risk

- This PR does not rewrite root README explicit `0.3.0` release examples; those are a separate release-line documentation concern.
