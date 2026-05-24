# Snapshot Validation Line

## Context

After the previous release, snapshot validation needed the repository reopened
on the next development line while consuming matching upstream bluetape4k and
AWS snapshots.

## Decision

Set `baseVersion=0.1.3`, keep `snapshotVersion=` empty, and consume
`bluetape4k-bom:1.9.2-SNAPSHOT` plus
`bluetape4k-aws-bom:0.2.2-SNAPSHOT`.

## Outcome

The repository can publish `0.1.3-SNAPSHOT` through `publish-snapshot.yml`
without checking a snapshot suffix into `gradle.properties`.

## Verification

Pending in the snapshot validation train.
