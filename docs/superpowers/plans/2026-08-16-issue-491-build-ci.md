# BUILD-1 CI 신뢰성 및 Gradle 10 호환성 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dependency Submission의 저장소 정책 불일치, native retry 관측 공백, 저장소 소유 Gradle 10 deprecation을 한 번의 BUILD-1 PR로 명시적이고 검증 가능한 계약으로 정리한다.

**Architecture:** Dependency Submission은 저장소 변수 `DEPENDENCY_GRAPH_POLICY`를 단일 정책 입력으로 사용하고, 정책이 비활성인 경우 실패를 숨기지 않고 job summary에 의도적 비활성 상태를 기록한다. Native Gradle retry는 공통 shell helper가 최초 시도·재시도 횟수·최종 결과를 `$GITHUB_STEP_SUMMARY`에 기록하며, Gradle Kotlin DSL은 deprecated delegate/API를 직접 사용하지 않도록 additive한 문법 치환만 수행한다. 기존 Vips unopted fixture의 의도적 컴파일 실패 계약은 제거하지 않고 opted fixture 성공 검증과 분리한다.

**Tech Stack:** GitHub Actions YAML, Bash, Gradle 9.7 Kotlin DSL, Kotlin 2.4, JDK 25, `actionlint`, Gradle test/compile tasks.

---

### Task 1: 정책 실패를 명시적 Dependency Submission 결과로 바꾸기

**Files:**
- Modify: `.github/workflows/dependency-submission.yml`
- Test: `.github/workflows/dependency-submission.yml` with `actionlint`

- [x] **Step 1: Add a policy job with a single repository-variable input**

`DEPENDENCY_GRAPH_POLICY`가 `enabled`일 때만 snapshot submission을 실행하고, 값이 없거나 `disabled`이면 성공한 policy job summary에 “repository dependency graph disabled by policy; snapshot submission skipped”를 기록한다. 기존 `contents: write` 권한과 push/workflow_dispatch trigger는 유지한다. `dependency-submission` job은 policy job에 `needs`를 걸고 output이 `true`인 경우에만 실행한다.

- [x] **Step 2: Validate workflow syntax and policy branches**

Run:

```bash
actionlint .github/workflows/dependency-submission.yml
```

Expected: exit 0. YAML에는 disabled policy와 enabled submission 양쪽의 `if` 조건, summary 문구, `github.repository` 외부 입력이 명시되어야 한다.

- [x] **Step 3: Commit the policy-only change**

```bash
git add .github/workflows/dependency-submission.yml
git commit -m "ci: dependency graph 정책과 submission 상태를 분리한다"
```

### Task 2: Native Gradle retry 결과를 summary에 기록하기

**Files:**
- Create: `.github/scripts/gradle-retry.sh`
- Modify: `.github/workflows/ci.yml` (Vips Java 21/25 native test jobs)
- Test: `.github/scripts/gradle-retry.sh` with fake success/failure commands

- [x] **Step 1: Write the retry helper contract test**

Use a temporary `GITHUB_STEP_SUMMARY` and fake commands that fail once then succeed, and always fail. Assert the summary contains 최초 시도 결과, 총 시도 횟수, 최종 결과, and that the helper exits with the wrapped command's final status.

- [x] **Step 2: Implement the helper**

The helper must use `set -Eeuo pipefail`, default to five attempts and thirty-second delay, preserve the wrapped command exit code, avoid retrying after the final attempt, and append a Korean summary containing the command, first-attempt status, attempts used, and final status. It must work when `$GITHUB_STEP_SUMMARY` is absent by writing no summary and still returning the command status.

- [x] **Step 3: Replace only Vips native retry loops**

In the Java 21 and Java 25 Vips jobs, call the helper after checkout/setup instead of duplicating `for attempt in 1 2 3 4 5`. Keep existing Gradle command flags, `maxParallelForks`, native isolation, and job ordering unchanged.

- [x] **Step 4: Run shell/workflow validation**

```bash
bash -n .github/scripts/gradle-retry.sh
actionlint .github/workflows/ci.yml
```

Expected: exit 0; fake retry tests show one first-attempt failure followed by success and an always-failing command returns non-zero after five attempts.

- [x] **Step 5: Commit retry observability**

```bash
git add .github/scripts/gradle-retry.sh .github/workflows/ci.yml
git commit -m "ci: native Gradle 재시도 결과를 summary에 기록한다"
```

### Task 3: Repository-owned Gradle 10 deprecation 제거

**Files:**
- Modify: `build.gradle.kts`
- Modify: `bom/build.gradle.kts`
- Test: Gradle configuration/deprecation scan and affected module tests

- [x] **Step 1: Replace deprecated property delegates**

Replace `val projectGroup: String by project`, `baseVersion`, and `snapshotVersion` with `project.property("...") as String` (or the equivalent provider API where the value is optional) without changing the resolved values.

- [x] **Step 2: Replace deprecated task delegates**

