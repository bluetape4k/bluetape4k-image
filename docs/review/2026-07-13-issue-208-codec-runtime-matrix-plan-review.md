# Issue #208 Codec/Runtime Matrix Plan 검토

- 날짜: 2026-07-13
- Artifact: `docs/superpowers/plans/2026-07-13-issue-208-codec-runtime-matrix-plan.md`
- Artifact SHA-256: `caeff9e8ff24d825f9250efcb26378279d9f5ba573850b784ff6c1243f981341`
- Artifact 종류: implementation plan
- Review mode: 여섯 개의 격리된 read-only role lens와 main integration

## 초기 발견 사항

| Priority | 관점 | 발견 사항 | 수정 |
|---|---|---|---|
| P1 | Performance | default/focused/direct-profiler protocol이 drift될 수 있었고, experimental profiler가 ineligible direction을 포함했으며, chart key가 불완전했다. | class/config/CLI protocol parity를 고정하고, direction eligibility에서 profiler regex를 derive하며, exact latency/allocation cell equality와 전체 comparability key를 요구했다. |
| P1 | Stability | Capability와 experimental preparation이 sibling dependency였고, Java 21 `N/A`가 terminal cell로 확장되지 않았으며, 실패 attempt에 durable lineage test가 없었다. | output-provider chain을 강제하고, native artifact를 거부하면서 `N_A` preflight를 모든 예상 backend cell로 확장했으며, immutable failed-attempt ledger와 one-way replacement lineage test를 추가했다. |
| P1 | Security | fixture/finalizer caller path가 trust boundary를 넓혔고, promotion race/symlink 규칙이 부족했다. | pinned working directory에서 fixed root를 derive하고, traversal/symlink를 거부하며, locked no-replace atomic promotion과 strict bounded JSON 및 full raw-tree leakage scan을 요구했다. |
| P1 | Operator/Ops | JMH jar selection이 stale output을 선택할 수 있었고, clean/prerequisite/rollback rule이 fail closed하지 않았다. | 정확한 `Jar.archiveFile` provider를 freshness/class/hash check와 함께 staging하고, prerequisite 및 run-path absence gate를 추가했으며, 새 superseding run을 통한 append-only correction을 정의했다. |
| P1 | Developer/API | concurrent draft가 catalog-version scope를 열었고, replacement lineage가 old ledger mutation을 암시했으며, dynamic JMH filtering에 concrete plugin API와 RED/GREEN functional proof가 없었다. | catalog 변경을 금지하고, lineage를 new-run-to-old-ledger 전용으로 만들었으며, 실제 kotlinx-benchmark 0.4.17 `JavaExec` parameter-file contract와 `onlyIf`/`setArgs`를 사용하고 TestKit RED/GREEN case를 추가했다. |
| P1 | User/caller | local result가 과도하게 일반화될 수 있었고, locale parity와 rerun command가 executable contract가 아니었다. | local-only/no-production-ranking statement, value/command/link parity ledger, fresh run ID, `supersedes`와 failed-attempt replacement용 Gradle-provider mapping test를 요구했다. |

P0 finding은 보고되지 않았다. 모든 P1 finding은 plan에서 수정했고 각 수정 뒤 affected lens를 재실행했다.

## 통합 결정

- `src/main`은 Vips-free 상태를 유지한다. selected runtime/image adapter와 native entrypoint는 `src/benchmark` 아래에 둔다.
- Backend fact는 하나의 run ID 아래 backend-keyed preflight, eligibility, size, latency, allocation artifact를 사용해 last-writer-wins evidence를 피한다.
- `prepareExperimentalCodecMatrixFixtures`는 capability task output provider를 소비하여 preflight -> stable fixtures -> capability -> experimental fixtures -> JMH 순서를 강제한다.
- 설치된 kotlinx-benchmark 0.4.17 source는 compiled benchmark source set을 담은 JMH jar가 target마다 하나임을 보여준다. 따라서 configuration-specific AVIF/HEIC jar는 사용할 수 없고 필요하지도 않다. 대신 plan은 exact target `Jar.archiveFile`을 staging하고 profiler 사용 전에 두 matrix class를 모두 검증한다.
- accepted 및 failed raw evidence는 immutable이다. `supersedes`와 `replaces-failed-attempt`는 new manifest에서 existing manifest hash로 향하는 forward reference만 만든다.
- Catalog/BOM/settings/CI/Nightly/public API 변경은 범위 밖이다. dependency-resolution failure는 inline catalog fix를 허용하지 않고 approval을 다시 연다.

## 최종 재실행 판정

| 관점 | P0 | P1 | 남은 P2/P3 | 판정 |
|---|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | PASS |
| Stability | 0 | 0 | 0 | PASS |
| Security | 0 | 0 | 0 | PASS |
| Operator/Ops | 0 | 0 | 0 | PASS |
| Developer/API | 0 | 0 | 0 | PASS |
| User/caller | 0 | 0 | 0 | PASS |
| Main integration | 0 | 0 | 0 | PASS |

Main integration은 승인된 spec을 기준으로 11개 task, exact class/task name, vips-free source-set boundary, TDD order, task isolation, evidence immutability, documentation parity, PR stop boundary를 모두 다시 확인했다.

Required checks: 7/7; N/A: 0; Blocked: 0.

최종 plan review convergence: **P0=0, P1=0**.

## Concurrent-Change Boundary

이 plan review 중 다른 process가 `7e405dc` (`feat: add codec matrix manifest model`)를 commit했다. 해당 implementation commit은 이 review의 mutation scope 밖이며, revert하지도 않았고 later implementation task가 통과한다는 evidence로 포함하지도 않았다. 이 artifact는 현재 plan만 승인한다. implementation verification은 여전히 RED/GREEN 및 review gate를 따라야 한다.
