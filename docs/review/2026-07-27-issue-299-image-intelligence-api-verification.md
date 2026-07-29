# Issue #299 구현·검증 체크리스트

**워크플로:** Type A Full Feature
**저장소:** `bluetape4k/bluetape4k-image`
**기준:** `develop`
**작업 브랜치:** `feat/issue-299-image-intelligence-api`
**작업 트리:** `bluetape4k-image/.worktrees/feat-issue-299-image-intelligence-api`
**기계 실행:** `20260726T183134Z-2b6b242f`
**중단 조건:** PR의 정확한 head에서 CI와 리뷰를 통과한 merge-ready 상태

이 문서는 승인된 계획을 실행하면서 증거를 즉시 기록한다. `[ ]` 항목은 아직
증명되지 않았으며 뒤 단계 진행을 차단한다.

## 라우터

- [x] **WF-01 — Type A로 분류**
  - **조치:** 새 예제 모듈, Spring API, coroutine orchestration, 문서와 CI 변경을 분류한다.
  - **근거:** `bluetape-workflow`와 `bluetape-full-feature` 로드; Issue #299 범위 확인.
  - **실패 시:** 더 가벼운 workflow로 축소하지 않는다.
- [x] **WF-02 — 첫 구체 계획 작성**
  - **조치:** 파일, RED/GREEN, 검증, rollback, PR 경계를 가진 계획을 작성한다.
  - **근거:** `docs/superpowers/plans/2026-07-27-issue-299-image-intelligence-api-plan.md`.
  - **실패 시:** 계획 누락 시 구현을 중단한다.
- [x] **WF-03 — 계획 승인**
  - **조치:** 사용자에게 계획을 제시하고 명시적 승인을 받는다.
  - **근거:** 2026-07-27 사용자 응답 `승인`.
  - **실패 시:** 승인 전 구현하지 않는다.
- [x] **WF-04 — 실행 계약 로드**
  - **조치:** 공통, Type A, Kotlin, module, testing, Spring, coroutine 계약을 읽는다.
  - **근거:** 현재 세션에서 모든 필수 skill과 trigger reference를 완독.
  - **실패 시:** 누락된 계약이 있으면 Kotlin 편집 전 중단한다.
- [x] **WF-04A — 기계 판독 증거 확인**
  - **조치:** 기존 Type A run을 읽기 전용 검증한다.
  - **근거:** run `20260726T183134Z-2b6b242f`, sequence `3`, checksum
    `ac698f0b68dfbe22feda17decdc150f11f3153d8d04bfc43ef36b8031c43a365`,
    `ok=true`.
  - **실패 시:** receipt 오류 시 구현을 중단하고 진단한다.
- [x] **WF-05 — 의존 순서대로 게이트 실행**
  - **조치:** CG, A, Kotlin 항목을 위에서 아래로 실행한다.
  - **근거:** Tasks 1–9의 순차 RED/GREEN, 검증, 리뷰와 아래 완료 체크.
  - **실패 시:** 앞 항목 실패 시 뒤 항목을 실행하지 않는다.
- [x] **WF-06 — 누락 게이트 복구**
  - **조치:** 누락이나 약한 증거 발견 시 복구하고 하위 검증을 다시 수행한다.
  - **근거:** multipart request headroom P1과 branch Markdown trailing
    whitespace를 수정하고 관련 테스트와 전체 검증을 다시 실행했다.
  - **실패 시:** 미복구 상태로 완료하지 않는다.

## 공통 게이트

- [x] **CG-01 — 권한 재확인**
  - **조치:** AGENTS, issue, skill, plan, status, diff를 다시 읽는다.
  - **근거:** workspace/repo `AGENTS.md`, Issue #299, clean worktree,
    commits `b47b40b8`, `e0bee289`.
  - **실패 시:** 범위가 다르면 편집을 중단한다.
- [x] **CG-02 — 과거·현재 근거 조회**
  - **조치:** GNO, GitHub, 현재 저장소에서 관련 예제를 조회한다.
  - **근거:** GNO는 workshop OCR API 계획과 image OCR manual을 반환했고,
    GitHub Issue #299는 open, assignee `debop`, milestone `0.4.0`이다.
  - **실패 시:** 근거 없는 새 추상화를 추가하지 않는다.
