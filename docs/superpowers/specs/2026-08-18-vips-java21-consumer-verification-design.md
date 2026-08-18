# VIPS Java21 소비자 검증 설계

## 목적

부모 PR #538의 native lifecycle·concurrency 계약 위에 Issue #486의 소비자 검증 경계를 고정한다. 대상은 `images-vips-api`, `images-vips-java21`, 공통 golden fixture, benchmark 모듈, published BOM, 그리고 CI/nightly 증적이다.

성공 조건은 다음 네 가지다.

1. 현재 release contract에 맞춰 `images-vips-api`와 legacy artifact인 `images-vips-java21`의 production bytecode가 Java 25 이하이고, 실제 Java 25 JVM에서 test fixture가 아닌 production classpath만으로 소비자 smoke가 실행된다. 원 Issue의 JDK21 문구는 PR #502에서 전역 Java25 baseline으로 교정되었으므로 구현 전에 Issue acceptance를 이 기준으로 정렬한다.
2. golden 비교에서 resource 누락이 JUnit assumption skip로 사라지지 않는다. native capability가 없는 환경의 skip과 golden contract 위반을 분리한다.
3. Java21 JNI backend benchmark가 실제로 실행되고 JSON 결과가 CI artifact와 step summary에 남는다. benchmark contract test만으로 실행을 대신하지 않는다.
4. published BOM을 사용하는 별도 임시 consumer project가 Java 25 toolchain으로 dependency resolution·compile·smoke 실행을 완료한다.

## 현재 근거와 범위

- Issue #486: `bluetape4k/bluetape4k-image#486`, milestone `0.5.0`, Epic #509의 VIPS-2.
- 부모 stacked base: PR #538 `fix/issue-485-vips-contract`, head `7726530975e664330e247d48089b06d55e9fbd2d`.
- PR #502(`662869822d47...`)가 Java 25를 실제 repository baseline으로 확정했고 legacy `java21` 이름만 compatibility identifier로 유지한다. 따라서 현재 branch에서 Java21 성공 smoke/major 65를 구현하면 merged release contract를 되돌리는 scope expansion이 된다.
- 현재 golden directory는 비어 있고 `VipsGoldenAssert`는 resource가 없을 때 assumption skip한다.
- benchmark task/configuration은 존재하지만 CI의 일반 build와 nightly 모듈 lane은 benchmark 실행 결과를 제출하지 않는다.
- BOM은 동적 subproject constraint를 게시하지만 published-coordinate consumer resolution 증거가 없다.

### 포함 범위

- 현재 Java25 production compile target의 소비자 smoke/bytecode ceiling task. Issue acceptance는 JDK25/major 69로 먼저 정렬한다.
- golden assertion의 missing-resource fail-closed 동작과 canonical PNG fixture 복구.
- Java21 JNI용 짧은 benchmark configuration, report 검증 task, CI/nightly artifact·summary.
- 임시 file Maven repository에 게시된 BOM/API/JNI를 사용하는 격리 consumer smoke task/test.
- 관련 README EN/KO와 Issue/PR 증적 문서.

### 제외 범위

- FFM Java25 backend의 runtime 또는 native API 변경.
- libvips JNI binding 자체의 기능 변경.
- benchmark 기준값의 성능 우열 판정. 이번 변경은 실행·재현·증적 존재를 보장한다.
- 외부 Maven Central release/publication dispatch.

## 설계 결정

### 1. Java 25 production line과 consumer-only verification 분리

`images-vips-api`와 `images-vips-java21`의 `main` Kotlin/Java compile 및 jar는 Java 25를 유지한다. 별도의 `consumerTest` source set은 test fixtures를 classpath에 넣지 않고 `main` output·runtime dependency만 사용하며 Java 25 launcher로 실행한다. 이 task는 published consumer가 test fixture API에 우연히 의존하지 않는다는 경계를 증명한다.

production class file major는 69 이하를 전용 Gradle verification task가 재귀적으로 검사한다. 이 검사는 consumer smoke와 별개로 실패하므로, 우연한 Java 26 class 누출을 runtime 결과에 의존하지 않는다.

### 2. Golden resource는 canonical fixture로 복구하고 누락은 실패

`images-vips-api/src/testFixtures/resources/golden/vips/`에 Java25 FFM backend가 생성한 canonical PNG 여섯 개를 저장한다. Java21/Java25 양쪽 golden test는 동일 resource를 픽셀 비교한다. `VipsGoldenAssert`는 update mode가 아니면 missing key를 `AssertionError`로 보고한다. native capability 부재는 각 backend의 기존 explicit capability gate에서만 skip하며, capability가 준비된 뒤 resource가 없으면 테스트 실패다.

update mode는 CI에서 계속 금지한다. fixture 재생성은 Java25 FFM authoritative source와 명시적인 local command로만 수행한다.

### 3. Benchmark는 짧은 Java21 JNI smoke configuration으로 실행

