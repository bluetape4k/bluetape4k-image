# #650 CaptchaOptions 컬렉션 스냅샷 검토

## 범위와 판정

`CaptchaOptions`, 기존 generator 호출자, 옵션 테스트와 API/ABI 결과를 검토했다.

| 역할 | 설치된 모델·추론 수준 | 판정 | P0/P1 |
|---|---|---|---|
| code-reviewer | gpt-5.6-luna / max | 주 작업자 inline fallback | 0 |
| architect | gpt-5.6-sol / high | CLEAR | 0 |

code-reviewer native turn은 최종 응답 전에 중단되어 독립 attestation으로 사용하지 않았다.
워크플로의 inline fallback에 따라 주 작업자가 최종 diff를 다시 읽고 P0/P1,
정확성·수명·호환성·테스트·문서 위험을 점검했다. architect는 별도로 `CLEAR`를 반환했다.
현재 구현 근거는 다음과 같다.

- 수정 전 mutable `textColors`·`fonts`를 clear하면 보관 옵션이 변경되는 RED를 확인했다.
- 수정 후 CAPTCHA 옵션 테스트 20개, `checkProductionAbi` 10개 모듈, 지정 detekt가 통과했다.
- `javap`에서 기존 constructor marker, getter, 10개 `componentN`, Duration
  mangled `copy-HOM7YUo`/`copy$default`, private `serialVersionUID`가 유지됨을 확인했다.
- `readResolve`는 새 private 보조 메서드이며 공개 API가 아니다.

inline fallback에서 추가 P0/P1은 발견하지 않았다. 과거 릴리스에서 직렬화한
실제 fixture는 없으므로 구조적 UID·field descriptor 검증과 현재 round trip으로
범위를 한정한다.

## 호환성 결정과 잔여 위험

`KClass.isData`가 `false`가 되는 것은 사용자가 승인한 변경이다. Kotlin/Java
호출자는 기존 생성·getter·component·copy surface를 사용하며, 직렬화 UID는 유지한다.
호출자가 data reflection 자체를 계약으로 사용했다면 별도 마이그레이션이 필요하다.

목록 원소인 `Color`와 `CaptchaFont`는 이 옵션에서 변경할 수 없는 값으로 사용한다.
호출자가 원소 객체 자체를 외부에서 비정상적으로 변경하는 deep-copy 계약은 추가하지 않았다.

## DoD Status

- [x] 외부 목록 mutation 및 copy mutation RED/GREEN
- [x] generator 호출자·serialization·JVM ABI 확인
- [x] architect CLEAR 및 code-reviewer inline fallback 통합
- [ ] exact-head 원격 CI 확인
- [ ] 전체 PR 준비 후 새 머지 승인
