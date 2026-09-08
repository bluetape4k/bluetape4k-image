# #651 OcrOptions 컬렉션 스냅샷 검토

## 범위와 판정

`OcrOptions`, Tess4J 호출자, 기존 옵션 테스트와 API/ABI 결과를 검토한다.

| 역할 | 설치된 모델·추론 수준 | 판정 | P0/P1 |
|---|---|---|---|
| code-reviewer | gpt-5.6-luna / max | CLEAR (P2 보완 후 재검토) | 0 |
| architect | gpt-5.6-sol / high | CLEAR | 0 |

code-reviewer는 최초 검토에서 생성자 원본과 저장 snapshot을 각각 검증하던
조건부 P2를 지적했다. 구현은 `init` 검증을 `this.languages`,
`this.tessdataPath`, `this.variables`, `this.configs`로 바꿔 저장된 snapshot과
검증 대상을 일치시켰고, 후속 정적 재검토에서 P0/P1/P2 0건 및 `CLEAR`를
확인했다. architect도 별도로 `CLEAR`를 반환했다. 현재 구현 근거는 다음과 같다.

- 수정 전 mutable `languages`를 clear하면 `languageExpression`이 빈 문자열이
  되는 RED를 확인했다.
- 수정 후 생성자·copy의 languages/variables/configs/regions 입력 변경 격리,
  componentN, `isData == false`, serialization round trip을 테스트했다.
- OCR 모듈 35 pass/6 pending, `checkProductionAbi` 10개 모듈, 대상 detekt가
  통과했다.
- `javap`에서 기존 constructor/default bridge, getter, 9개 component, copy와
  `copy$default`, 기존 serial UID를 확인했다.

## 호환성 결정과 잔여 위험

`KClass.isData`가 `false`가 되는 것은 승인된 변경이다. Kotlin/Java 호출자는 기존
생성·getter·component·copy 표면을 사용하며, 직렬화 UID와 필드 descriptor는
유지한다. data reflection 자체를 계약으로 사용한 호출자는 별도 마이그레이션이
필요하다.

`OcrRegion` 원소는 immutable data value이므로 이 옵션에서는 list 구조만
방어 복사한다. 호출자가 향후 mutable region 원소를 도입하면 deep-copy 범위를
다시 검토해야 한다. native Tess4J 실행은 옵션 단위 테스트에 필요하지 않으며,
기본 모듈 실행에서 native/container 선택 테스트가 pending인 것은 환경 게이트
증거로 기록한다.

## DoD Status

- [x] 외부 컬렉션·맵 mutation 및 copy mutation RED/GREEN
- [x] Tess4J-free 옵션 테스트와 serialization round trip
- [x] constructor/getter/component/copy/default bridge/serial UID 확인
- [x] OCR 모듈·ABI·대상 detekt 검증
- [x] 독립 code-reviewer 및 architect 판정 통합
- [ ] exact-head 원격 CI 확인
- [ ] 전체 PR 준비 후 새 머지 승인