- [x] **CG-03 — 사용자 작업과 경계 보호**
  - **조치:** repo/worktree/base/status를 확인한다.
  - **근거:** 격리 worktree, base `origin/develop@9afbef35`, clean status.
  - **실패 시:** 관련 없는 변경을 포함하거나 버리지 않는다.
- [x] **CG-04 — 정책과 독자 경계 적용**
  - **조치:** README locale, English public artifacts, Korean internal docs를 구분한다.
  - **근거:** 구현 계획의 bilingual README, English PR/commit, non-published example 경계.
  - **실패 시:** 언어·배포 경계가 흐려지면 수정한다.
- [x] **CG-05 — 생태계 패턴 재사용**
  - **조치:** OCR, detection, barcode, Spring quickstart, workflow API를 재사용한다.
  - **근거:** 승인된 spec/plan의 source anchor와 실제 local dependency alias 확인.
    CodeGraph는 files=0으로 미구축 상태여서 현재 소스 검색으로 보완한다.
  - **실패 시:** 기존 계약을 중복 구현하지 않는다.
- [x] **CG-06 — 공개·문서 계약 증명**
  - **조치:** 영어·한국어 README, diagram, registration을 동등하게 갱신한다.
  - **근거:** Task 8의 bilingual README, dark SVG/PNG 2쌍, root README와
    Examples CI 등록, 상대 링크와 공개 블로그 링크 검증.
  - **실패 시:** 문서/등록 누락 시 PR을 만들지 않는다.
- [x] **CG-07 — RED/GREEN과 targeted proof**
  - **조치:** 각 동작을 실패 테스트부터 구현한다.
  - **근거:** Tasks 1–7과 multipart headroom 보강의 RED/GREEN 기록.
  - **실패 시:** 즉시 통과한 테스트를 증거로 인정하지 않는다.
- [x] **CG-08 — heavyweight 검증 직렬화**
  - **조치:** native OCR 등 선택 검증을 다른 heavy check와 병렬 실행하지 않는다.
  - **근거:** Gradle 검증을 clean example → 영향 모듈 → projects/detekt
    순으로 실행했다. 선택적 native OCR은 환경 의존 gap으로 분리했다.
  - **실패 시:** 모호한 병렬 증거를 폐기하고 다시 실행한다.
- [x] **CG-09 — lesson 게이트**
  - **조치:** 구현에서 얻은 재사용 가능한 교훈을 커밋한다.
  - **근거:** `docs/lessons/2026-07-27-issue-299-image-intelligence-api.md`,
    commit `e063336`.
  - **실패 시:** lesson 커밋 전 PR을 만들지 않는다.
- [x] **CG-10 — pre-PR 증거 수렴**
  - **조치:** 최종 diff, 테스트, 검토를 P0=0/P1=0으로 수렴한다.
  - **근거:** code review artifact 최종 P0=0/P1=0, clean example 48/48,
    영향 모듈 791개 failure 0, `projects`, `detekt`, `actionlint`, diagram,
    link와 diff checks.
  - **실패 시:** blocker가 남으면 PR을 만들지 않는다.
- [x] **CG-11 — PR 권한 검증**
  - **조치:** repo/base/head와 PR 생성 권한을 최신 상태로 확인한다.
  - **근거:** 승인된 계획의 PR-only 범위, open Issue #299,
    `bluetape4k/bluetape4k-image`, base `develop`, approved head를 재확인했다.
  - **실패 시:** PR 생성 전 중단한다.
- [x] **CG-12 — 정확한 head 게시**
  - **조치:** force 없이 head를 push하고 remote SHA를 읽는다.
  - **근거:** force 없이 upstream을 만들고 local/remote SHA 일치를 확인했다.
  - **실패 시:** 불일치 상태로 PR을 만들지 않는다.
