# #650 CaptchaOptions 컬렉션 방어 복사와 값 계약

## 원인과 결정

`CaptchaOptions`는 생성 시 목록을 검증했지만 입력 `List`를 그대로 저장했다.
호출자가 생성 뒤 `clear()`하면 검증을 통과한 `textColors`와 `fonts`가 비어
generator의 전제가 무너진다. `copy`도 새 입력 목록을 그대로 보관하는 같은
경계를 가져야 한다.

기존 생성자 파라미터와 JVM 호출 표면을 유지하기 위해 `data class` 대신 일반
final class로 바꾸고, getter·`componentN`·`copy`와 Kotlin default bridge를
명시적으로 보존했다. 목록은 `Collections.unmodifiableList(values.toList())`로
생성자·copy·직렬화 복원 경계에서 복사한다. `serialVersionUID`는 기존 값을
그대로 유지하고 `readResolve()`에서 다시 copy해 오래된 stream도 방어 복사한다.

`KClass.isData == false`는 승인된 호환성 결정이다. JVM 생성자·getter·component·
copy/copy$default·직렬화 계약을 유지하는 대신 data reflection 표면만 바뀐다.

## 검증과 재발 방지

수정 전 mutable list를 clear한 뒤 options가 비워지는 회귀를 실패시켰다.
수정 후 생성자·copy의 입력 목록 변경, generator가 읽는 getter, 직렬화 round trip,
componentN, equality와 기본값을 테스트했다. CAPTCHA 모듈 20개와 production ABI
10개 모듈이 통과했고 지정 detekt는 통과했다.

값 객체를 일반 class로 수동 보존할 때는 source compilation만으로 충분하지 않다.
생성된 `$default`, value-class duration의 mangled 이름, 이전 `serialVersionUID`,
Java getter 및 실제 serialization을 javap/API·실행 결과로 함께 확인한다.
