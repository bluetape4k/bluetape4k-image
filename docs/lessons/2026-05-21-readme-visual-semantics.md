# README Visual Semantics

## Context

The root README visual overview already existed, but the module table and diagram labels did not fully reflect the current Gradle source layout. The BOM project was missing from the root module list, and several Vips labels used generated title casing rather than artifact names.

## Decision

Keep the first README visual as an English-only overview, but use semantic group names that explain role boundaries: BOM, core processing, Spring Boot 4, Vips API, Vips Java 21, Vips Java 25, and benchmarks. Keep install coordinates aligned with `projectGroup=io.github.bluetape4k.image` and current Gradle project names.

## Outcome

Updated the root README module tables, regenerated the root overview and module chart PNGs, normalized stale dependency examples, and corrected localized diagram alt text where labels were misleading.

## Verification

- `rsvg-convert` regenerated updated PNG assets from SVG sources.
- `xmllint --noout` passed for updated root SVG assets.
- `./gradlew -q projects` confirmed current projects: `:bluetape4k-image-bom`, `:bluetape4k-images`, Spring Boot, Vips API, Vips Java 21, Vips Java 25, and benchmark modules.
- Visual inspection confirmed centered labels and readable layout.

## Future Guidance

When README diagrams are refreshed, verify module names against `settings.gradle.kts` and artifact group against `gradle.properties` before rendering PNGs.
