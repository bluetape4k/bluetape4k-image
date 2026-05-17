# Non-published module BOM filter

## Context

The image benchmark module should remain a local performance tool, not a BOM
constraint or Central Portal artifact.

## Decision

Use a normalized non-published module filter for examples, demos, and
benchmarks. Also keep Spring dependency-management POM customization enabled so
Central validation receives explicit dependency versions.

## Outcome

`bluetape4k-images-benchmark` is excluded from BOM constraints, NMCP
aggregation, publication/signing setup, and coverage aggregation.

## Verification

- `./gradlew clean generatePomFileForBluetapeImagePublication --no-daemon --no-configuration-cache --no-build-cache`
- Generated BOM POM scan found no `examples`, `demo`, or `benchmark` entries.