- [x] **CG-13 — PR 생성·검증**
  - **조치:** 영어 PR, assignee/labels/milestone, 마지막 `## DoD Status`를 적용한다.
  - **근거:** PR #300 live 조회에서 base/head, assignee `debop`, labels
    `documentation`/`enhancement`, milestone `0.4.0`, final H2 `DoD Status` 일치.
  - **실패 시:** metadata를 수리한다.
- [ ] **CG-14 — CI와 live review 통과**
  - **조치:** 정확한 head의 CI와 최신 review/thread를 확인한다.
  - **근거:** 성공한 required checks와 blocker 0.
  - **실패 시:** 구현 단계로 돌아간다.
- [ ] **CG-15 — merge-ready 보고**
  - **조치:** 정확한 PR/head와 모든 증거를 사용자에게 보고한다.
  - **근거:** 사용자에게 보이는 DoD 보고.
  - **실패 시:** merge 승인을 요청하지 않는다.
- [ ] **CG-16 — 최신 merge 승인**
  - **조치:** merge-ready 보고 이후 새 승인을 기다린다.
  - **근거:** 현재 head에 대한 사용자 승인.
  - **실패 시:** 대기는 정상 PENDING이며 merge하지 않는다.
- [ ] **CG-17 — merge 실행·검증**
  - **조치:** 승인 뒤 rebase merge하고 live SHA를 확인한다.
  - **근거:** merged state와 merge SHA.
  - **실패 시:** 자동 merge로 우회하지 않는다.
- [ ] **CG-18 — 로컬 동기화·정리**
  - **조치:** merge 확인 뒤 기본 checkout을 동기화하고 안전한 worktree만 정리한다.
  - **근거:** ancestry, local/upstream SHA, cleanup 목록.
  - **실패 시:** 모호한 작업 트리를 보존한다.

## Type A

- [x] **A-01 — 격리와 요구사항**
  - **조치:** worktree와 요구사항·제외 범위를 확정한다.
  - **근거:** 승인 spec의 목적, 포함/제외, PR-only stop condition.
  - **실패 시:** 범위가 변하면 설계로 돌아간다.
- [x] **A-02 — 현재 근거 기반 설계**
  - **조치:** 로컬 API와 기존 예제를 조사한다.
  - **근거:** spec의 재사용 표와 기준선 테스트 53 tasks PASS.
  - **실패 시:** recall 기반 설계를 금지한다.
- [x] **A-03 — 설계 승인·검토**
  - **조치:** 설계를 승인받고 6관점 검토한다.
  - **근거:** spec commit `b47b40b8`, spec review P0=0/P1=0.
  - **실패 시:** material change는 재승인한다.
- [x] **A-04 — 계획 승인·검토**
  - **조치:** 실행 계획과 6관점 검토를 승인받는다.
  - **근거:** plan commit `e0bee289`, plan review P0=0/P1=0, 사용자 `승인`.
  - **실패 시:** 순서·증거 누락 시 구현을 시작하지 않는다.
- [x] **A-05 — 위험 예측**
  - **조치:** decode, timeout/cancellation, semaphore, partial result, logging, diagram 위험을 기록한다.
  - **근거:** plan `Predicted risks and controls`.
  - **실패 시:** 위험 제어 없는 hot path를 구현하지 않는다.
- [x] **A-06 — 테스트 우선 구현**
  - **조치:** Task 1–8을 RED/GREEN으로 구현한다.
  - **근거:** Task 1–7의 RED/GREEN 기록과 Task 8 문서·다이어그램 검증.
  - **실패 시:** 실패한 동작으로 돌아간다.
- [x] **A-07 — spec/plan/hazard 검증**
  - **조치:** acceptance mapping과 module/workflow hazard를 검증한다.
  - **근거:** Task 9의 16개 수용 기준 추적표와 repository hazard 검사.
  - **실패 시:** 누락 task로 돌아간다.
- [x] **A-08 — pre-PR review 수렴**
  - **조치:** 6관점 code review와 integration review를 수행한다.
  - **근거:** code review artifact에서 P1 1건을 수정하고 최종
    P0=0/P1=0/P2=0/P3=0으로 수렴했다.
  - **실패 시:** blocker 수정·재검증.
