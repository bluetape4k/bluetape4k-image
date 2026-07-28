# bluetape4k-image 7-Tier review stack

날짜: 2026-07-04

## 배경

image repository review는 core image decoding, Ktor route, Spring storage/CDN
integration, Vips native backend, CAPTCHA verification을 다뤘다.

## 결정

- 기존 one-argument image decode helper는 source-compatible하게 유지하고 external input
  boundary용 bounded overload를 추가한다.
- malformed Ktor thumbnail payload는 caller input error로 다루면서 coroutine cancellation
  propagation을 보존한다.
- 이 module에 parallel client construction path를 추가하지 않고 S3 timeout/header behavior는
  `S3Operations` boundary에 문서화한다.
- Vips path input에서는 validation과 decode가 같은 byte에서 동작하도록 native path decode보다
  bounded byte snapshot을 우선한다.
- one-shot verification semantic은 바꾸지 않고 default CAPTCHA in-memory store를 save 시점에
  bound한다.

## 검증

- 각 stack layer를 commit하기 전에 수정된 module별 targeted test를 실행했다.
- stack 조립 후에는 full repository test가 여전히 필요하다.

## 향후 지침

- 향후 작업이 public KDoc을 건드리면 이번 localization 정책에 맞게 Korean prose로 전환한다.
- native-backed test는 native library가 없을 때 pending으로 변하지 않도록 pre-native
  input-boundary check를 작은 unit test로 분리한다.
