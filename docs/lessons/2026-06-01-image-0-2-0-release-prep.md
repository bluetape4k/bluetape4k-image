# Image 0.2.0 Release Prep

## Context

The `0.2.0` milestone has no open issues, but `CHANGELOG.md` did not contain
the already released `0.1.2` section. The release line also still referenced an
internal `bluetape4k-aws-bom:0.2.2-SNAPSHOT` artifact.

## Decision

Backfill the missing `0.1.2` changelog section, add the `0.2.0` release notes,
set `baseVersion=0.2.0`, and use the public stable `bluetape4k-aws-bom:0.3.0`
artifact before any stable tag is created.

## Outcome

Release metadata and changelog coverage now match the completed milestone. The
`0.2.0` API cleanup also removes compatibility shims that were explicitly
deprecated for this minor release.

## Verification

Run dependency resolution, targeted image module tests, CI, and a fresh Nightly
on the release-prep commit before dispatching the stable release workflow.

## Future Notes

Do not prepare a stable image tag while any internal bluetape4k dependency still
uses a `*-SNAPSHOT` version.
