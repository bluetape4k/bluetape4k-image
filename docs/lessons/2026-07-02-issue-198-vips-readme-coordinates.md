# Issue #198 VIPS README Coordinates

## Context

The Java 21 and Java 25 VIPS module README pairs documented `1.7.0` dependency
coordinates, while the image repository had moved to a different release line.

## Decision

Use `<version>` placeholders in module-level dependency examples instead of
hard-coding a release number that can silently drift from `gradle.properties`
and the BOM README.

## Outcome

The VIPS Java 21 and Java 25 README pairs now match the repository's module
README convention, and the Java 21 BOM snippet matches the BOM README usage.

## Verification

- `git diff --check`
- Stale `1.7.0` searches across the changed VIPS README pairs, root README, and BOM README

## Future Guard

For module README dependency snippets, prefer `<version>` or BOM-managed
coordinates unless the document is explicitly showing a release-specific copy
snippet.