- [x] **A-09 — durable lesson**
  - **조치:** lesson을 커밋한다.
  - **근거:** lesson path와 commit `e063336`.
  - **실패 시:** PR 생성 차단.
- [ ] **A-10 — PR·CI·review**
  - **조치:** CG-11–14를 완료한다.
  - **근거:** PR URL, SHA, live metadata, CI.
  - **실패 시:** merge-ready 보고 차단.
- [ ] **A-11 — 지식 캡처·merge-ready**
  - **조치:** 지식과 최종 DoD를 정리한다.
  - **근거:** CG-15와 pending CG-16–18.
  - **실패 시:** 완료라고 보고하지 않는다.
- [ ] **A-12 — 승인 후 merge closeout**
  - **조치:** 최신 merge 승인 뒤에만 CG-16–18을 수행한다.
  - **근거:** 사용자 승인, merge SHA, sync/cleanup.
  - **실패 시:** 현재 단계에서는 정상 PENDING.

## Kotlin trigger 게이트

- [x] **KT-01 — Kotlin 지침 로드**
  - **조치:** test, Spring, module, coroutine trigger reference를 읽는다.
  - **근거:** `testing.md`, `spring-boot.md`, `module-setup.md`,
    repository hazards, coroutine dispatcher/cancellation/timeout/test references.
  - **실패 시:** Kotlin 편집 차단.
- [x] **KT-02 — 영향과 재사용 조사**
  - **조치:** existing quickstart와 local module API를 우선 사용한다.
  - **근거:** barcode/ocr examples, suspend extraction extensions, version catalog aliases.
  - **실패 시:** raw fallback 근거 없이는 새 utility를 추가하지 않는다.
- [x] **KT-03 — Kotlin 계약 적용**
  - **조치:** validation, cancellation, dispatcher, logging, DTO 불변식을 검토한다.
  - **근거:** 여섯 관점 review와 production coroutine quick scan.
  - **실패 시:** P0/P1 수정 전 진행 차단.
- [x] **KT-04 — Kotlin 검증**
  - **조치:** diagnostics, compile, targeted/full tests, diff check를 수행한다.
  - **근거:** clean example 48/48, 영향 모듈 791개 failure 0,
    `projects`, `detekt`, diff checks.
  - **실패 시:** fresh proof 없이 PASS 금지.
- [x] **KT-05 — Kotlin 최종 체크리스트**
  - **조치:** completion 시 전체 Kotlin checklist를 렌더링한다.
  - **근거:** 아래 Kotlin trigger gate 전체를 PASS/N/A로 판정했고
    code review P0=0/P1=0이다.
  - **실패 시:** 미확인 row 공개·복구.
- [x] **KT-MOD-01 — module 등록 동기화**
  - **조치:** settings, README, AGENTS, test resources, Examples CI를 갱신한다.
  - **근거:** registration search가 settings, AGENTS, 영어·한국어 README,
    Examples CI를 확인했고 `./gradlew projects`가 module을 한 번 나열하며 성공했다.
  - **실패 시:** 누락 link가 module 완료를 차단한다.
- [x] **KT-MOD-02 — dependency governance**
  - **조치:** 관리된 alias와 local project를 사용하고 publish surface를 추가하지 않는다.
  - **근거:** existing catalog aliases와 non-published example 분류.
  - **실패 시:** 중복 version이나 BOM entry를 제거한다.
- [x] **KT-MOD-03 — benchmark module (N/A)**
  - **조치:** benchmark trigger 여부를 판단한다.
  - **근거:** 실행 예제이며 benchmark module을 만들지 않으므로 N/A.
  - **실패 시:** benchmark가 추가되면 재분류한다.
- [x] **KT-MOD-04 — 최종 module surface**
  - **조치:** compile/test와 old/new name search를 실행한다.
  - **근거:** `projects`, registration search, clean/affected tests 통과.
  - **실패 시:** surface 불일치 수정.
