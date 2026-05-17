# Snapshot Version Parameterization

Context: Central Portal releases should not require editing `gradle.properties`
only to remove `-SNAPSHOT`.

Decision: Keep `snapshotVersion=` empty by default and let
`publish-snapshot.yml` pass `-PsnapshotVersion=-SNAPSHOT`.

Outcome: `develop` stays release-ready, while snapshot publishing remains
explicit in the workflow command.

Release-prep outcome: `bluetape4k-*` dependencies now use formal release
versions, not `-SNAPSHOT`, before Central Portal deployment.
Name version aliases after BOM artifacts, such as `bluetape4k-bom` and
`bluetape4k-aws-bom`, instead of bare repository names.

Verification: `actionlint .github/workflows/publish-snapshot.yml`.

Future guard: Do not reintroduce `snapshotVersion=-SNAPSHOT` as the default in
`gradle.properties`.
