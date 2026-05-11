# Kover Coverage Policy

## Current Status

`bluetape4k-image` aggregates Kover reports across image modules. No module
currently enforces a failing Kover threshold.

## Policy

Status: report-only transition.

Pure image processing code and native libvips/Panama variants have different
testability and platform constraints. Avoid one repository-wide threshold.

## Threshold Plan

- Treat Kover as a trend signal, not a build gate.
- Use Nightly XML reports and existing coverage artifact uploads to identify
  coverage regressions.
- Open a focused issue when a module needs coverage repair; do not introduce a
  failing threshold as the default enforcement mechanism.
- Keep benchmark modules out of production coverage gates.

## CI/Nightly Contract

Nightly coverage remains informational. Kover XML reports and existing coverage
artifact uploads are retained for visibility, but CI and Nightly must not fail
solely because a module is below a fixed coverage percentage unless a future
issue explicitly reintroduces that gate.
