# #549 ImageClassifier provider-neutral 경계 lesson

## 배경

#549는 이미지 분류 기능을 구현하는 issue가 아니라, #548 model manifest와 #550 native
검증을 연결하는 API 설계 단계다. 이번 train에서는 문서만 변경하고 Kotlin source,
dependency, module, model bytes, native runtime을 추가하지 않는다.

## 결정

- 공통 API는 ImmutableImage와 명시적 ClassificationOptions를 받는다. public timeout은
  Java-friendly `java.time.Duration`으로 고정하고 provider가 사용하는 ORT/JNI/native type은
  public surface 밖에 둔다.
- `ClassifierCapabilities`는 top-k/batch/timeout/confidence semantics를 identity에 함께
  제공해야 하며, capability와 options/error 계약을 따로 두지 않는다.
- blocking ImageClassifier를 Java-friendly 기준 계약으로 두고, ImmutableImage의 sync/suspend extension을 별도 제공한다.
- suspend bridge는 blocking/native call을 IO dispatcher로 이동하지만, coroutine cancellation이 native inference를 즉시 중단한다고 주장하지 않는다.
- 결과 confidence는 0.0..1.0 probability semantics로 통일한다. confidence 내림차순, 동률 class index 오름차순을 deterministic rule로 고정한다.
- options의 topK, minConfidence, maxResults, maxPixels는 bounded value이며 provider가 값을 조용히 확대하지 않는다.
- CancellationException은 broad RuntimeException catch보다 먼저 재전파한다. `ImageClassifier`는
  `AutoCloseable`이고, OrtSession, Result, tensor와 pinned output은 정상·예외·cancellation·close
  race path에서 명시적으로 닫는다.
- bluetape4k-images-classification-api와 bluetape4k-images-classification-onnxruntime을 분리한다. API artifact에는 ORT/JNI/NDArray/Path/URL/mapper dependency를 올리지 않는다.
- Jackson 3는 private implementation-only canonical JSON codec의 기본값이다. kotlinx.serialization과
  default typing은 common public wire에 추가하지 않는다. DTO의 `Serializable` marker/`serialVersionUID`는
  workspace 규칙이며 JSON stream format과 분리한다.
- #548 manifest의 model bytes, labels, preprocessing, postprocessing, provenance와 license 상태가 classifier identity의 기준 원본이다.
- #551이 ADOPT를 기록하기 전에는 public API, ORT dependency, model auto-download와 module registration을 구현하지 않는다.

## 관찰한 miss와 surprise

1. 모델 파일 이름만 고정하면 결과 의미를 고정할 수 없다. labels, preprocessing,
   logits/probability 변환, tie-break와 legal provenance가 바뀌면 같은 image와
   runtime에서도 결과가 달라진다.
2. ORT environment는 JVM lifetime singleton이고 close가 no-op다. 일반적인 per-request
   use pattern을 environment에 적용하면 ownership을 잘못 설명하게 된다. `SessionOptions`를
   session보다 먼저 닫으면 안 되며, session/result/pinned output의 lifecycle을 분리해야 한다.
3. RunOptions의 terminate API가 있어도 coroutine cancellation과 native 작업 종료는
   동일하지 않다. API는 호출자 관찰 중단과 native cleanup을 구분해야 한다.
4. API module을 먼저 publish하고 나중에 ORT를 붙이면 public signature와 generated POM이
   provider type을 누출하기 쉽다. module graph·POM·APIElements 검사를 첫 Type-A PR에
   넣어야 한다.
5. deterministic JSON은 mapper 선택보다 schema·unknown field·duplicate field·trailing
   token·size limit가 먼저다. Jackson 3를 사용하더라도 fail-closed codec contract가
   없으면 fixture가 재현성을 보장하지 못한다.
6. docs-only baseline에서 Gradle test가 성공해도 ORT native provider가 동작한다는
   뜻은 아니다. 704 tests/18 skipped는 변경 전 core baseline으로만 기록한다.

## 재사용할 방어선

- 연구 문서에는 source URL, ref, retrieval date, 확인한 사실, 미확인 사실을 같은 표에 둔다.
- public contract에는 path/URL/credential/native handle을 넣지 않고 model manifest digest만 참조한다.
- result DTO는 provider type과 raw image/model bytes를 보유하지 않으며 list/array mutation을 caller에게 돌려주지 않는다.
- fake provider와 golden JSON으로 confidence tie, top-k, malformed output, capability mismatch, cancellation-before-start를 먼저 검증한다.
- ORT provider는 single-file model, managed root, SHA-256, labels hash, session/result/tensor close, sanitized error를 소유한다.
- implementation PR는 API → provider → catalog/BOM/consumer → CI/native → example/benchmark 순서로 쪼개고 각 PR를 revert 가능하게 둔다.
- independent reviewer 결과와 leader disposition을 같은 문장으로 합치지 않는다. timeout이면 bounded liveness evidence와 inline fallback을 별도 기록한다.
- adoption PENDING을 implementation PASS로 바꾸지 않는다. #550 native/CI와 #551 adoption이 없으면 상태는 IMPLEMENTATION_BLOCKED다.

## 후속 검증

- #550에서 Ubuntu x64·macOS ARM64 CPU artifact, Java 25 native access, ORT version/license/SBOM, BOM/consumer smoke를 검증한다.
- #551에서 동일 corpus quality, cold/warm latency, RSS/thread/session bound, model/weight/dataset license, NOTICE와 attestation을 결정한다.
- Type-A implementation에서는 API diff, generated POM leakage, Jackson 3 visibility, fake/golden fixture, cancellation/resource cleanup을 required check로 둔다.

## 범위와 Kotlin pattern 적용

이번 train은 research/spec/plan/lesson/review 문서만 변경했다. Kotlin source와
test를 변경하지 않았으므로 $bluetape-kotlin-patterns의 production implementation
check는 N/A다. 단, 설계에는 불변 data, defensive copy, 명시적 provider, structured
cancellation, use/close lifecycle이라는 저장소 Kotlin pattern을 반영했다. 이는
Kotlin 코드 검증 PASS가 아니라 후속 구현의 acceptance 기준이다.

## Writer DoD

- SPW-01: PASS — issue, 독자, 결정, 미해결 gate와 source를 고정했다.
- SPW-02: PASS — context, decision, outcome, miss/surprise, future guard를 포함했다.
- SPW-03: PASS — 한국어 technical register와 API/ORT/Jackson/command token을 보존했다.
- SPW-04: PASS — #548 research, #3 research, official ORT API와 후속 #550/#551을 연결했다.
- SPW-05: PASS — read-back에서 baseline test와 native adoption evidence를 혼동하지 않았다.

최종 상태: LESSON RECORDED / ADOPTION PENDING / IMPLEMENTATION BLOCKED