- [x] **KT-TEST-01 — 프로젝트 테스트 관례**
  - **조치:** JUnit, MockK, bluetape4k assertions, suspend-aware API를 사용한다.
  - **근거:** 13개 test class와 imports/assertions 검토, `detekt` 통과.
  - **실패 시:** 호환되지 않는 assertion 교체.
- [x] **KT-TEST-02 — concurrency·cancellation 증명**
  - **조치:** virtual time과 실제 job cancellation, permit 복구를 검증한다.
  - **근거:** 집중 20개 중 runner/workflow/cancellation tests 통과.
  - **실패 시:** 가짜 cancellation proof 교체.
- [x] **KT-TEST-03 — infrastructure fixture (N/A)**
  - **조치:** Testcontainers 사용 여부를 판단한다.
  - **근거:** default suite는 생성 이미지와 local providers만 사용하므로 N/A.
  - **실패 시:** container 도입 시 launcher/serialization 게이트를 연다.
- [x] **KT-TEST-04 — HTTP lifecycle**
  - **조치:** input, timeout, cancellation, sanitized failure를 검증한다.
  - **근거:** controller, exception handler, application, cancellation,
    observability tests 통과.
  - **실패 시:** 누락 lifecycle case 보강.
- [x] **KT-TEST-05 — targeted→module 검증**
  - **조치:** 개별 test class부터 clean module test까지 실행한다.
  - **근거:** 집중 20개, multipart RED/GREEN, clean example 48개,
    영향 모듈 791개 순서로 통과.
  - **실패 시:** stale cache 결과를 인정하지 않는다.
- [x] **KT-SPR-01 — optional type guard (N/A)**
  - **조치:** compileOnly optional bean type 여부를 확인한다.
  - **근거:** example은 implementation local projects만 사용하므로 N/A.
  - **실패 시:** compileOnly type 추가 시 조건을 적용한다.
- [x] **KT-SPR-02 — registration·ordering**
  - **조치:** profile bean과 startup guard를 명시적으로 검증한다.
  - **근거:** `ImageIntelligenceConfigurationTest` 4개 통과.
  - **실패 시:** bean ordering 의존 제거.
- [x] **KT-SPR-03 — configuration semantics**
  - **조치:** immutable properties와 default/invalid 값을 검증한다.
  - **근거:** properties 5개와 multipart request headroom test 통과.
  - **실패 시:** mutable/ambiguous property 수정.
- [x] **KT-SPR-04 — Spring test isolation**
  - **조치:** context runner와 좁은 application test를 사용한다.
  - **근거:** profile은 `ApplicationContextRunner`, HTTP는 좁은
    `SpringBootTest`/standalone MockMvc로 검증했다.
  - **실패 시:** broad scan으로 숨겨진 계약 제거.
- [x] **KT-SPR-05 — lifecycle**
  - **조치:** suspend controller와 provider cancellation을 보존한다.
  - **근거:** async MockMvc, 실제 job cancellation, 다음 요청 복구 tests 통과.
  - **실패 시:** cancellation wrapping 수정.

## 현재 집계

필수 검사: 45/53; N/A: 3; 차단: 0.
미확인: CG-14–18, A-10–12.

## 구현 근거

### 작업 1 — 예제 등록과 설정 계약

- **RED:** `:spring-boot-image-intelligence-api:compileTestKotlin`은 unresolved
  `ImageIntelligenceProperties`만으로 실패했다.
- **GREEN:** `ImageIntelligencePropertiesTest` 5/5 passed.
- **등록:** `./gradlew projects` listed
  `:spring-boot-image-intelligence-api` exactly once and completed successfully.
- **Heavy checks:** N/A; this task used no native, container, JNI, or external service.

### 작업 2 — 업로드 검증과 단일 디코딩

- **RED:** qualifier, upload exception, qualified-image 계약이 없어 test compilation이 실패했다.
  WebP test fixture에는 기존 catalog의 `libs.scrimage.webp` test dependency를 재사용했다.
