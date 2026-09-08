# #651 OcrOptions 컬렉션 방어 복사와 값 계약

## 원인과 결정

`OcrOptions`는 생성 시 `languages`, `variables`, `configs`, `regions`를
검증했지만 입력 컬렉션을 그대로 저장했다. 호출자가 생성 뒤 원본 목록·맵을
변경하면 `languageExpression`과 Tess4J에 전달할 설정이 검증 시점과 달라진다.
`copy`도 새 입력 컬렉션을 그대로 보관할 수 있어 같은 별칭 경계를 가졌다.

기존 JVM 생성자와 호출 표면을 유지하기 위해 `data class` 대신 일반 final class로
바꾸고 getter·`componentN`·`copy`와 Kotlin default bridge를 명시적으로 보존했다.
입력은 `toList()`/`toMap()` 후 unmodifiable wrapper로 저장해 생성자와 copy 양쪽의
외부 변경을 차단한다. 기존 `serialVersionUID`와 필드 이름·타입을 유지하고
`readResolve()`에서 다시 copy해 역직렬화 경계도 동일한 불변식을 적용한다.

승인된 호환성 결정에 따라 `KClass.isData == false`가 된다. 생성·getter·component·
copy·직렬화 계약은 유지하지만 data reflection 표면을 사용하는 호출자는 별도
마이그레이션이 필요하다.

## 검증과 재발 방지

수정 전 mutable 목록·맵을 clear한 뒤 옵션의 언어·설정·region이 바뀌는 RED와
`isData == true`를 확인했다(6 pass/2 fail). 수정 후 원본 및 copy 입력을 각각
변경해도 getter·`languageExpression`이 유지되는 테스트와 componentN 테스트가
통과했다.

- targeted `OcrOptionsTest`: 8 pass
- OCR 모듈: 35 pass, 6 pending (native/container 선택 테스트는 기본 플래그로 대기)
- `checkProductionAbi`: 10 published JVM modules 통과
- 대상 detekt: 통과
- `javap`: constructor/default bridge/getter/component/copy/copy$default 및 기존
  `serialVersionUID = -2101859296994037212L` 확인

독립 code-review에서 저장 snapshot과 생성자 원본을 각각 검증하던 조건부 P2가
발견됐다. `init`은 `this.*` snapshot을 검증하도록 보완했고, 후속 정적 재검토에서
P0/P1/P2가 모두 0건임을 확인했다. 다음 옵션 value object에서도 저장된 상태를
검증하는지 먼저 확인한다.

값 객체를 일반 class로 수동 보존할 때는 source compilation만으로 충분하지 않다.
생성된 default bridge와 Java getter, serial UID·필드 descriptor, 실제
serialization round trip을 API/바이트코드와 실행 결과로 함께 확인한다.
