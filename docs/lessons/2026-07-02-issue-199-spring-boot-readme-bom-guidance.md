# Issue #199 Spring Boot README BOM Guidance

## Context

The Spring Boot module README pair showed a versionless dependency declaration
without first importing the image BOM.

## Decision

Make the module README adoption path BOM-first, and also show an explicit
`<version>` fallback for consumers that do not import the BOM.

## Outcome

Copying only the Spring Boot module README dependency section now yields a
resolvable Gradle dependency path.

## Verification

- `git diff --check`
- Artifact-name search against `settings.gradle.kts`
- BOM usage search against `bom/README.md` and `bom/README.ko.md`

## Future Guard

Versionless module dependencies in README files must be adjacent to a BOM
import. If a README intentionally avoids the BOM, show the dependency with an
explicit `<version>` placeholder.
