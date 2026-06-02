# 2026-06-01 Open 0.3.0 Snapshot Line

## Context

`bluetape4k-image` `0.2.0` was retained for the previous dependencies release
train. The next catalog-train snapshot needs to consume the new projects and AWS
snapshot lines.

## Decision

Keep the committed `baseVersion=0.3.0` and `snapshotVersion=` empty, and align
direct BOM references to `bluetape4k-bom:1.11.0-SNAPSHOT` and
`bluetape4k-aws-bom:0.4.0-SNAPSHOT`.

## Outcome

The repository resolves against the next internal snapshot train.

## Verification

- `./gradlew help --no-daemon --console=plain` resolves the updated catalog.
