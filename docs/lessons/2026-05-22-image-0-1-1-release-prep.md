# Image 0.1.1 Release Prep

## Context

The 0.1.1 milestone has no open issues after the pre-stabilization API cleanup.
The release line needs to consume the published bluetape4k 1.9.0 ecosystem
artifacts before tagging.

## Decision

Prepare `bluetape4k-image` 0.1.1 as a release, not a snapshot, and align its
catalog with `bluetape4k-bom:1.9.0` and `bluetape4k-aws-bom:0.2.0`.

## Outcome

Release metadata, CHANGELOG, and WIP were updated for the 0.1.1 release gate.

## Verification

Pending release validation must include Gradle version checks, POM generation,
POM scans for snapshots/example artifacts, actionlint, and CI before tagging.

## Future Notes

Do not tag image releases while dependent ecosystem artifacts are still absent
from Maven Central public metadata.
