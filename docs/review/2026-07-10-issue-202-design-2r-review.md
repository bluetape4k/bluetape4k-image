# Issue #202 Step 2-R 설계 검토

## 범위

구현 전에 `bluetape4k-images-vips-api`의 승인된 dependency-boundary design을 검토한다. 검토는 public opt-in contract, Java 21/25 backend compilation boundary, publication metadata, documentation migration, verification feasibility를 다룬다.

## 발견 사항과 수정

첫 review pass는 세 가지 blocking gap을 발견했다:

1. Java 21/25 backend와 API test source가 여전히 `IncubatingImageApi`를 직접
   import한다. main API dependency를 제거하면 compilation이 깨진다.
2. Maven POM inspection만으로 Gradle normal variant가 `bluetape4k-images`를
   생략하는지 증명할 수 없다. 의도적인 test-fixture variant는 별도로 확인해야
   한다.
3. design이 public `@VipsIncubatingApi` propagation과 implementation-only
   `@OptIn`을 구분하지 않아 stable codec report usability가 모호했다.

이제 design은 모든 Vips API와 Java 21/25 main/test opt-in을 새 marker로
migrate하고, POM과 Gradle Module Metadata를 모두 validate하며, public marker를
AVIF/HEIC enum entry에만 적용하고, report container는 implementation opt-in
상태로 유지하되 caller에게는 stable하게 둔다. 또한 marker target/message
contract, strict compiler-fixture verification, README example, image-module KDoc
boundary, rollback criteria를 바로잡는다.

## 관점별 결과

| 관점 | 최종 결과 | 근거 |
|---|---|---|
| 성능 | PASS — P0/P1/P2/P3: 0/0/0/0 | annotation/import 및 metadata 변경은 JNI/FFM resource, codec, benchmark hot path를 바꾸지 않는다. |
| 안정성 | PASS — P0/P1/P2/P3: 0/0/0/0 | API와 backend main/test migration, strict compiler fixture가 compilation failure mode를 닫는다. |
| 보안 | PASS — P0/P1/P2/P3: 0/0/0/0 | normal Gradle variant와 POM은 intentional fixture와 별도로 확인하며, marker propagation이 명시적이다. |
| 운영자/release | PASS — P0/P1/P2/P3: 0/0/0/0 | verification은 generation/compile/test task만 사용하며, POM 또는 normal metadata leakage가 있으면 rollback은 PR 전에 멈춘다. |
| 사용자/호출자 | PASS — P0/P1/P2/P3: 0/0/0/0 | 모든 AVIF/HEIC README example에 import와 scoped opt-in이 들어가며, stable report는 계속 사용 가능하다. |
| 개발자/API | PASS — P0/P1/P2/P3: 0/0/0/0 | target set, ABI boundary, fixture scope, no-dependency compiler fixture approach가 현재 Kotlin/Gradle 구조와 맞다. |

## 최종 판정

**PASS.** P0 또는 P1 발견 사항은 남아 있지 않다. 승인된 design은 Step 3 implementation planning으로 넘어갈 준비가 됐다. 이 review 중 source code, publication, release, pull request, merge action은 발생하지 않았다.
