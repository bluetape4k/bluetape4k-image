# Issue #491 BUILD-1 독립 검토

## 검토 범위

- 대상: `fix/issue-491-build-ci`
- 검토 기준 HEAD: `b90256b3a599c82582a6b2fe292126e9c0a5a79c`
- 연결: Epic #510, child #491, milestone `0.5.0`
- 범위: Dependency Submission 정책, native Gradle retry, Gradle 10 저장소 소유 DSL, Vips opt-in fixture CI 증거

## 독립 검토 결과

| 등급 | 결과 | 근거 및 처분 |
|---|---|---|
| P0 | 0건 | 차단 결함 없음 |
| P1 | 0건 | 초기 검토의 fixture summary 누락, retry summary 쓰기 실패 처리, JDK 주석, Detekt 범위 불명확성을 모두 수정하고 재검토에서 해소 |
| P2 | 1건 | PR 생성 전 hosted exact-head CI 증거 부재. 코드 결함이 아니며 PR 생성 후 CG-14에서 확인 |
| P3 | 0건 | 추가 조치 없음 |

## 주요 결정

- `DEPENDENCY_GRAPH_POLICY=disabled`는 repository Dependency graph가 꺼진 환경에서 snapshot submission을 실행하지 않고 policy job summary에 의도적 생략을 남긴다.
- `DEPENDENCY_GRAPH_POLICY=enabled`는 graph를 먼저 활성화해야 하며, 현재 저장소 설정을 코드에서 변경하지 않는다.
- Gradle 10 검증의 DoD는 저장소가 소유한 deprecated DSL/API 0건으로 고정한다. 남은 `ReportingExtension.file(String)` 경고는 Detekt 1.23.8 플러그인 내부 호출이며, plugin ID·report API·rule 설정을 포함하는 Detekt 2.x 전환은 별도 후속 child로 분리한다.
- Vips opted fixture의 성공과 unopted fixture의 `VipsIncubatingApi` 실패를 별도 단계로 실행하고 두 결과를 `$GITHUB_STEP_SUMMARY`에 기록한다.
- retry helper가 hosted summary 경로를 받았지만 기록하지 못하면, wrapped command가 성공했더라도 job을 실패시켜 관측 공백을 허용하지 않는다.

## 검증 근거

- `actionlint .github/workflows/dependency-submission.yml .github/workflows/ci.yml`: PASS
- `bash -n .github/scripts/gradle-retry.sh`: PASS
- `./gradlew :bluetape4k-images-vips-api:test --no-daemon --console=plain`: PASS
- `./gradlew :bluetape4k-images-vips-api:compileOptedVipsOptInFixtureKotlin -PverifyVipsOptInFixtures`: PASS
- unopted fixture task: exit 1 및 `VipsIncubatingApi` 진단 확인
- retry helper fail-once/always-fail/summary-write-failure 시나리오: PASS
- JNI Java 21 및 FFM Java 25 native module test: BUILD SUCCESSFUL; Java 21 로컬 환경에서는 선택적 native/golden 케이스가 SKIPPED됨
- `./gradlew help --warning-mode all`: 저장소 소유 warning 없음; 외부 Detekt 경고만 남음
- `git diff --check develop...HEAD`: PASS

## 잔여 증거

- PR 생성 후 exact head에서 hosted CI, live PR metadata, review thread, mergeability를 다시 읽는다.
- Dependency Submission enabled 경로는 repository setting이 활성화된 뒤 별도 hosted 검증이 필요하다.
- 이번 child는 Detekt 2.x 전환을 수행하지 않는다. 해당 migration은 plugin ID와 report/rule 계약 변화가 있어 별도 계획이 필요하다.

## Writer DoD

- SPW-01: PASS — 대상, 독자, 목적, exact SHA, issue/epic, 근거 명령을 고정했다.
- SPW-02: PASS — 범위, 등급별 처분, 결정, 검증, 잔여 증거를 포함했다.
- SPW-03: PASS — 한국어 기술 문체와 식별자·명령·진단 보존을 확인했다.
- SPW-04: PASS — 초기 리뷰 P1과 수정 commit, 재검토 결과를 source-to-disposition으로 대조했다.
- SPW-05: PASS — Markdown heading/table/list와 DoD를 read-back했다.