Replace `by registering(...)` and `by tasks.registering(...)` with explicit `register<TaskType>(name)` providers. Preserve task names, publication artifacts, Detekt finalization, and report inputs.

- [x] **Step 3: Replace BOM Project-object dependency notation**

Use the Gradle `DependencyHandler.project(path)` notation in `bom/build.gradle.kts` instead of passing a `Project` object to `api`, preserving the same published constraint coordinates.

- [x] **Step 4: Verify warning ownership and behavior**

```bash
./gradlew help --warning-mode all --no-daemon --console=plain 2>&1 | tee /tmp/issue-491-gradle-warnings.log
./gradlew :bluetape4k-images-vips-api:test --no-daemon --console=plain
./gradlew :bluetape4k-image-bom:tasks --no-daemon --console=plain
```

Expected: repository-owned delegate/project-notation warnings are absent; the known upstream Detekt `ReportingExtension.file(String)` warning is recorded as external plugin debt if it remains; targeted tasks pass.

- [x] **Step 5: Commit Gradle compatibility changes**

```bash
git add build.gradle.kts bom/build.gradle.kts
git commit -m "build: Gradle 10 호환 Kotlin DSL API를 사용한다"
```

### Task 4: Vips opt-in fixture 경계와 CI 증거 고정

**Files:**
- Modify: `.github/workflows/ci.yml` (`test-images-vips-api` job)
- Preserve: `images-vips-api/build.gradle.kts` source-set/property boundary
- Test: opted fixture success and unopted fixture expected failure

- [x] **Step 1: Preserve the negative fixture contract**

Do not make `UnoptedVipsOptInFixture.kt` compile successfully. The unopted task must remain an explicit expected-failure check, while `compileOptedVipsOptInFixtureKotlin -PverifyVipsOptInFixtures` must be the positive clean-compile command.

- [x] **Step 2: Add an unambiguous CI verification step**

Add a step to `.github/workflows/ci.yml` after the `images-vips-api` test that invokes the opted task for the required success gate and runs the unopted task separately, captures its output, and asserts that the compiler reports `VipsIncubatingApi` opt-in failure. Do not combine both tasks in one command whose non-zero result obscures the positive fixture.

- [x] **Step 3: Verify both sides**

```bash
./gradlew :bluetape4k-images-vips-api:compileOptedVipsOptInFixtureKotlin \
  -PverifyVipsOptInFixtures --no-daemon --console=plain
if ./gradlew :bluetape4k-images-vips-api:compileUnoptedVipsOptInFixtureKotlin \
  -PverifyVipsOptInFixtures --no-daemon --console=plain; then
  echo "unopted fixture unexpectedly compiled" >&2
  exit 1
fi
```

Expected: opted task succeeds; unopted task fails with the required opt-in diagnostic.

- [x] **Step 4: Commit fixture verification boundary**

```bash
git add .github/workflows/ci.yml
git commit -m "test: Vips opt-in fixture 성공·실패 경계를 명시한다"
```

### Task 5: Full BUILD-1 verification and delivery evidence

**Files:**
- Modify: issue/PR body only after implementation evidence is fresh
- Test: all commands below

- [x] **Step 1: Run targeted and static checks**

```bash
actionlint .github/workflows/dependency-submission.yml .github/workflows/ci.yml
bash -n .github/scripts/gradle-retry.sh
./gradlew :bluetape4k-images-vips-api:test --no-daemon --console=plain
./gradlew :bluetape4k-images-vips-api:compileOptedVipsOptInFixtureKotlin \
  -PverifyVipsOptInFixtures --no-daemon --console=plain
git diff --check develop...HEAD
```

- [ ] **Step 2: Run independent reviewer and resolve findings**

Require an independent reviewer lane to classify P0-P3 findings, check workflow policy semantics, retry exit-code preservation, Gradle ABI/behavior parity, and the negative fixture contract. Resolve all P0/P1 findings before PR creation.

- [ ] **Step 3: Create the single BUILD-1 PR**

Use base `develop`, link Epic #510 and issue #491, write Korean PR prose, and end the body with `## DoD Status`. Include exact head SHA, test evidence, known external Detekt warning (if still present), and disabled dependency-graph policy semantics.

- [ ] **Step 4: Verify hosted CI and merge only after fresh approval**

Read back exact head, CI, reviews, threads, labels, assignee, milestone, and mergeability. Merge only after a separate fresh approval, then sync `develop`, rerun the affected module tests, close #491 and #510 only after DoD readback, and remove only the proven merged worktree/branch.

## Self-review

- Spec coverage: dependency submission policy, Gradle 10 warnings, native retry observability, and Vips opt-in fixture behavior each have a dedicated task and command.
- No intentional negative fixture is weakened; the positive and negative compiler checks are separated.
- External Detekt `ReportingExtension.file(String)` is not repository-owned; it remains a documented upstream warning unless the selected catalog upgrade is independently proven safe.
