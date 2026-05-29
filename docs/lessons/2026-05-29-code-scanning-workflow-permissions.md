# Code scanning workflow permissions

## Context

GitHub CodeQL reported `actions/missing-workflow-permissions` alerts for release,
snapshot publish, and nightly workflow jobs.

## Decision

Declare a workflow-level `contents: read` baseline for jobs that check out source,
then override token-free aggregation or resolution jobs with `permissions: {}`.
Keep release creation on a job-level `contents: write` permission because it calls
`gh release create`.

## Outcome

The workflow token boundary is explicit and least-privilege oriented without
changing Gradle, publish, or release behavior.

## Verification

Run `actionlint` on the touched workflows and inspect the YAML diff before PR.

## Future guard

When adding a workflow or job, declare either workflow-level permissions or a
job-level override at the same time.
