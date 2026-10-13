# Issue #698 설계·계획 통합 리뷰

## 목표와 검토 범위

이 문서는 Issue #698의 공용 PNG/JPEG/WebP 바이트 시그니처 API 설계와 구현 계획을 여섯 관점으로 검토하고, 각 발견의 심각도·근거·조치·재검토 결과를 추적한다. 구현 전 계획과 현재 업로드 판별 경로를 검토했다. 구현 코드, 제품 README, 테스트, ABI dump, PR은 아직 만들거나 변경하지 않았다.

## 출처와 기준 시점 (SPW-01)

| 출처 | 확인 기준 | 사용 범위와 한계 |
|---|---|---|
| [Issue #698](https://github.com/bluetape4k/bluetape4k-image/issues/698) | 2026-10-13 UTC에 `gh issue view 698 --repo bluetape4k/bluetape4k-image`로 다시 확인: OPEN, 담당자 `debop`, `enhancement`, milestone 없음. 제목은 `Image 종류 검증을 위한 Helper 제공`. | 요청 의도와 현재 issue metadata 확인에 사용했다. CI나 PR 상태를 입증하지 않는다. |
| 작업 기준 | branch `fix/issue-698-image-signatures`, 시작 HEAD `2d843c001956482b7c5a61ca22134819d4c904a1`; 저장소 `bluetape4k/bluetape4k-image`, base `develop`. | 계획 작성의 기준점이다. 구현 HEAD, remote push, PR 또는 CI 증거가 아니다. |
| 업로드 판별 구현 | `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifier.kt:117-176,228-251` | 선언/실제 바이트 크기, 형식 판별, 차원 판독, 디코더 호출·예외 경계를 확인했다. 기존 WebP 비교는 `copyOfRange` 두 번을 사용한다. |
| 회귀 테스트 | `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifierTest.kt:41-140,245-298` | MIME, 크기·픽셀 제한, 잘린 PNG, 디코더 호출, 취소 전파의 현재 조건을 확인했다. 새 JPEG/WebP 헤더 전용 회귀 테스트는 아직 작성하지 않았다. |
| 메인 CI | `.github/workflows/ci.yml:129-130,292-321,1121-1149` | `images` 경로 감지, production ABI, `Test / images`, 집계 job을 확인했다. 실제 PR workflow 결과는 아니다. |
| 예제 CI | `.github/workflows/Examples.yml:23-39,63-90,118-136` | `images/**` PR 경로 필터, 7개 예제 행렬, `Examples Status` 집계를 확인했다. 실제 PR workflow 결과는 아니다. |
| 공개 ABI 구조 | `build.gradle.kts:474-485`; 설계의 ABI 절 및 계획 Task 5 | production JVM module ABI 집계와 새 API dump 반영 범위를 확인했다. API dump 변경·검사는 아직 하지 않았다. |
| PR 전달 게이트 | `/Users/debop/.codex/skills/bluetape-workflow/references/common-gates.md:135-161` | CG-11 권한, CG-12 원격 SHA, CG-12A 지침 갱신, CG-13 PR 생성 순서를 확인했다. 계획의 PR 이전 게이트 근거다. |
| 작업별 구현·리뷰 절차 | `/Users/debop/.codex/skills/subagent-driven-development/SKILL.md:48-60` | executor 구현·테스트·커밋과 별도 spec/quality review 순서를 확인했다. 실제 구현이나 commit은 아직 없다. |

### 문서별 독자·언어·목적·검증 경계

| 문서 | 독자와 언어 | 목적과 확인 출처 | 확인하지 않은 범위 |
|---|---|---|---|
| `docs/superpowers/specs/2026-10-12-image-signature-helpers-design.md` | 이미지 API 유지보수자와 구현자; 한국어 | Issue #698, 현재 `ImageUploadQualifier.kt`, 대응 테스트, 공개 ABI 패턴을 바탕으로 API 계약·비목표·수용 기준을 고정한다. | 구현 결과나 ABI dump는 아직 없다. |
| `docs/superpowers/plans/2026-10-12-image-signature-helpers-plan.md` | 구현자와 전달 담당자; 한국어 | 설계와 저장소 구현·테스트·ABI·CI/Examples workflow, 공통 게이트, 하위 구현 skill을 바탕으로 순서·검증·PR 전달 절차를 지정한다. | 계획에 적힌 테스트·ABI·CI 결과는 아직 실행되지 않았다. |
| `docs/review/2026-10-12-image-signature-plan-review.md` | 유지보수자와 승인자; 한국어 | live Issue metadata, 현재 코드/테스트/워크플로, 독립 리뷰 lane ID와 finding 처분을 추적한다. | 이 문서는 PR 코드 리뷰가 아니며 push, PR, exact-head CI를 입증하지 않는다. |

## 관점별 최종 결과

아래 심각도 수치는 발견 사항을 반영하고 재검토한 뒤 남아 있는 항목이다. 최초 발견과 처분은 다음 표에 따로 기록한다.

| 관점 | 검토 근거: lane / agent ID | P0 | P1 | P2 | P3 | 최종 판정 |
|---|---|---:|---:|---:|---:|---|
| 성능 | `plan_performance_r3` / `issue698_plan_performance` | 0 | 0 | 0 | 0 | PASS |
| 안정성 | `plan_stability_r5` / `issue698_plan_stability_r5` | 0 | 0 | 0 | 0 | PASS |
| 보안 | `plan_security_r4` / `issue698_plan_security` | 0 | 0 | 0 | 0 | PASS |
| 운영 | `plan_operator_r7` / `issue698_plan_operator_r7` | 0 | 0 | 0 | 0 | PASS |
| 개발자/API | `plan_api_caller_r3` / `issue698_plan_api_caller_r3` | 0 | 0 | 0 | 0 | PASS |
| 사용자/호출자 | `plan_api_caller_r3` / `issue698_plan_api_caller_r3`; 보완 `plan_caller_recheck_r1` / `issue698_caller_recheck` | 0 | 0 | 0 | 0 | PASS |

개발자/API와 사용자/호출자 관점은 초기에는 한 reviewer가 함께 검토했고, README 선택 기준의 P3는 별도 호출자 재검토로 닫았다. 따라서 관점 결과 여섯 개를 reviewer 여섯 명의 독립 결과로 표현하지 않는다.

## 발견 사항과 조치 (SPW-02, SPW-04)

| ID / 심각도 | 관점과 근거 위치 | 발견 사항 | 조치와 재검토 |
|---|---|---|---|
| PERF-1 / P2 | 성능; `ImageUploadQualifier.kt:240-243` | WebP 비교가 `copyOfRange` 두 번으로 임시 배열을 만들고, 동작 테스트만으로 무복사 요구를 보증할 수 없다. | 설계 `:56`, 계획 Task 3·Task 6의 정적 확인에 인덱스 직접 비교와 복사 금지를 추가했다. `plan_performance_r3` / `issue698_plan_performance` PASS. |
| STAB-1 / P2 | 안정성; 설계 `:52-56`, 계획 Task 1–3 | unsigned `0..255`, 빈/짧은 접두어, 포맷별 잘림·불일치·WebP offset 경계가 고정되지 않았다. | 설계 `:52-56`과 계획 Tasks 1–3에 계약과 RED/GREEN 테스트를 추가했다. `plan_stability_r5` / `issue698_plan_stability_r5` PASS. |
| SEC-1 / P2 | 보안; `ImageUploadQualifier.kt:117-176`, 테스트 `ImageUploadQualifierTest.kt:245-298` | 헤더가 일치해도 페이로드가 없거나 잘못된 데이터일 수 있다. 형식 판별을 업로드 검증으로 오용하면 안 된다. | 설계 `:55`, 계획 Task 4 `:213-239`와 Task 6 `:280-305`에서 크기·차원·픽셀 제한 및 실제 디코더 검증을 보존하고 헤더만 있는 입력을 `image_not_decodable`로 거부하게 했다. `plan_security_r4` / `issue698_plan_security` PASS. |
| OPS-1 / P2 | 운영; `ci.yml:129-130,292-321,1121-1149`, `Examples.yml:23-39,63-90,118-136` | 메인 CI만 보면 예제 테스트 7개와 집계 결과를 놓치거나 skip을 성공으로 오해할 수 있다. | 계획 Task 6 `:303-305`와 Task 8 `:358-361`에서 `images=true`, `Test / images`, ABI, `CI Status`, 7개 예제 결과와 `Examples Status`를 분리한다. `plan_operator_r7` / `issue698_plan_operator_r7` PASS. |
| API-1 / P2 | 개발자/API; `build.gradle.kts:474-485`, 설계 `:72-76` | 새 public extension의 ABI dump 갱신·검사가 변경 파일과 DoD에서 빠졌다. | 계획 Task 5 `:241-268`에 `api/bluetape4k-images.api`와 기준 갱신·검사를 추가했다. 독립 API 리뷰 PASS. |
| CALL-1 / P2 | 사용자/호출자; `images/README.md:30-40`, `images/README.ko.md:30-40` | 두 언어 README에서 공개 API, 사용 예제와 헤더 판별의 한계를 함께 확인할 계획이 구체적이지 않았다. | 계획 Task 5 `:254-262`에 같은 import·코드와 언어별 설명, 업로드 한계 고지를 요구했다. 개발자/API·호출자 리뷰 PASS. |
| OPS-2 / P3 | 운영; 설계 `:58-76`, 계획 `:369-377` | 변경을 되돌릴 때 검증 범위와 구현 외 문서 보존 기준이 부족했다. | 계획 `:369,373`에서 구현 작업 커밋별 되돌림과 patch 공백·파일 목록·ABI·qualifier·images 테스트 확인을 지정했다. 운영 리뷰 r4/r7 PASS. |
| OPS-3 / P2 | 운영; `~/.codex/skills/bluetape-workflow/references/common-gates.md:135-160`, 계획 `:328-346` | PR 권한인 CG-11, push SHA 확인인 CG-12, CG-12A 순서가 혼동될 수 있었다. | 계획 `:328-346`에서 CG-11 권한 확인과 CG-12 원격 SHA 확인을 분리하고 PR 전 CG-12A를 추가했다. `plan_operator_r6`와 `plan_operator_r7` / `issue698_plan_operator_r7` 확인. |
| OPS-4 / P2 | 운영; 계획 `:42-46,333-336` | 계획 문서 선행 커밋 요구가 구현 task 뒤에 배치되어 실행 순서가 모순됐다. | spec·plan·plan-review 커밋을 계획 `:42-46`에서 Task 1 이전 사전 게이트로 옮겼다. `plan_operator_r6`와 `plan_operator_r7` / `issue698_plan_operator_r7` 확인. |
| OPS-5 / P2 | 운영; 계획 `:358-361,377` | CG-14/15/16 순서와 DoD 집계, GNO 수동 경계, 머지 전 중단 지점이 불명확했다. | 계획 `:358-361,377`에서 적용 체크와 N/A 수를 명시하고 CG-15 보고 뒤 CG-16을 PENDING으로 둔다. GNO·머지·로컬 동기화·정리는 승인 범위에서 제외했다. 운영 최종 재검토 r7 PASS. |
| PROC-1 / P2 | 실행 절차; `~/.codex/skills/subagent-driven-development/SKILL.md:48-56`, 계획 `:40,333-336,373` | 새 executor별 작업을 요구하면서 구현 전체를 단일 커밋으로 묶도록 해 하위 skill의 작업별 구현·검토·커밋 절차와 충돌했다. | 계획 `:40,333-336`을 작업별 Lore 커밋과 필요 시 후속 수정 커밋으로 맞추고 `:373`에서 구현 커밋을 역순으로 되돌리도록 했다. `plan_operator_r7` PASS; 운영 r8은 inline으로 재대조했고 독립 결과로 계수하지 않는다. |
| PROC-2 / P2 | TDD 실행 경계; `~/.codex/skills/test-driven-development/SKILL.md`, 계획 `:39-40,48-126,333-336,373` | RED 테스트와 GREEN 구현을 별도 task로 나누면 task별 커밋·리뷰 절차상 의도적인 실패 커밋을 남기거나 GREEN 전에 작업을 완료 처리할 수 있었다. | 계획 `:39-40,92,126,333-336,373`에서 Task 1 RED와 Task 2 GREEN을 같은 executor의 한 TDD 단위로 묶고, GREEN 및 타깃 검증 뒤 테스트와 구현을 함께 커밋하며 리뷰는 커밋 뒤에 수행하도록 했다. rollback도 한 TDD 커밋으로 처리한다. 주 세션 inline 재검토에서 해결을 확인했으며, thread limit으로 독립 재검토는 수행하지 않았다. |
| PROC-3 / P2 | 커밋/롤백 범위; 계획 `:270-305,333-336,373` | 검증 전용 Task 6이 구현 커밋과 롤백 목록에 포함되어 빈 커밋 또는 존재하지 않는 rollback 대상을 요구했다. | 계획 `:334-336`에서 변경 task를 Task 1–2와 Task 3–5로 특정하고 Task 6은 검증 증거만 기록하도록 했다. `:373`의 롤백도 실제 변경 커밋인 Task 1–2와 Task 3–5로 한정했다. 독립 adversarial reviewer `plan_tdd_review` / `issue698_plan_tdd_review`는 최종 재검토에서 이 수정과 Task 1–2 TDD 경계를 확인했다. |
| PROC-4 / P2 | 롤백 검증 명령; 계획 `:373` | ABI 검증 명령이 존재하지 않는 `:bluetape4k4-images` Gradle project path를 가리켜 롤백 검증이 실패할 수 있었다. | 계획 `:373`의 오타를 `:bluetape4k-images:checkKotlinAbi`로 고쳤다. `issue698_plan_tdd_review`가 `settings.gradle.kts`와 `build.gradle.kts`의 실제 project/task 경로를 대조하고, 롤백 명령 세 개와 Task 3–5 범위를 최종 재검토해 PASS했다. |
| CALL-2 / P3 | 사용자/호출자; 설계 `:29`, 계획 `:255-262` | PNG/JPEG/WebP에는 형식별 헬퍼와 일반 `hasPrefix` 중 무엇을 먼저 쓸지 안내가 없었다. | 설계 `:29`와 계획 `:255-262`에 내장 형식별 헬퍼 우선, 다른 명시적 접두어 검사에는 `hasPrefix` 사용을 적었다. `plan_caller_recheck_r1` / `issue698_caller_recheck`에서 P0–P3 추가 발견 0건. |
| DOC-1 / SPW-01·02 | 문서; 본 리뷰 `:8-27,44-66` | 출처 원장과 finding 표에 live issue 기준, 심각도, 정확한 위치·처분이 부족했다. | live Issue metadata, 프로젝트 출처 원장, 문서별 독자·목적·검증 경계와 finding별 근거·조치를 추가했다. writer 재검토 r2가 요청한 세 문서별 출처 경계도 아래에 추가했다. |
| DOC-2 / SPW-04 | 문서; 설계 `:110`, 계획 `:248-268,383` | 설계는 API reviewer가 응답하지 않았다고 했지만 독립 PASS가 완료되어 있었다. ABI 검증을 Task 1–3에 연결했으나 실제 실행은 Task 5다. | 완료된 독립 API/호출자 리뷰를 설계 `:110`에 기록하고 ABI 기준 추적 행을 계획 `:383`에서 Task 5로 고쳤다. matrix에 reviewer lane/agent 식별자를 추가했다. |
| DOC-3 / KO-04·06 | 문서; 설계 `:55,67,110`, 계획 `:183-190,237-262,367-387` | 일부 일반 설명에 영어 용어가 섞였고 live issue 링크의 현재 상태 증거가 본문에 없었다. | 일반 설명을 다듬고 이 리뷰 `:8-27`에 live metadata를 추가했다. 제품 README는 향후 구현 대상이라고 분리하고 설계의 `actual decoder`를 `실제 디코더`로 수정했다. |
| DOC-4 / SPW-04 | 문서; 관점별 matrix `:33-40` | 통합 판정이 lane/agent 식별자 없이 reviewer의 독립성을 추적하기 어려웠다. | 여섯 관점별 lane/agent ID와 writer 재검토 r2의 PENDING 결과를 추가했다. 운영 재검토 r8은 thread 제한으로 생성되지 않아 독립 결과로 표시하지 않는다. |

## 운영 재검토와 writer 게이트 기록 (SPW-01..05, KO-01..07)

### 운영 절차 및 TDD 경계 inline 대조

- 독립 운영 재검토 lane `plan_operator_recheck_r8`는 `collaboration.spawn_agent`가 `agent thread limit reached`로 실패해 reviewer가 생성되지 않았다. `.bluetape/` receipt에는 실패 원인이 기록되어 있다.
- 주 세션은 2026-10-13 02:22 UTC에 계획 Task 1–2 `:39-126`, 작업별 커밋 게이트 `:333-336`, 롤백/수동 경계 `:369-377`, `test-driven-development/SKILL.md`, `subagent-driven-development/SKILL.md:48-60`, common-gates `:135-161`, `ci.yml:292-321,1121-1149`, `Examples.yml:63-90,118-136`을 대조했다. 이후 독립 adversarial re-review가 커밋/롤백 범위 P2와 잘못된 Gradle project path P2를 추가 발견했다. 두 항목을 수정했고 `issue698_plan_tdd_review`의 최종 재검토에서 Task 1–2 RED/GREEN 경계, Task 3–5 변경 커밋, Task 6 검증 전용 구분, 롤백 명령 경로가 모두 확인됐다.
- 이 inline 대조는 독립 운영 재검토가 아니다. 기존 `plan_operator_r7` / `issue698_plan_operator_r7`의 PASS와 구분한다.

독립 writer r1은 초기 보완을 요청했다. 그 반영본을 독립 writer `plan_writer_recheck_r2` / `issue698_plan_writer_recheck_r2`가 다시 읽고 PENDING으로 판정했다. 결과는 로컬 `.bluetape/plan_writer_recheck_r2-result.json` 및 workflow receipt에 기록했다. 해당 재검토 뒤 문서별 독자·출처 표와 reviewer ID를 보강하고 `actual decoder`를 `실제 디코더`로 고쳤다. 재검토 결과는 최신 문서 상태의 독립 PASS가 아니며, 최종 통합 문서 검사는 주 세션에서 수행한다.

| 항목 | 현재 상태 | 확인 범위 |
|---|---|---|
| SPW-01 | PASS | 각 문서의 독자·언어·목적·근거·미확인 범위와 live Issue metadata를 기록했다. |
| SPW-02 | PASS | 설계 계약·수용 기준, 실행 가능한 계획·rollback, 심각도·근거·처분·미검증 항목을 확인했다. |
| SPW-03 | PASS | 한국어 자연스러움 체크리스트를 적용하고 기술 용어와 문장 의미를 대조했다. |
| SPW-04 | PASS | 코드·테스트·ABI·workflow·skill 출처, 여섯 관점의 reviewer lane/agent ID, ABI Task 5 추적성을 다시 확인했다. |
| SPW-05 | PASS | 세 파일 read-back, Markdown 표/fence/링크/공백 구조 검사 결과를 아래에 기록했다. CLI renderer가 없어 구조 검사로 대체했다. |
| KO-01 | PASS | Issue, branch, HEAD, API 식별자, 결과와 미실행 범위가 원장과 일치한다. |
| KO-02 | PASS | 측정이나 출처가 없는 중요성·효율성 주장이 없다. |
| KO-03 | PASS | 문장을 엔지니어 간 기술 문체로 읽고 번역투·기계식 연결 표현을 고쳤다. |
| KO-04 | PASS | 같은 개념의 용어를 통일하고 코드 식별자는 보존했다. |
| KO-05 | PASS | 의미를 흐리는 농담·비유·홍보 표현이 없다. |
| KO-06 | PASS | 본문·표·링크를 세 문서에서 다시 읽고 계획 링크 두 개가 존재하는지 확인했다. |
| KO-07 | PASS | 용어 감사 결과는 최종 검사 기록에 남겼다. |

계획된 Kotlin 테스트·ABI·정적 검사는 구현 전에 실행하지 않았다. PR exact-head CI도 아직 없다. 이는 미검증 사실을 PASS로 표현하지 않기 위한 명시적 경계다.

## 문서 자체 검증과 계획 상태

- 최신 Issue #698 metadata를 live `gh issue view`로 다시 읽었다. 조회 결과는 OPEN, assignee `debop`, label `enhancement`, milestone 없음이다.
- `node /Users/debop/.codex/skills/bluetape-writer/scripts/audit-korean-terms.mjs`에 세 문서를 전달했다. 마지막 수정 뒤 결과: `Terminology audit passed: 3 file(s), series=clinic-appointment, findings=0`.
- Markdown CLI renderer(`mistune`, `pandoc`, `cmark`)와 `markdownlint`는 사용할 수 없어 구조 검사로 대체했다. Python 검사에서 세 파일 모두 fence 수가 짝수이고 표 블록 열 수가 일치했으며, 깨진 상대 링크와 후행 공백은 0건이었다.
- 최종 read-back은 설계·계획·통합 리뷰 전체를 대상으로 수행했다. 변경 범위는 세 문서뿐이며, 한국어 용어 감사 후 불일치나 파일 수정은 없다.
- 운영 재검토 r8은 native agent thread limit 때문에 생성되지 않았다. 계획과 common-gates 원문은 주 세션에서 inline으로 대조했으며, 이를 독립 review로 표시하지 않는다.
- 현재 구현·테스트·README·ABI 변경, push, PR 생성, CI 실행, 머지는 하지 않았다.

**최종 판정:** 계획 문서의 SPW-01..05와 KO-01..07은 `PASS`이며 독립 adversarial plan re-review는 P0/P1/P2/P3 0건으로 `PASS`다. 세 문서는 아직 미커밋이므로 Task 1 사전 게이트는 커밋과 범위 read-back 전까지 미완료다. 다음 실행은 세 계획 문서만 Lore 형식으로 커밋하는 것이다.
