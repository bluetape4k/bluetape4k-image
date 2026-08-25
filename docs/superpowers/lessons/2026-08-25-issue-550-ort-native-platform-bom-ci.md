# #550 ORT Java native·platform·BOM·CI 연구 lesson

## 배경

#550은 이미지 분류 backend를 구현하는 issue가 아니라, #549 provider-neutral API 뒤에
ORT Java provider를 붙일 수 있는지 검증하는 evidence contract를 만드는 issue다.
문서 train에서는 production Kotlin, dependency, module, model bytes, native runtime,
CI YAML을 변경하지 않는다. 실제 채택은 #551의 `ADOPT` 뒤에만 가능하다.

## 결정

- CPU `com.microsoft.onnxruntime:onnxruntime`을 기본 후보로 두고
  `onnxruntime_gpu`와 CUDA/cuDNN은 opt-in/manual/nightly로 격리한다.
- Linux x64를 CPU baseline으로, macOS ARM64와 Windows x64를 별도 consumer smoke로
  둔다. 공식 문서의 artifact 표기만으로 ARM64 지원을 선언하지 않는다.
- Java binding의 일반 실행 기준(Java 8+)과 이 저장소의 Java 25 consumer 호환성은
  분리한다. Java 25 compile/runtime/native smoke가 없으면 지원을 주장하지 않는다.
- `OrtEnvironment`는 JVM lifecycle host object이며 per-request `close()` 대상이 아니다.
  `SessionOptions`는 모든 session 뒤에 닫고, session/result/tensor/pinned output의
  native ownership을 각각 검증한다.
- provider fallback과 EP 선택을 receipt에 기록한다. GPU 요청이 CPU로 조용히
  내려가면 성공으로 표시하지 않는다.
- exact ORT version, model/manifest digest, artifact SHA-256, license, SBOM/NOTICE,
  Java/OS/arch/EP를 하나의 provenance receipt로 묶는다. 조사일 기준 Maven Central
  관찰값 `1.29.0`은 latest 선언이 아니다.
- PR fixture는 checked-in tiny/no-network model 또는 fake provider만 사용한다.
  remote auto-download, untrusted external data, custom op를 기본 차단한다.
- public error는 model path, credential, host path, native stack trace를 노출하지
  않고 bounded reason으로 정규화한다.
- benchmark는 cold/warm latency, p50/p95/p99, RSS/native memory, thread/session
  count, concurrency/session reuse를 protocol로 고정하지만 실제 수치는 #551 뒤에
  수집한다.
- API와 ORT provider, catalog/BOM/consumer, CI/native, example/benchmark를 PR-A~E
  stacked train으로 분리한다. 하나의 broad PR로 합치지 않는다.

## 관찰한 miss와 surprise

1. Java API가 jar에 native resource를 포함해도 모든 OS/arch 조합이 자동으로
   지원되는 것은 아니다. macOS ARM64는 소비자 smoke로 확인해야 하며 host library가
   우연히 설치된 환경을 green으로 세면 안 된다.
2. `OrtEnvironment.close()`가 no-op이라는 사실은 일반적인 `use {}` lifecycle과
   다르다. environment, session, result, tensor의 ownership을 한 계층으로
   설명하면 leak 또는 premature close를 숨긴다.
3. `SessionOptions`를 session보다 먼저 닫을 수 없다. session pool과 close race를
   명시하지 않으면 cancellation 경로에서 native use-after-free가 생길 수 있다.
4. `Result.close()`와 pinned output ownership은 동일하지 않다. output mapper가
   native memory를 heap object로 복사하는 시점을 fixture로 고정해야 한다.
5. GPU EP가 설치됐다는 사실만으로 GPU 실행을 증명하지 못한다. active EP, fallback,
   CUDA/cuDNN/driver/VC++ matrix를 receipt에 함께 기록해야 한다.
6. ORT version 하나만 pin해도 model IR/opset, labels, preprocessing/postprocessing,
   license/provenance가 고정되지 않는다. #548 manifest digest가 함께 있어야 한다.
7. docs-only 또는 old-SHA CI green은 native runtime evidence가 아니다. skipped와
   unavailable은 별도 상태로 남겨야 한다.
8. BOM constraint가 맞아도 APIElements/runtimeClasspath에 provider/native type이
   누출되면 consumer가 ORT를 뜻하지 않게 받는다. generated POM와 Gradle metadata를
   둘 다 검사해야 한다.

## 재사용할 방어선

- ORT source URL, retrieval date, exact coordinate/version, license, SHA, model digest,
  Java/OS/arch/EP를 source ledger에 함께 기록한다.
- PR/no-network fixture와 scheduled/native fixture를 분리하고, native check는
  sequential로 실행한다.
- external data는 managed root containment와 link policy 없이는 거부하고 custom
  op와 remote download는 첫 채택 범위에서 차단한다.
- sanitized reason을 public contract로 두고 raw path, URL, credential, native stack
  trace는 metric/public message에서 제거한다.
- #551 `ADOPT` 전에 dependency·module·native runtime을 추가하지 않는다. `DEFER`면
  provider train을 멈추고 API 문서와 fake fixture만 유지한다.
- 독립 reviewer timeout은 PASS가 아니다. stall/probe/inline fallback을 별도 receipt와
  review 문장으로 남긴다.
- public 문서와 PR body는 한국어로 작성하되 `OrtEnvironment`, Maven coordinates,
  commands, URLs, version token은 번역하지 않는다.

## 후속 검증

- exact ORT version을 선택하고 Maven POM/Gradle metadata/license/SBOM/NOTICE/SHA를
  보존한다.
- Linux x64 CPU native load/run/close와 Java 25 consumer를 required evidence로 만든다.
- macOS ARM64·Windows x64 consumer smoke에서 실제 architecture/native loader를
  확인하고 unavailable이면 지원을 `DEFER`한다.
- external-data/custom-op/path traversal/corrupted model/unsupported opset/timeout/
  cancellation fixture를 fail-closed로 실행한다.
- BOM/APIElements/runtimeClasspath/generated POM에 ORT/JNI/native type이 API
  consumer로 새지 않는지 검사한다.
- cold/warm latency, RSS/native memory, thread/session bound, concurrency/reuse와
  same-corpus quality를 #551 adoption decision에 제출한다.

## 범위와 Kotlin pattern 적용

이번 train은 research/plan/review/lesson 문서와 workflow receipt만 변경했다. Kotlin
production source와 test를 바꾸지 않았으므로 `$bluetape-kotlin-patterns`의 코드
검증은 N/A다. 다만 후속 구현의 acceptance 기준으로 불변 결과, 명시적 provider,
structured cancellation, `use`/close lifecycle, dependency boundary를 문서에 고정했다.

## Writer DoD

- `SPW-01`: PASS — issue, 독자, 결정, 미해결 gate와 source를 고정했다.
- `SPW-02`: PASS — context, decision, miss/surprise, 방어선, 후속 검증을 기록했다.
- `SPW-03`: PASS — 한국어 technical register와 ORT/JVM/Gradle/CI token을 보존했다.
- `SPW-04`: PASS — #548/#549/#551, 공식 ORT/ONNX/Maven source와 repository rules를
  연결했다.
- `SPW-05`: PASS — native/benchmark/adoption evidence를 문서 설계와 혼동하지 않았다.

최종 상태: `LESSON RECORDED / RESEARCH COMPLETE / IMPLEMENTATION BLOCKED BY #551`
