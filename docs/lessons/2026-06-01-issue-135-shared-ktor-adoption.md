# 2026-06-01 - Issue 135 Shared Ktor Adoption

## Context

`bluetape4k-projects` 1.10.0 published the shared `bluetape4k-ktor-*`
modules. `bluetape4k-images-ktor` and `examples/ktor-image-api` still carried
local JSON/test-client setup and route parameter/error helpers.

## Decision

Use `bluetape4k-ktor-core` for shared JSON defaults, request-parameter parsing,
and `ApiErrorResponse` bad-request payloads. Use `bluetape4k-ktor-testing` for
JSON-aware test clients and status assertions. Keep image-specific CAPTCHA and
thumbnail route behavior in this repository.

## Outcome

The image Ktor module now depends on the shared Ktor core module, the quickstart
installs `installBluetape4kKtorCore`, and tests use the shared Ktor testing
helpers. Duplicate direct Ktor JSON/test dependencies were removed where the
shared modules already expose them.

## Verification

- `./gradlew :bluetape4k-images-ktor:test :ktor-image-api:test --no-daemon --no-configuration-cache --no-build-cache`

## Future Guard

When adopting shared bluetape4k modules in downstream repos, replace duplicated
helper code first, then trim direct dependency declarations only after the
targeted tests prove the shared module exposes the required API surface.
