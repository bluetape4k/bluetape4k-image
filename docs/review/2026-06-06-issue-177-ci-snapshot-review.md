# Issue 177 CI Snapshot Review

## Scope

- PR: #178
- Issue: #177
- Files reviewed:
  - `.github/workflows/ci.yml`
  - `.github/workflows/Examples.yml`
  - `.github/workflows/nightly-tests.yml`
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `docs/lessons/2026-06-06-issue-177-central-snapshot-auth.md`

## Findings

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## Evidence

- Failed PR run 27047331885 showed `CI / Build (compile only)` did not receive
  `CENTRAL_USERNAME` or `CENTRAL_PASSWORD`, then failed on Central snapshot
  metadata HTTP 403.
- Failed PR run 27047331894 showed `Examples / spring-boot-image-api` did
  receive Central secrets but still failed under parallel Examples matrix
  execution, while sibling example jobs passed.
- Failed PR run 27047765648 showed `Examples / ktor-image-api` timed out after
  repeated Central snapshot metadata connect timeouts; the same command passed
  locally.
- Failed PR runs 27048363331 and 27048363343 showed that passing Central
  credentials was not sufficient while workflow commands forced
  `--refresh-dependencies`; each retry still re-queried Central snapshot
  metadata and repeated HTTP 403 failures.
- Examples and Nightly Gradle-heavy job timeouts were raised so the five-attempt
  retry loop is not cancelled before later attempts can run.
- Changing modules now cache for one day, and CI, Examples, and Nightly no
  longer pass `--refresh-dependencies` in routine Gradle commands.
- `actionlint .github/workflows/ci.yml .github/workflows/Examples.yml .github/workflows/nightly-tests.yml`: PASS
- `git diff --check`: PASS
- `rg -n --fixed-strings "\\'" .github/workflows`: no matches
- `./gradlew :basic-processing:test --no-configuration-cache --no-daemon`: PASS
- `./gradlew :spring-boot-image-api:test --no-configuration-cache --no-daemon`: PASS
- `./gradlew :ktor-image-api:test --no-configuration-cache --no-daemon`: PASS
- `./gradlew build -x test --max-workers=1 --no-configuration-cache`: PASS

## Verdict

Gate passes with P0=0 and P1=0. The change is scoped to workflow reliability
and optional Central snapshot repository credentials. Merge should wait for the
fresh PR CI and Examples checks on the updated commit.
