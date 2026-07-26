# Issue #299 구현 계획 검토

**검토 대상**

- `docs/superpowers/specs/2026-07-27-issue-299-image-intelligence-api-design.md`
- `docs/superpowers/plans/2026-07-27-issue-299-image-intelligence-api-plan.md`
- 현재 `develop`의 이미지, OCR, 감지, 바코드, workflow API

**검토 방식**

구현 전 계획을 성능, 안정성, 보안, 운영, 개발자/API, 사용자/호출자 관점으로
각각 다시 읽었다. 저장소에 있는 실제 확장 함수, Spring Boot 예제, version catalog,
`WorkContext` 계약과 대조했다.

## 1. 성능 관점

### 발견과 보강

- **P1 — blocking 공급자 실행 방식이 현재 라이브러리 계약과 달랐다.**
  설계와 초안은 `runInterruptible`을 직접 사용한다고 적었지만 현재 라이브러리는
  `suspendExtractOcr`, `suspendDetectRegions`, `suspendExtractBarcodes`에서
  dispatcher 전환을 제공한다. 설계와 계획을 실제 API 재사용 방식으로 고쳤다.
- **P2 — 매우 작은 양의 제한 시간이 0밀리초로 변환될 수 있었다.**
  `Duration > ZERO` 대신 `toMillis() > 0`을 검증하고 나노초 경계 테스트를 추가했다.
- 압축 바이트를 `QualifiedImage`에 보관하지 않고, 크기 탐색 뒤 한 번만 디코딩하며,
  공급자별 semaphore와 병렬 실행 상한을 검증하는 계획은 충분하다.

## 2. 안정성 관점

### 발견과 보강

- **P1 — `GuardedAnalysisRunner`의 제네릭 계약이 의사코드에만 암시돼 있었다.**
  `provider`, `Duration`, `Semaphore`, `isEmpty`, `suspend block`의 정확한 시그니처와
  예외 처리 순서를 계획에 추가했다.
- **P1 — `demo`와 `native-ocr` 동시 활성화가 bean 순서에 의존할 수 있었다.**
  명시적인 profile 표현식과 시작 시점 guard를 추가하고 context 실패 테스트를
  계획에 포함했다.
- **P2 — workflow 결과 키가 없거나 타입이 다를 때의 실패 계약이 불명확했다.**
  `WorkContext.requireResult`와 안정된 `missing_workflow_result` 오류를 추가했다.
- 내부 timeout은 해당 분석 결과로 바꾸고 외부 `CancellationException`은 다시
  던지는 순서, permit 복구, 후속 요청 성공 검증은 유지했다.

## 3. 보안 관점

### 발견과 보강

- **P1 — 계획과 승인된 설계의 과대 입력 HTTP 상태가 달랐다.**
  기존 Spring barcode 예제와 계획의 `413` 계약에 맞춰 설계도 압축 바이트, 한 변,
  픽셀 한계 초과를 `413 ProblemDetail`로 고쳤다.
- **P2 — 공급자 예외를 stack trace와 함께 기록하면 OCR 본문이나 native 경로가
  간접 노출될 수 있었다.** 로그에는 request/provider/status/reason/elapsed만 남기고
  원본 예외 메시지, stack trace, 분석 payload를 남기지 않도록 계획과 검증을 보강했다.
- MIME과 magic byte 일치, 크기 탐색 전후의 바이트 한계, 디코딩 전 픽셀 한계,
  응답의 raw object 차단은 충분하다.

## 4. 운영 관점

### 발견과 보강

- **P2 — 관측성 요구를 문서만으로 확인하고 있었다.**
  완료와 실패 요청의 구조화 로그 필드, payload·경로·예외 비노출을 검증하는
  `ImageIntelligenceObservabilityTest`를 계획에 추가했다.
- native OCR이 실행 중 취소에 협조하지 않을 수 있다는 한계와 엄격한 SLA에서
  process 또는 원격 worker로 격리해야 한다는 운영 경계가 명시돼 있다.
- 새 예제의 Examples workflow 등록, `actionlint`, clean test, 관련 모듈 회귀
  검증 순서는 적절하다.

## 5. 개발자/API 관점

### 발견과 보강

- **P1 — 제네릭 `AnalysisResponse<T>.value`가 설계의 `ocr.result`,
  `detection.regions`, `barcodes.items` 응답과 달랐다.**
  분석 종류별 DTO와 `AnalysisStatus`를 명시해 JSON 필드와 상태별 불변식을
  고정했다.
- **P2 — 무작위 request ID 때문에 HTTP 테스트가 불안정해질 여지가 있었다.**
  기본값은 `UUID.randomUUID()`를 사용하되 `requestIdProvider`를 주입하도록
  계획을 보강했다.
- 실제 ZXing 판독, fixture OCR·감지, disabled 공급자를 같은 좁은 adapter 계약으로
  조합하므로 예제 목적과 모듈 책임이 일치한다.

## 6. 사용자·호출자 관점

### 확인 결과

- HTTP `200`과 분석 업무 성공을 분리하고, 호출자가 `COMPLETED`, `PARTIAL`,
  `FAILED` 및 정책 결정을 함께 확인하도록 설명한다.
- 감지 결과가 비어 있는 경우와 공급자가 실패한 경우를 구분하므로 실패를 자동
  허용으로 오판하지 않는다.
- 방문증은 구체적인 학습 시나리오지만 정책 교체 지점을 분리해 배송·상품 라벨에도
  같은 검증·병렬 처리·부분 실패 구조를 적용할 수 있다.
- 영어·한국어 README, dark SVG·PNG, 정상·부분 실패·외부 취소 흐름을 함께 제공하는
  계획이 독자 관점의 설명 범위를 충족한다.

## 7. 수용 기준 추적성과 실행 가능성

- 설계의 16개 수용 기준이 Task 1–9에 모두 연결돼 있다.
- 각 구현 task는 파일, RED, GREEN, 검증 명령, rollback 지점, Lore commit을 가진다.
- placeholder 검색 결과 `TBD`, `TODO`, `FIXME`, `implement later`, `fill in`은 없다.
- versioned manual과 배포 BOM을 건드리지 않는 경계, PR 생성 후 merge-ready에서
  멈추는 경계가 명시돼 있다.

## 8. 최종 판정

| 심각도 | 미해결 건수 |
|---|---:|
| P0 | 0 |
| P1 | 0 |
| P2 | 0 |
| P3 | 0 |

현재 계획은 구현 시작 전에 사용자가 검토할 수 있는 상태다. 구현은 승인 후
Task 1부터 순서대로 수행한다.