- **GREEN:** `ImageUploadQualifierTest` 8/8 passed.
- **증명한 경계:** PNG/JPEG/WebP magic 일치, MIME 위장 차단, reported/actual byte
  한계, side/pixel 한계의 decode 이전 차단, probe fallback, malformed 정제,
  upload-read cancellation 재전파, 정상 입력당 decode 1회.
- **Heavy checks:** N/A; pure JVM generated image만 사용했다.

### 작업 3 — 분석 결과와 공급자 실행 보호

- **RED:** `AnalysisResult`, `GuardedAnalysisRunner`, provider-unavailable 계약 부재로
  test compilation이 실패했다.
- **GREEN:** `GuardedAnalysisRunnerTest` 6/6 passed.
- **동시성 증명:** permit 상한 2, failure/timeout/cancellation 뒤 permit 복구,
  subsequent request 성공.
- **취소 증명:** 내부 `TimeoutCancellationException`만 `Failed(timeout)`으로
  바꾸고 외부 cancellation은 원래 message와 함께 재전파했다.
- **정제 증명:** 예상하지 못한 exception text는 결과에 들어가지 않고
  `provider_failure` reason code만 남는다.

### 작업 4 — OCR·객체 탐지·바코드 공급자 경계

- **RED:** 공급자 계약, 프로필별 구현체, 생성형 QR fixture가 없어 test compilation이
  실패했다.
- **GREEN:** `ImageAnalysisProvidersTest` 5/5,
  `ImageIntelligenceConfigurationTest` 4/4 passed.
- **기본 경계:** 기본 프로필은 OCR·객체 탐지를 `Unavailable`로 결과화하고
  ZXing만 실제 로컬 공급자로 사용한다.
- **프로필 소유권:** `demo`, `native-ocr`, 기본 프로필에서 OCR·객체 탐지 공급자가
  각각 하나만 등록되며, `demo,native-ocr` 동시 사용은 고정 메시지로 시작을 거부한다.
- **Fixture 출처:** QR 이미지는 테스트 실행 시 `visitor:PASS-001` payload로
  생성하며 외부 binary fixture를 추가하지 않았다.
- **Native 경계:** 기본 테스트는 Tesseract를 호출하거나 모델·traineddata를
  내려받지 않는다.

### 작업 5 — 병렬 분석과 방문증 정책 분리

- **RED:** 병렬 workflow, 전체 상태 집계, 방문증 정책 타입이 없어 test compilation이
  실패했다.
- **GREEN:** `ImageIntelligenceWorkflowTest` 4/4,
  `VisitorPassPolicyTest` 5/5 passed.
- **병렬성 증명:** 세 분석 lane의 최대 동시 실행 수가 3이며, 각 lane은
  `analysis.ocr`, `analysis.detection`, `analysis.barcode` 고유 키에만 결과를 쓴다.
- **부분 결과 증명:** OCR 공급자 예외가 `Failed(provider_failure)`로 남아도
  객체 탐지와 바코드 결과를 보존하고 workflow 단계는 완료된다.
- **취소 증명:** 외부 job 취소가 실행 중인 세 공급자 모두의 `finally`
  경계까지 전파된다.
- **정책 경계:** `Empty`와 `Failed`를 구분하고, 민감 영역·잘못된 QR·공급자
  저하·사실 개수 조건을 고정된 우선순위와 reason code로 판정한다.

### 작업 6 — 안정된 부분 결과 HTTP API

- **RED:** `/api/images/intelligence` endpoint가 없어 요청이 `404`로 실패했다.
- **GREEN:** controller 3/3, service envelope 2/2, sanitized exception mapping 1/1 passed.
- **Transport 경계:** 생성형 방문증 QR은 HTTP `200`, 전체 `COMPLETED`, 정책
  `ALLOW`와 세 provider 식별자를 반환한다.
- **부분/실패 envelope:** 한 lane 실패는 `PARTIAL`, 사용 가능한 lane이 없으면
  `FAILED`를 반환하며 둘 다 분석 envelope를 생성한 정상 HTTP 결과다.
- **입력 경계:** multipart 누락·빈 파일·지원하지 않는 형식·MIME 불일치·손상
  이미지·압축 크기·한 변·픽셀 한계를 안정된 `ProblemDetail.reasonCode`로 구분한다.
