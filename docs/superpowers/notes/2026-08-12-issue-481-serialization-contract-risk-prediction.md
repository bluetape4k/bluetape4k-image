# issue #481 구현 위험 예측

## 범위

대상은 `bluetape4k-images` privacy snapshot/Jackson 3 codec과
`bluetape4k-images-spring-boot` runtime serialization 경계다. 동시성, 입력 경계,
Java deserialization, Spring 조건부 bean wiring, 공개 dependency graph가 포함되므로
Step 4-P를 적용한다.

| 위험 | 조기 신호 | 완화·검증 | 되돌림/재개 지점 |
|---|---|---|---|
| Jackson 3 API 또는 catalog 좌표가 실제 build와 불일치 | minimal compile 또는 `compileKotlin` 실패 | 공식/캐시 jar API 확인 후 direct BOM/databind/module-kotlin 좌표만 적용 | 의존성 변경 전 상태로 되돌리고 계획의 좌표 절을 재개정 |
| bounded decode가 base64/list를 먼저 materialize | oversized JSON이 `toOptions()` 이후에만 실패하거나 allocation/JFR 급증 | token preflight, bounded sink, InputStream test와 1/1/64 MiB heap matrix | codec 공개를 닫고 실패 테스트부터 다시 고정 |
| trusted mapper가 임의 module/mixin/deserializer를 통과시킴 | malicious `@JsonTypeInfo` 또는 custom deserializer가 실행됨 | external fixed mapper, internal capability, negative test, module allow-list | trusted API를 제거하고 fixed mapper만 유지 |
| Java serialization이 생성자 검증을 우회 | corrupt stream이 NaN/음수/두 결과 상태로 복원됨 | 모든 snapshot proxy/readObject, ObjectInputFilter, malformed stream fixture | Java serialization 공개 경계를 철회하고 Jackson-only로 전환 |
| runtime Path/CloudFront secret/원본 cause 노출 | JSON, `toString()`, startup/Actuator 진단에 경로·키·cause 문자열 포함 | sourceId validation, redacted wire boundary, sanitized exception test | 해당 Spring/codec 변경만 되돌리고 진단 경계를 먼저 수정 |
| Spring provider matrix가 현재 `@ConditionalOnBean`과 어긋남 | enabled provider가 조용히 bean을 생략하거나 disabled provider가 실패 | `ApplicationContextRunner` matrix를 먼저 고정하고 startup failure/no-op를 구분 | auto-configuration 단계로 돌아가 조건/순서를 재정의 |
| payload copy와 mapper 재구성이 동시 호출에서 heap/latency를 증폭 | barrier 16-worker test의 timeout, mismatch, OOME, allocation threshold 초과 | singleton typed reader/writer, streaming path, mandatory JMH/JFR evidence | benchmark gate를 닫고 snapshot/codec API 수정 |
| runtime `Serializable` 제거로 소비자 ABI/stream이 예기치 않게 깨짐 | classfile diff 또는 old stream 테스트가 예상 예외와 다름 | source/binary/Java stream matrix, migration README/CHANGELOG | 0.5.0 migration 문서와 additive snapshot API를 먼저 보완 |

## 중단 조건

P0/P1 보안·호환성·자원 상한이 남아 있거나, targeted test가 실패하거나, benchmark
freshness/heap evidence를 수집하지 못하면 구현·PR 단계로 진행하지 않는다. 해당 조건은
수정 후 영향을 받은 테스트와 독립 검토만 다시 실행한다.
