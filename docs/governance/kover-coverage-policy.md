# Kover Coverage Policy

## Current Status

`bluetape4k-image` aggregates Kover reports across image modules. No module
currently enforces a failing Kover threshold.

## Policy

Status: report-only transition.

Pure image processing code and native libvips/Panama variants have different
testability and platform constraints. Avoid one repository-wide threshold.

## Threshold Plan

- Gate the pure image module first after baseline measurement.
- Treat libvips Java 21/25 modules as integration-heavy and use a documented
  lower starting threshold.
- Keep benchmark modules out of production coverage gates.

## CI/Nightly Contract

Nightly coverage remains informational until module-level `koverVerify` bounds
are introduced.