- **누출 경계:** 응답과 정제된 `500`에는 `WorkContext`, `WorkReport`, raw bytes,
  stack trace, native path와 원본 workflow exception message가 없다.

### 작업 7 — 생명주기·복구·관측성 통합 검증

- **Application 경계:** 기본 Spring context는 disabled OCR·disabled detector·ZXing을
  구성하고, 유효한 빈 이미지를 `PARTIAL`·`MANUAL_REVIEW`로 fail-closed 처리한다.
- **취소 후 복구:** 실행 중인 세 lane이 모두 외부 취소를 관찰한 뒤 같은
  workflow의 후속 요청이 성공해 semaphore permit 반환을 증명한다.
- **타임아웃 격리:** 한 lane의 내부 timeout은 `Failed(timeout)`으로 남고 다른
  두 lane은 `Empty` 결과를 정상적으로 반환한다.
- **관측성:** request ID, provider ID, 상태, 경과 시간이 로그에 남고 QR payload,
  OCR 본문, native path, 원본 exception message, stack trace는 남지 않는다.
- **Clean example gate:** 최초 `cleanTest test --no-build-cache`에서 47/47 passed,
  `BUILD SUCCESSFUL`.

### 작업 8 — 독자 중심 문서·다이어그램·CI 등록

- **독자 계약:** 영어·한국어 README가 방문증 사례, 입력 검증, 단일 디코딩,
  세 분석 lane의 병렬 실행, 부분 실패, 외부 취소, 정책 교체, 운영 한계를 같은
  순서와 의미로 설명한다.
- **Source anchor:** upload qualifier, workflow, provider adapter, visitor policy,
  HTTP/cancellation test로 바로 이동하는 상대 링크와 관련 공개 블로그 링크를 제공한다.
  모든 상대 링크가 현재 worktree에 존재하고 영어·한국어 공개 링크 4개가 HTTP 200을
  반환했다.
- **Diagram reference:** 기존
  `examples/spring-boot-ocr-api/docs/images/readme-diagrams/examples-spring-boot-ocr-api-sequence-01.png`,
  `examples/spring-boot-barcode-api/docs/images/readme-diagrams/barcode-api-sequence.png`,
  `examples/spring-boot-barcode-api/docs/images/readme-diagrams/barcode-api-architecture.png`
  구조를 참고하고, 승인 계획에 따라 dark palette로 재구성했다.
- **Architecture diagram:** upload에서 qualification, dimension probe, 단일
  `ImmutableImage`, OCR/detection/ZXing, aggregate, `VisitorPassPolicy`, response까지
  현재 구현 경계를 표현한다.
- **Interaction diagram:** participant 6, lifeline 6, activation 10, message 19,
  branch frame 3으로 정상 완료, 한 공급자 실패, 외부 취소를 표현한다.
- **자동 diagram check:** SVG text hazard 0, code highlight omission 0,
  connector intrusion/crossing/shared segment/label collision 0, geometry failure 0,
  endpoint check PASS, mixed-corner check PASS. SVG 2개는 `xmllint --noout`,
  PNG pair 존재 검사를 통과했다.
- **Visual inspection:** 두 PNG를 원본 크기로 열어 glyph 누락, clipping, 잘못된
  화살촉, label/line 겹침이 없음을 확인했다. icon을 사용하지 않아 icon source
  검사는 N/A다.
- **등록:** root 영어·한국어 README에서 OCR quickstart 다음에 예제를
  소개하고, Examples workflow에 정확히 한 개의 module test row를 추가했다.
  `actionlint`와 `git diff --check`가 통과했다.

### 작업 9 — 요구사항 추적과 최종 검증

