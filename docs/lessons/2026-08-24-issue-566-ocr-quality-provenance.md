# Issue #566 OCR benchmark 품질·provenance 보강 교훈

## 맥락

Issue #563의 v2 benchmark는 `clean-text-v2-001` 한 개 fixture를 두 JMH task에
연결했지만, trial setup에서 direct OCR 경로만 `expectedOutcome`을 확인했다.
또한 manifest의 benchmarkable ID와 JMH `@Param`, 사용한 Tesseract
`traineddata`, raw report hash를 자동으로 대조할 수 없었다.

## 결정

- `TesseractOcrExtractionBenchmark`의 trial setup에서
  `extractText`와 `preprocessAndExtract` 결과를 모두 `fixture.verifyOutput`으로
  검증한다.
- `build.gradle.kts`가 manifest의 `TEXT`/`EMPTY` fixture ID와
  `@Param("fixtureId")` 집합을 읽어 exact equality가 아니면 fail-fast한다.
- 기존 baseline receipt에 `model-provenance.json`을 추가하고
  `run-manifest.json`에서 그 파일의 SHA-256을 고정한다. 각 model entry는
  언어, `*.traineddata` 식별자, 요청 경로, 해석 경로, byte 수, SHA-256을 가진다.
- `validateOcrBenchmarkReceipt` task가 fixture manifest hash, raw JSON의 단일
  trailing LF, report hash, model provenance hash와 언어 집합을 자동 대조한다.
  OCR benchmark report validator도 같은 EOF 규칙을 사용한다.

새 dependency, production OCR API, provider 교체는 추가하지 않았다. 9개
시나리오·27개 fixture, CER/WER, cold/warm latency, RSS 측정은 Issue #565의
후속 범위로 유지한다.

## 결과

현재 baseline receipt는 `eng.traineddata`의 `4,113,088` bytes와 SHA-256
`7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2`를
재현할 수 있다. 모델 경로는 host-specific 값이므로 파일이 존재한다고 가정하지
않고, receipt의 identity와 hash 형식·언어 집합을 검증한다.

## 검증

- RED: 새 contract test가 두 OCR 경로 검증 호출과 receipt validator 연결 부재로
  2건 실패했다.
- GREEN: `./gradlew :bluetape4k-images-benchmark:test --tests io.bluetape4k.images.benchmark.OcrBenchmarkContractTest --console=plain` — 4/4 PASS.
- `./gradlew :bluetape4k-images-benchmark:validateOcrBenchmarkReceipt --console=plain` — receipt, report EOF/hash, model provenance PASS.
- `./gradlew :bluetape4k-images-benchmark:benchmarkOcrLatencyBenchmark --console=plain` — direct `233.792 ± 71.728 ms/op`, preprocess `204.332 ± 58.534 ms/op`, 두 row와 validator PASS.
- `./gradlew :bluetape4k-images-benchmark:benchmarkOcrThroughputBenchmark --console=plain` — direct `4.547 ± 0.280 ops/s`, preprocess `4.511 ± 1.822 ops/s`, 두 row와 validator PASS.
- 기준선: 변경 전 `./gradlew :bluetape4k-images-benchmark:test --console=plain` — 104/104 PASS.
- 문서 범위: `README.md`, `README.ko.md`, 이 lesson의 command·fixture·receipt 경로를
  현재 branch source와 대조했다.

## 놓치기 쉬운 점과 다음 guard

JMH가 생성하는 report는 EOF에 여러 개의 LF를 붙일 수 있어 첫 검증에서
fail-fast 되었다. build output을 `normalizeOcrRawReport`로 한 번 정규화한 뒤
동일 validator를 적용해야 committed raw receipt의 단일 LF 규칙과 충돌하지 않는다.
JMH `@Param`을 수동으로 갱신하면 manifest와 drift할 수 있으므로 fixture를
추가하는 PR은 manifest와 benchmark source를 함께 수정하고
`validateOcrBenchmarkReceipt`를 실행해야 한다. model path는 운영 환경마다
달라질 수 있으므로 절대 경로 문자열을 공통 설정으로 승격하지 말고, 실행 시점의
모델 identity receipt로 남긴다. 전체 corpus 확장 시에는 benchmarkable fixture의
언어 집합만 provenance 대상에 포함하고, `ERROR` negative fixture를 OCR 입력에
섞지 않는다.

## 독립 review 범위

최종 diff에는 별도 read-only reviewer lane의 7-Tier 결과를 첨부한다. reviewer가
확인할 범위는 두 OCR 경로의 fail-fast 계약, exact parameter equality, model
provenance/hash receipt, raw EOF 규칙, 한·영 문서 구조이며, P0/P1은 PR을
차단한다.
