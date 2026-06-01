# AWS 0.3.1 Catalog Sync

## Context

`bluetape4k-dependencies` 1.2.0 final BOM preparation promoted
`bluetape4k-aws-bom` from `0.3.0` to `0.3.1` after the AWS release became
Maven Central-visible.

## Decision

Keep the image repo-local shared catalog aligned with the dependencies source
of truth and move `bluetape4k-aws-bom` to `0.3.1`.

## Outcome

The image catalog now consumes the same public stable AWS line that the final
dependencies 1.2.0 BOM will publish.

## Verification

- `sync-shared-versions.py --workspace .. --write --check --summary`
  updated the image catalog from `0.3.0` to `0.3.1`.
- Maven Central returned HTTP 200 for
  `io.github.bluetape4k.aws:bluetape4k-aws-bom:0.3.1`.

