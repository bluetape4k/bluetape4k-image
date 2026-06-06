# Issue #175 OCR Native CI Research

- Issue: [#175](https://github.com/bluetape4k/bluetape4k-image/issues/175)
- Date: 2026-06-06
- Scope: decide whether `images-ocr` should restore host-native OCR on GitHub
  Actions or keep that lane local/manual.

## Evidence

- PR #174 CI run `27026749188` failed on `ubuntu-24.04` before a library alias
  existed. Lept4J/JNA could not load the expected Leptonica library name.
- PR #174 CI run `27027373907` added an alias from `liblept.so.5` to
  `libleptonica.so`, then failed later with
  `pixAddMultipleBlackWhiteBorders` missing from `/lib/x86_64-linux-gnu/liblept.so.5`.
- Local `dependencyInsight` shows:
  - `net.sourceforge.tess4j:tess4j:5.19.0`
  - `net.sourceforge.lept4j:lept4j:1.24.0`
- Local `javap` against `lept4j-1.24.0.jar` shows
  `Leptonica1.pixAddMultipleBlackWhiteBorders(...)` is part of the native API
  registration surface.
- Ubuntu Noble publishes `liblept5` from Leptonica `1.82.0-3build4`.
- Lept4J release notes show recent runtime movement past Ubuntu Noble's
  package line: Lept4J 1.20 upgraded to Leptonica 1.85.0, 1.22 to 1.86.0, and
  1.23 to 1.87.0. The current 1.24.0 line inherits that newer native API
  expectation.
- Local macOS validation environment reports Tesseract 5.5.2 with
  Leptonica 1.87.0.

## Decision

Do not source-build Leptonica/Tesseract in CI for this issue. That would make the
OCR job slower and create a second native-runtime maintenance surface. Keep the
GitHub CI and Nightly OCR gate on `-Docr.container.enabled=true`, which runs the
portable Testcontainers smoke path. Keep `-Docr.enabled=true` as a local/manual
host-native Tess4J check for developer machines with compatible native libraries.

## Impact

- CI/Nightly behavior is aligned: both run the container-backed OCR gate.
- The host-native quickstart remains useful for local validation and documents
  the required native runtime compatibility.
- A future host-native CI restoration should start by choosing a maintained
  native runtime source: newer runner image, source-built Leptonica/Tesseract
  cache, or a Tess4J/Lept4J version line intentionally compatible with the
  runner package set.

## Sources

- Lept4J release notes: https://tess4j.sourceforge.net/lept4j.html
- Tess4J 5.19.0 metadata: https://mvnrepository.com/artifact/net.sourceforge.tess4j/tess4j/5.19.0
- Ubuntu Noble `liblept5`: https://launchpad.net/ubuntu/noble/arm64/liblept5
- Leptonica `pixAddMultipleBlackWhiteBorders` API reference: https://tpgit.github.io/Leptonica/pix2_8c.html
