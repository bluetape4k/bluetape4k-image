# #649 Builder 목록은 build 시점에 복사한다

## 원인과 결정

`sizes.ifEmpty { default }`는 목록이 비어 있지 않으면 원래 mutable list를 반환한다.
따라서 생성자를 `List`로 선언해도 Builder가 보관한 목록과 공유하면 불변 설정이 아니다.
같은 모듈의 `ImageFilterChain.build()`처럼 `toList()`로 사본을 만든 뒤 기본값을 적용했다.
요소인 `ThumbnailSize`는 읽기 전용 속성만 가진 값이므로 목록 사본으로 이 경계는 분리된다.
새 컬렉션 추상화나 의존성은 추가하지 않았다.

## 검증과 재발 방지

테스트는 내부 필드 reflection 대신 실제 `process()` 결과와 기록된 PNG 크기를 확인한다.
두 크기로 pipeline과 미수집 Flow를 만든 다음 Builder에 세 번째 크기를 추가했다.
수정 전에는 기존 Flow도 세 크기를 출력해 실패했고, 수정 후에는 기존 pipeline은
두 크기, 새 pipeline은 세 크기를 출력한다. 출력 디렉터리도 각 build 시점의 값을 유지한다.
빈 Builder의 320×240 기본 크기와 재사용한 Builder의 사용자 지정 크기도 별도로 확인한다.

스냅샷은 순차 Builder 재사용을 지원한다는 뜻이다. Builder 자체의 동시 수정까지
보장하는 것으로 확대 해석하지 않는다. KDoc와 한영 README에 이 경계를 명시했다.