| 수용 기준 | 구현 | 테스트·문서·명령 근거 |
| --- | --- | --- |
| 실행 가능한 비배포 Spring Boot 예제 | `build.gradle.kts`, application main, settings 등록 | `./gradlew projects`, Examples CI row |
| local image/OCR/barcode와 관리된 workflow 재사용 | module project dependencies와 `bt4k.bluetape4k.workflow` | 영향 모듈 7개 테스트 |
| 한 번 검증·한 번 디코딩·세 작업 공유 | `ImageUploadQualifier`, `QualifiedImage`, `ImageIntelligenceWorkflow` | `ImageUploadQualifierTest`의 decode count와 decode 이전 거부 |
| 제한된 병렬 OCR·검출·바코드 | 공급자별 `Semaphore`, `suspendParallelFlow` | `GuardedAnalysisRunnerTest`, `ImageIntelligenceWorkflowTest` |
| 네 분석 결과를 독립 표현 | `AnalysisResult` sealed interface | runner/provider/policy tests |
| 워크플로 완료와 업무 성공 분리 | 각 작업이 결과를 기록한 뒤 `WorkReport.success` | workflow test와 bilingual README |
| 한 작업 실패 뒤 형제 결과 보존 | 공급자 예외를 `Failed`로 정규화 | workflow/service/cancellation tests |
| 작업 timeout과 외부 cancellation 구분 | `GuardedAnalysisRunner`의 catch 순서 | runner와 cancellation tests |
| 분석 사실과 방문증 정책 분리 | `ImageAnalysisResults`, `VisitorPassPolicy` | 정책 결정표 5개 |
| 기본 테스트에 네이티브·운영 모델 불필요 | disabled/demo provider profiles | configuration/application tests와 README |
| ZXing·빈 결과·부분 실패·입력 오류·취소 | 실제 `ZxingBarcodeReader`와 생성형 QR fixture | 최종 example clean test 48개 |
| 영어·한국어 README와 dark SVG/PNG | module README 2개, diagram 2쌍 | 링크·SVG·geometry·PNG 원본 검사 |
| settings·AGENTS·root README·Examples CI | 저장소 등록 파일 5개 | registration search, `actionlint` |
| targeted/full example tests | 새 예제와 영향 모듈 | 최종 clean 48/48, 영향 모듈 실패 0 |
| 정적·문서 검증 | detekt, actionlint, diagram scripts | 모든 명령 exit 0 |
| manual/BOM 미변경 | branch changed-file 목록 | 금지 범위 검색 결과 `none` |

#### 성능·안정성 집중 증거

- `ImageUploadQualifierTest`, `GuardedAnalysisRunnerTest`,
  `ImageIntelligenceWorkflowTest`, `ImageIntelligenceCancellationTest` 20개를
  `--rerun-tasks`로 실행해 실패 0건을 확인했다.
- 단일 디코딩, 픽셀 한도의 디코딩 전 거부, 세 lane의 겹친 실행, 공급자별 동시
  실행 상한, 실패·timeout·취소 뒤 permit 반환, 한 공급자 실패 뒤 형제 결과 보존을
  검증했다.
- `QualifiedImage`는 media type, dimensions, `ImmutableImage`만 보관하며 업로드
  `ByteArray`를 필드로 유지하지 않는다.
- production coroutine quick scan에서 `GlobalScope`, `runBlocking`,
  `Thread.sleep`, `delay`, monitor synchronization, `runCatching` 사용은 0건이었다.

#### 리뷰에서 보강한 운영 경계

- **RED:** multipart 파일 한도와 전체 요청 한도가 모두 5 MB여서
  `maxRequestSize > maxFileSize` 테스트가 `false`로 실패했다.
- **GREEN:** 전체 요청 한도를 6 MB로 조정한 뒤 같은 테스트가 통과했다.
- **Review:** 여섯 관점과 통합 검토의 최종 결과는 P0=0, P1=0, P2=0, P3=0이다.
  자세한 근거는
  `docs/review/2026-07-27-issue-299-image-intelligence-api-code-review.md`에 있다.
- **알려진 gap:** `native-ocr` 실호출은 Tesseract와 traineddata가 필요한 선택 검증이라
  현재 호스트에서 실행하지 않았다. 기본 경로의 완료를 차단하지 않으며 README에
  설치 조건과 강제 종료 한계를 명시했다.