기존 장시간 benchmark configuration과 report provenance는 변경하지 않는다. 새 `vipsJava21Smoke` configuration은 `VipsBackendBenchmark`만 대상으로 1 fork·최소 warmup/measurement를 사용한다. `vips.impl=java21`로 runtime backend를 고정하고, report JSON이 생성되지 않거나 positive score row가 없으면 verification task가 실패한다.

CI는 benchmark task와 report verification을 순차 실행하고 `build/reports/benchmarks`를 artifact로 업로드하며 JSON의 backend/configuration 경로를 step summary에 기록한다. nightly도 동일한 lane을 실행하되 native 환경이 없는 일반 PR build에는 이 무거운 lane을 강제하지 않는다.

### 4. BOM consumer는 published coordinate를 실제로 resolve

전용 Gradle verification task가 먼저 현재 version의 BOM/API/JNI publication을 임시 file Maven repository에 게시한 뒤, 임시 디렉터리에 독립 consumer project를 생성한다. consumer는 해당 file repository와 Maven Central만 사용하고 `platform("io.github.bluetape4k.image:bluetape4k-image-bom:<version>")` 및 버전 없는 `bluetape4k-images-vips-java21` coordinate를 선언한다. Java 25 toolchain으로 compile하고, native capability가 명시적으로 활성화된 경우 Java25 smoke main을 실행한다. Maven Local과 project substitution은 사용하지 않는다.

임시 디렉터리는 task 종료 시 삭제하며 repository source에 생성된 consumer 파일을 남기지 않는다. publication 실패, BOM 미해결, versionless constraint 누락, Java 25 compile 실패는 모두 non-zero failure다.

## 실패·호환성 계약

| 상황 | 결과 | 외부 노출 |
|---|---|---|
| Java25 production class가 major 69 초과 | bytecode verification 실패 | 위반 class path와 major를 build log에 보고 |
| consumer가 test fixture symbol을 요구 | compile 실패 | fixture가 consumer classpath에 없음을 증명 |
| native capability 미설정 | consumer/golden/benchmark lane의 명시적 capability gate | skip reason을 summary에 기록 |
| native capability 설정 후 libvips/JNI 불능 | 테스트/benchmark 실패 | raw native path를 사용자 응답에 노출하지 않고 CI log에만 보존 |
| golden resource 누락 | assertion failure | key와 canonical resource 경로 보고 |
| benchmark report 누락/빈 score | verification task 실패 | expected report directory와 backend 보고 |
| BOM 또는 module coordinate 미해결 | consumer build 실패 | coordinate/version/constraint를 보고 |

기존 public Kotlin/Java API signature는 변경하지 않는다. production target은 PR #502가 확정한 Java25를 유지하고, consumer-only source set은 fixture 누출 방지 검증 surface다.

## 수용 기준과 DoD

- [ ] acceptance 정렬 후 Java25 consumer task가 Java 25 launcher에서 production-only classpath로 실행되고 native image dimension smoke를 통과한다.
- [ ] `verifyVipsJava21Bytecode`가 API/JNI production output의 class file major 69 ceiling을 검증한다.
- [ ] golden six PNG가 tracked resource로 존재하고 Java21/Java25 golden comparison이 missing skip 없이 실행된다.
- [ ] missing-key regression test가 fail-closed를 증명한다.
- [ ] `vipsJava21Smoke` benchmark가 실제 JNI backend를 실행하고 report verification이 JSON score를 확인한다.
- [ ] CI와 nightly가 benchmark artifact/summary를 보존한다.
- [ ] file-repository published BOM consumer가 Java25 compile/resolution/smoke를 통과한다.
- [ ] EN/KO README가 legacy `java21` artifact name과 Java25 runtime 경계를 일치하게 설명한다.
- [ ] Kotlin pattern, TDD RED→GREEN, native/JNI sequential rule, actionlint, `git diff --check`, affected Gradle tests가 PASS한다.

## 대안과 trade-off

| 대안 | 판단 |
|---|---|
| 현재 JDK25 target을 유지하고 stale JDK21 acceptance를 그대로 구현 | PR #502와 공개 문서의 release contract를 깨므로 거부. Issue acceptance를 JDK25/major 69로 먼저 정렬 |
| Maven Local/project substitution consumer | developer machine state와 project output을 섞어 published metadata를 증명하지 못하므로 거부 |
| 기존 장시간 benchmark를 PR마다 실행 | native 환경·시간·flakiness 비용이 크고 Issue의 증적 목적보다 과하므로 짧은 smoke configuration을 추가 |
| golden missing을 assumption skip로 유지 | 조용한 회귀를 허용하므로 거부 |

## Writer gate

- SPW-01: 독자·목적·근거 고정 — PASS
- SPW-02: 설계/경계/실패/호환성/수용 기준 구조 — PASS
- SPW-03: 한국어 기술 문체·용어·식별자 보존 — PASS
- SPW-04: Issue/현재 파일/부모 PR/계획 traceability — PASS
- SPW-05: 최종 문맥 read-back — PASS
