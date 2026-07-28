# Issue #197 구현 검토 (6-R)

날짜: 2026-07-10
범위: `origin/develop...perf/issue-197-large-streaming-parity`, worktree의
최종 documentation 및 generated-chart repair 포함.

## 결과

**PASS — P0: 0, P1: 0**

large-streaming comparison은 이제 Scrimage와 Java 25 FFM libvips에 대해 같은 color-preserving decode-resize-JPEG-encode contract를 측정한다.
`ImageLargeStreamingBenchmark`는 FFM을 요구하고, JNI로 조용히 대체하거나 null
row를 내보내는 대신 fail fast한다.

## 독립 검토 관점

| 관점 | P0 | P1 | P2 | 판정 |
| --- | ---: | ---: | ---: | --- |
| 성능 / benchmark | 0 | 0 | 1 | PASS |
| 안정성 / lifecycle | 0 | 0 | 1 | PASS |
| 보안 / evidence handling | 0 | 0 | 0 | PASS |
| 운영 / runbook | 0 | 0 | 0 | PASS |
| 개발자 / API | 0 | 0 | 1 | PASS |
| 라이브러리 사용자 / documentation | 0 | 0 | 0 | PASS |

## 발견하고 수정한 차단 사항

1. rebase된 large-streaming SVG가 stale 상태였고 CairoSVG output에서 middle-dot
   glyph가 tofu로 렌더링됐다. 현재 chart generator에서 target SVG/PNG만 다시
   생성했다. published PNG는 3120x1720에서 확인했고 이제 ASCII
   `ms/op - lower is better` label을 사용한다.
2. README text가 vips `Path`를 범용 최강 large-file throughput/memory option으로
   잘못 홍보했다. commit된 짧은 Java 25 snapshot은 그 규칙을 입증하지 않는다.
   EN/KO docs는 이제 `Path`를 API/lifecycle boundary로만 설명한다.
3. Java 21 full-suite command가 FFM 전용 `ImageLargeStreamingBenchmark`를 포함할
   수 있었다. EN/KO runbook은 이제 이름이 지정된 JNI-compatible benchmark task를
   사용하고 FFM 전용 large streaming을 명시적으로 제외한다. Java 25 full-suite
   command는 이를 명시적으로 포함한다.
4. 첫 documentation wording은 non-`Path` vips load만 bounded라고 암시했다.
   source inspection 결과 `Path`를 포함한 모든 현재 vips input overload가 50 MiB
   guard 안에서 compressed input을 validate하고 buffer한다. EN/KO root 및
   benchmark documentation은 이제 이 invariant를 명시한다.

## 검증 근거

- `:bluetape4k-images-benchmark:test --tests '*ImageLargeStreamingBenchmarkContractTest'` 통과.
- `:bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25` 통과.
- 새 Java 25 `benchmarkLargeStreamingBenchmark -Pvips.impl=java25` run이 16개
  row를 모두 완료하고 local raw JSON artifact를 생성했다. 정상적인 short-run
  score variance는 committed evidence snapshot에 복사하지 않았다.
- 통제된 invalid FFM library override가 예상한 fail-fast benchmark error row를
  만들었고 temporary-run residue를 남기지 않았다.
- Primary 및 GC JSON artifact는 structural, metadata, method/scenario,
  sensitive-string gate를 통과했다. SVG는 XML-valid이고 target PNG도 valid이다.
- Java 21 named-task documentation은 Gradle `--dry-run`으로 확인했다.
- `git diff --check` 통과.

## 비차단 후속 작업

- commit된 raw evidence는 compressed fixture byte size를 기록하지 않는다. 향후
  evidence refresh에서 comparison에 필요해지면 추가할 수 있다.
- source contract test는 reflective FFM initialization을 실행하지 않는다. 새
  success/failure benchmark run이 이 release candidate를 cover하지만, 격리된
  automated startup/cleanup regression test가 있으면 반복 가능한 coverage가
  좋아진다.
- `Path`와 stream path는 현재 모두 bounded compressed input을 buffer한다. 향후
  API 작업은 benchmark boundary name만 보고 native streaming behavior를 추론하면
  안 된다.

## 인계

Step 6-R은 완료됐다. 구현은 pre-PR lesson, commit, PR review, CI gate로 진행할
수 있다. merge는 여전히 명시적인 사용자 승인 대상이다.
