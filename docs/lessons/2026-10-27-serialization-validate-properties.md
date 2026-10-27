# Java 직렬화 검증 공통화 교훈

## 맥락

Kotlin snapshot의 생성자 검증을 `readObject`에서도 재사용하도록 `validateProperties()`를 도입했다. 검토 중 일부 역직렬화 경로에서 enum non-null 계약과 batch `sourceId` 공백 검증이 공통 함수 밖에 남거나 누락될 수 있음을 확인했다.

## 결정과 결과

- 생성자와 `readObject`가 같은 불변식을 검사하도록 공통 함수에 값 검증을 모은다.
- Java 직렬화가 Kotlin 생성자와 `init`을 우회한다는 전제는 유지한다. 역직렬화 전용 null 검증도 공통 함수에 포함하고, 컬렉션 방어 복사는 검증 직후 수행한다.
- nullable source ID와 필수 batch source ID의 계약은 구분한다. 필수 식별자는 `null`, 빈 문자열, 공백 문자열, 경로/제어 문자를 모두 거부한다.

## 검증

- `PrivacyDerivativeSerializationTest`에 coordinate space, redaction mode, thumbnail crop, output format, batch source ID의 변조 역직렬화 회귀를 추가했다.
- 대상 직렬화 테스트 16개, `:bluetape4k-images:test` 770개가 통과했고 18개는 기존 pending이다.
- `:bluetape4k-images:checkKotlinAbi`, `checkLegacyAbi`, 루트 `checkProductionAbi`가 통과했다.

## 다음 guard

직렬화 가능한 Kotlin 모델을 변경할 때는 `init`과 `readObject`의 검증 목록을 나란히 비교하고, non-null 필드와 nullable 필드의 경계를 구분한 변조 테스트를 먼저 추가한다. 검증 공통화 후에는 방어 복사·정규화가 검증 뒤에 남아 있는지도 확인한다.
