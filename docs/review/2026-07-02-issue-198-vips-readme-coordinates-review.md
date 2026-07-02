# Issue #198 VIPS README Coordinates Review

## Scope

- Issue: #198, `docs: fix stale vips module dependency coordinates in README pairs`
- Files reviewed:
  - `images-vips-java21/README.md`
  - `images-vips-java21/README.ko.md`
  - `images-vips-java25/README.md`
  - `images-vips-java25/README.ko.md`
- Review date: 2026-07-02

## Findings

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## Evidence

- Java 21 EN/KO README direct dependency examples now use `<version>`.
- Java 21 EN/KO README BOM examples now use `<version>`, matching the BOM README convention.
- Java 25 EN/KO README direct dependency examples now use `<version>`.
- Root README and BOM README guidance remain conceptually consistent: consumers either use the explicit stable release line in root examples or import the BOM with a caller-supplied version placeholder.
- `git diff --check`: PASS.
- `rg -n "1\\.7\\.0" images-vips-java21/README.md images-vips-java21/README.ko.md images-vips-java25/README.md images-vips-java25/README.ko.md README.md README.ko.md bom/README.md bom/README.ko.md`: no matches.
- `rg -n "bluetape4k-images-vips-(java21|java25).*1\\.7\\.0|1\\.7\\.0.*bluetape4k-images-vips" .`: no matches.

## Residual Risk

- This PR intentionally does not rewrite all root README release examples from explicit versions to placeholders; that broader version-guidance work belongs to separate documentation issues.
