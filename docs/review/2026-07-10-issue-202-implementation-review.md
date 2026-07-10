# Issue #202 구현 6-R 코드 리뷰

## 범위와 기준선

- 기준선: `origin/develop...HEAD`
- 모듈 slice: `images-vips-api`, `images-vips-java21`, `images-vips-java25`, `images` AVIF/HEIC 계약 문서
- 검토 대상: Vips 전용 opt-in marker, main/test-fixtures 의존성 경계, Maven POM/Gradle metadata, 테스트 fixture, README/KDoc
- lane 제한: 읽기 전용, Gradle/Testcontainers 실행 금지

## 독립 관점 결과

| Tier | 관점 | P0 | P1 | P2 | P3 | 결과와 근거 |
|---|---:|---:|---:|---:|---:|---|
| 1 | Performance | 0 | 0 | 0 | 0 | runtime hot path와 locking 변경이 없고, POM XML 처리는 작은 publication-time 목록으로 제한됨 |
| 2 | Stability | 0 | 0 | 0 | 0 | lifecycle/cancellation 경로는 미변경이며, fixture와 normal variant 경계가 분리됨 |
| 3 | Security | 0 | 0 | 0 | 0 | allowlist와 optional fixture 항목으로 제한된 local POM 변환이며, secret·untrusted input 경로 없음 |
| 4 | Operator/Ops | 0 | 0 | 0 | 0 | capability report, host-local smoke test, native remediation, rollback 의존성 안내가 문서화됨 |
| 5 | Developer/API | 0 | 0 | 0 | 0 | marker target/retention, backend import migration, stable report opt-in-free 사용, compiler fixture를 확인함 |
| 6 | User/Caller | 0 | 0 | 0 | 0 | EN/KO README 예시에 scoped opt-in/import가 있고, direct dependency migration snippet과 test-fixtures 범위를 명시함 |

## 수렴 이력

| Iteration | 발견 | 처리 | 재검토 |
|---|---|---|---|
| 1 | P2: POM filter가 미래의 non-optional main dependency까지 제거할 수 있음 | optional test-fixtures-derived dependency만 제거하도록 제한 | Tier 1 재검토 통과 |
| 1 | P2/P3: transitive `bluetape4k-images` 제거에 대한 migration/rollback 안내가 부족하거나 복사 불가 | EN/KO README에 direct dependency declaration snippet 추가 | Tier 4/Tier 6 재검토 통과 |

## Main-session integration

- POM 직접 의존성 검증은 `pom_forbidden=0`이다.
- Gradle normal variant에는 `bluetape4k-images`, Scrimage, TwelveMonkeys direct dependency가 없고, test-fixtures variant에만 `bluetape4k-images`가 남는다.
- `VipsIncubatingApi`는 AVIF/HEIC enum entry와 내부 capability implementation 사용으로 한정되고, 기존 `IncubatingImageApi` import는 Vips API/Java 21/Java 25 소스에서 제거됐다.
- core AVIF/HEIC KDoc은 binding-neutral이며 README EN/KO 쌍이 함께 갱신됐다.
- CI workflow, module registration, diagram, concurrency helper, CHANGELOG 변경은 범위 밖이다. 새 module/새 dependency/production concurrency 변경도 없다.

## Gate verdict

`PASS` — final integrated count is `P0=0`, `P1=0`, `P2=0`, `P3=0`. Step 7로 진행 가능하다.
