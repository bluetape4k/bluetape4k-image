# Issue #202 Step 3-R 계획 검토

## 범위

- 검토한 계획: `docs/superpowers/plans/2026-07-10-issue-202-vips-api-boundary-plan.md`
- 검토한 설계: `docs/superpowers/specs/2026-07-10-issue-202-vips-api-boundary-design.md`
- 경계: published main `images-vips-api` artifact는 `bluetape4k-images`, Scrimage, TwelveMonkeys dependency를 노출하면 안 된다. test-fixtures variant는 의도적으로 image dependency를 유지할 수 있다.

## 검토 결과

| 관점 | Result | P0 | P1 | 수정 / 근거 |
|---|---:|---:|---:|---|
| 성능 | PASS | 0 | 0 | performance-sensitive runtime change나 benchmark는 범위 밖이며, marker compilation이 dependency removal보다 먼저 온다. |
| 안정성 | PASS | 0 | 0 | opted/unopted compiler fixture는 격리되고, backend validation은 compile-only라 native runtime 의존을 피한다. |
| 보안 | PASS | 0 | 0 | dependency check 전에 normal Gradle variant 존재를 assert하고, Maven validation은 direct dependency node만 inspect한다. |
| 운영자 | PASS | 0 | 0 | descriptor check는 실제 `BluetapeImage` publication path, namespace-tolerant XPath, 실제 image group, 별도 POM/Gradle metadata evidence를 사용한다. |
| 개발자 / API | PASS | 0 | 0 | fixture task name을 사용 전에 discover하고, 예상 opt-in diagnostic을 assert하며, task order가 구현 가능한 상태로 남는다. |
| 호출자 / library user | PASS | 0 | 0 | 여덟 AVIF/HEIC README variant, copy-paste import, scoped opt-in, fixture-only guidance, reverse-KDoc boundary가 모두 명시적이다. |

## 수정한 발견 사항

1. Gradle metadata dependency array를 clean으로 받아들이기 전에 normal `apiElements` / `runtimeElements`와 test-fixtures variant가 존재해야 한다.
2. Maven validation은 direct dependency element를 inspect하고, namespace tolerant해야 하며, 실제 `io.github.bluetape4k.image:bluetape4k-images` coordinate를 사용해야 한다.
3. generated path는 case-sensitive `BluetapeImage` publication name을 보존해야 한다.
4. documentation scope는 root, API, Java 21, Java 25 README pair를 포함해야 한다. AVIF/HEIC example은 scoped opt-in과 함께 resolve 가능한 marker, format, runtime import가 필요하다.
5. fixture documentation은 local test-only image dependency가 의도적임을 드러내고 published main artifact와 구분해야 한다.

## 통합 결정

blocking repair 뒤 여섯 필수 관점을 모두 다시 확인했다. 최종 건수는 **P0=0, P1=0, P2=0, P3=0**이다. 이 계획은 Step 4 TDD implementation 시작을 위한 사용자 승인 준비가 됐다. 이 review는 source를 수정하지 않고 PR, CI dispatch, release, merge를 승인하지도 않는다.
