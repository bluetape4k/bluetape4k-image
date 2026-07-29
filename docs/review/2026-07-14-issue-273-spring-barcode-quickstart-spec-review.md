# Issue #273 Spring Boot Barcode Quickstart Spec 검토

## 범위

- Artifact: `docs/superpowers/specs/2026-07-14-issue-273-spring-barcode-quickstart-design.md`
- Artifact 종류: spec
- Research basis: live issue #273, 기존 barcode API/ZXing source와 tests, `spring-boot-image-api`, `Examples.yml`, module-registration guard, issue #272 deterministic barcode fixture
- 관점: performance, stability, security, operator/Ops, developer/API, user/caller, 이후 main-session integration

active native-agent interface는 필수 `agent_type` field를 노출하지 않고 세션에는 여섯 개의 free lane도 없다. `model-routing.md`에 따라 각 필수 lens는 agent role을 지어내지 않고 별도 read-only main-session pass로 실행했다.

## 초기 발견 사항

| Priority | 관점 | 근거 | 필요한 수정 | 해결 |
|---|---|---|---|---|
| P1 | Security | 초기 dimension guard는 `probeImageDimensions`만 명명했는데, 해당 implementation은 ImageIO reader에 의존하고 upload allowlist에는 WebP가 포함된다. | full decode 전에 WebP dimension을 읽을 수 있는 bounded metadata-report fallback을 정의하고 세 accepted format을 모두 test한다. | Sections 6.3과 9에서 수정. |
| P1 | Developer/API | 초기 response shape가 library `BarcodeResult` 전체를 serialize해 HTTP contract로 의도하지 않은 backend metadata, point, future field를 노출할 수 있었다. | provider-neutral result를 text, normalized format, provider name만 담는 bounded DTO로 map한다. | Section 6.4에서 수정. |
| P1 | User/caller | 선택한 copied QR fixture가 issue #272 benchmark payload를 new-user quickstart에 노출했다. | stable payload `bluetape4k-barcode-quickstart`로 module-owned QR을 생성하고 benchmark directory를 runtime source set에서 제외한다. | Sections 6.4와 8에서 수정. |
| P2 | Performance | 초기 fixture component contract가 모든 GET마다 classpath I/O를 반복하는지 설명하지 않았다. | fixed resource를 startup에 한 번 load/validate하고 mutable byte array sharing을 피한다. | Section 6.1에서 수정. |

## 재실행 판정

| 관점 | 판정 | 근거 |
|---|---|---|
| Performance | PASS | Sections 6.1과 6.3은 fixture를 한 번 load하고, byte read 전에 upload size를 제한하며, blocking I/O와 CPU decode work를 적절한 coroutine dispatcher로 dispatch한다. |
| Stability | PASS | Sections 6.1, 6.3, 8, 9, 12는 missing fixture의 startup failure, immutable request isolation, cancellation propagation, deterministic failure case, stateless rollback을 정의한다. |
| Security | PASS | Sections 6.3, 6.4, 7은 encoded/decoded size를 bound하고, untrusted content type 이후 실제 image structure를 validate하며, error를 sanitize하고 payload/backend metadata를 생략하며 unauthenticated local-example boundary를 문서화한다. |
| Operator/Ops | PASS | Sections 6.4와 12는 stable status/error mapping, persistent state나 migration 없음, default port behavior, startup diagnostics, directory-level rollback을 정의한다. |
| Developer/API | PASS | Sections 5-6은 ZXing construction을 configuration에 두고, service에서 `BarcodeReader`를 사용하며, controller/service/fixture/DTO responsibility를 isolate하고 production API change를 피한다. |
| User/caller | PASS | Sections 4, 6.2, 7, 10, 13은 real upload path, 세 reproducible scenario, exact command/response, bilingual docs, capability limit, production-deployment warning을 제공한다. |

## 통합 판정

- selected dedicated module은 기존 storage-focused Spring example 확장보다 좁고 teachable하다.
- upload validation, malformed normalization, no-result semantic, provider boundary, fixture ownership, HTTP DTO shape, rollback이 명시적이고 testable하다.
- full registration chain은 settings, AGENTS, Examples workflow, README locale, project listing, diagram QA로 표현된다.
- BOM/catalog, publication, Kover aggregation, benchmark update, native/JNI, OCR, Docker, Testcontainers는 non-published pure-JVM N/A evidence가 구체적이다.
- Chart N/A는 evidence-backed이다. 이 issue에는 measured series가 없고, README diagram 세 개는 여전히 required visual artifact다.
- Latest convergence: **P0=0, P1=0**. P2 fixture-I/O finding은 수정됐다.

Required checks: 7/7; N/A: 0; Blocked: 0.
