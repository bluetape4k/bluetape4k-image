## Context

`Examples`, `CI`, and `Nightly` failed on GitHub-hosted Ubuntu runners before
tests started. The failing jobs could not read `1.11.0-SNAPSHOT` Maven metadata
from Central snapshots and returned HTTP 403, while sibling jobs and local
checks could still resolve the same artifacts.

## Decision

Keep public anonymous snapshot resolution as the default, but let scheduled,
manual, and same-repository workflow runs pass `CENTRAL_USERNAME` and
`CENTRAL_PASSWORD` into Gradle. The Gradle repository config only enables Basic
auth when both values are present. Cache changing snapshot modules for one day
and do not pass `--refresh-dependencies` in routine CI, Examples, or Nightly
jobs; forcing refresh on every attempt bypasses the cache and repeatedly hits
Central snapshot metadata endpoints. Matrix or parallel builds should reduce
concurrent Central snapshot metadata requests. When Central connect timeouts
consume several minutes per Gradle attempt, the workflow job timeout must be
long enough to let the retry loop finish.

## Outcome

Local builds and PRs without secrets continue to use anonymous Central snapshot
access. Repository workflows with Central credentials can avoid runner-specific
403 failures when resolving upstream bluetape4k snapshots. Reusing changing
module metadata for one day prevents each retry from forcing a fresh Central
snapshot metadata request. Serializing Examples matrix jobs and reducing build
worker parallelism avoids amplifying Central snapshot metadata contention.
Longer Gradle job timeouts prevent a recoverable Central connect timeout from
cancelling the workflow before later attempts can run.

## Verification

- `actionlint .github/workflows/ci.yml .github/workflows/Examples.yml .github/workflows/nightly-tests.yml`
- `./gradlew help --no-daemon`
- `CENTRAL_USERNAME=dummy CENTRAL_PASSWORD=dummy ./gradlew help --no-daemon`
- `./gradlew :basic-processing:test --no-configuration-cache --no-daemon`

## Future Guidance

If a workflow consumes unreleased upstream bluetape4k snapshots and fails with
Central snapshot metadata 403 on GitHub runners, first check whether the
workflow passes Central credentials into Gradle. If credentials are present but
jobs still fail, check whether `--refresh-dependencies` is forcing every retry
to re-query Central instead of using the one-day changing-module cache. If only
some parallel jobs fail, reduce concurrent snapshot metadata resolution before
adding more retries. If logs show `Connect timed out`, check whether the job
timeout is shorter than the retry loop's worst case.
