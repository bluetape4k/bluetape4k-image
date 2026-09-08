# Issue #655 benchmark CI 연결 교훈

- benchmark module은 일반 `build -x :bluetape4k-images-benchmark:build`와 별개로 `benchmarkClasses`·`testClasses`를 직접 required lane에 연결해야 compile 공백을 닫을 수 있다.
- `check`가 receipt validator를 포함하더라도 CI가 `test`만 호출하면 committed receipt 검증은 보장되지 않는다. OCR corpus/protocol과 VIPS transform validator를 명시적으로 호출해야 한다.
- benchmark는 성능·native·OCR 측정 코드와 계약/receipt 단위 테스트가 한 모듈에 공존한다. hosted CI에서는 후자만 실행하고 전자는 명시적 별도 실행으로 분리해야 시간·환경 변동을 통제할 수 있다.
- 기존 module test job과 `ci-status.require_test`를 재사용하면 path-filtered job이 skip으로 끝나는 누락을 required failure로 바꿀 수 있다.
- JUnit 결과만으로 receipt 검증을 증명할 수 없으므로 validator stdout을 별도 artifact와 step summary에 보존해야 한다.
- Gradle TestKit 기능 테스트는 중첩 dependency resolution을 수행하므로 Maven Central TLS 일시 오류가 코드 실패처럼 보일 수 있다. 실패 test 이름과 nested build error를 분리해 재현하고, 재실행 후 전체 count를 다시 확인한다.
- stacked PR의 branch filter 밖 hosted CI는 실행되지 않을 수 있다. local PASS와 remote N/A/PENDING을 분리해 기록해야 한다.
