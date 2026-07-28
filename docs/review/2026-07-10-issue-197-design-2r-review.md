# Issue #197 Design 2-R 검토

## 범위

구현 전에 `ImageLargeStreamingBenchmark` parity design을 검토했다. 검토는 성능, 안정성/lifecycle, 보안, 운영, 개발자 근거, library-user 문서/차트 우려를 다뤘다.

## 판정

**FAIL — P0: 0, P1: 5.** 모든 P1 발견 사항을 수정하고 영향을 받은 관점을 다시 실행하기 전에는 implementation plan을 작성하거나 benchmark를 변경하지 않는다.

## P1 발견 사항

1. **Cross-backend readiness가 통과 조건이 아니다.**
   - 현재 vips 경로는 초기화가 불가능할 때 `null`을 소비할 수 있으므로,
     성공한 task와 vips row 존재만으로 실제 libvips 측정을 증명하지 못한다.
   - 근거: `ImageLargeStreamingBenchmark.kt:179-181, 368-373, 395-397`;
     design `:100-101, 140-142`.
   - 수정: Java 25 FFM/libvips readiness와 skip되지 않은 vips 실행을 필수
     preflight 근거로 추가한다. vips를 사용할 수 없으면 이 cross-backend 결과의
     publication blocker로 취급한다.

2. **전체 근거 lifecycle이 완결되지 않았다.**
   - primary result, GC-profiler addendum, 각 benchmark README의 두 위치,
     root `README.md`/`README.ko.md`가 현재 asymmetric workload의 claim을
     담고 있다. 이전 상세 report도 shared chart를 embed하므로, 유효하지 않은
     table과 새 visual이 섞일 수 있다.
   - 근거: `benchmark/images-benchmark/README.md:119-143, 347-360`,
     `benchmark/images-benchmark/README.ko.md:121-145, 350-363`,
     `README.md:82-89`, `README.ko.md:71-78`,
     `large-streaming-2026-06-05.md:41-105`.
   - 수정: GC addendum과 allocation claim을 refresh할지 제거할지 결정해
     문서화하고, root README 양쪽 locale을 갱신한다. 이전 report는 invalid
     value 옆에 현재 shared chart를 embed하지 못하도록 visible superseded
     page로 만든다.

3. **차트의 semantic label이 올바르지 않다.**
   - 값은 `large-photo`와 `ocr-document`에 대한 것이지만 legend는 `JPEG`와
     `PNG`라고 표시한다. 비교하는 두 경로는 모두 JPEG로 encode한다. 렌더링된
     scale text와 tick 동작도 하나의 일관된 해석이 필요하다.
   - 근거: `docs/scripts/generate-readme-visual-assets.py:850`;
     `ImageLargeStreamingBenchmark.kt:69-71, 183-185, 199-202, 220-223`.
   - 수정: design acceptance criteria에 scenario legend label,
     backend/boundary category, report/README/chart label-value parity를
     요구한다.

4. **측정 configuration에 권위 있는 precedence contract가 없다.**
   - source annotation은 warmup 2회를 선언하지만 generated Gradle task와 기존
     raw result는 warmup 1회와 measurement 3회를 사용한다. design은 override를
     설명하거나 새 raw JSON이 effective settings를 증명하도록 요구하지 않고
     후자를 기록한다.
   - 근거: `ImageLargeStreamingBenchmark.kt:61-63`,
     `benchmark/images-benchmark/build.gradle.kts:98-107`.
   - 수정: Gradle task를 권위 있는 measurement surface로 명시하고 forks,
     warmups, iterations, duration, mode, time unit에 대한 raw-result 검증을
     요구한다.

5. **Chart visual validation과 generation scope가 닫히지 않았다.**
   - generator는 target chart만이 아니라 전체 diagram/chart set을 쓴다. design은
     XML validation을 요구하지만 필요한 CairoSVG render, full-size PNG inspection,
     PNG validity/dimension check, 관련 없는 generated asset에 대한
     allowlist/restore rule을 빠뜨렸다.
   - 근거: `generate-readme-visual-assets.py:855-861`; diagram skill
     common/chart contract; 이 worktree에서 관측한 generator side effect.
   - 수정: target SVG/PNG용 one-asset visual QA ledger와 관련 없는 asset을
     명시적으로 복원하는 generated-file allowlist를 추가한다.

## P2/P3 후속 작업

- P2: commit 전에 갱신된 raw JSON/report command metadata에서 user home path,
  host name, token-like JVM property를 제거한다.
- P2: benchmark teardown 전에 setup이 실패했다면
  `bt4k-image-large-streaming-*` temporary-directory residue가 남았는지 확인한다.
- P3: benchmark command 근처에 effective Gradle-task override를 문서화한다.

## 긍정 근거

- 선택한 parity 방향은 타당하다. Scrimage만 `GRAYSCALE_FILTER`를 적용하고,
  vips는 resize와 JPEG encode를 수행한다.
- 기존 benchmark fixture는 deterministic하며, 정상 output path는 `finally`
  cleanup을 사용한다.
- 제안한 변경은 새 dependency, native-access 확장, untrusted write path를
  도입하지 않는다.

## 필수 재검토

P1 수정 뒤 성능, 안정성, 보안, 아키텍처, developer/API, library-user 관점을
다시 실행한다. main session은 이후 findings를 normalize하고 Step 3-R 전에
`P0=0`과 `P1=0`을 기록해야 한다.

## 수정 후 재검토

**PASS — P0: 0, P1: 0.** 수정된 design은 implementation planning 전 user
review gate로 넘어갈 수 있다. 이 review 중 source나 benchmark artifact는
변경하지 않았다.

| 관점 | 결과 | 확인한 근거 |
| --- | --- | --- |
| 성능 | PASS | Color-preserving parity, 권위 있는 Gradle/JMH settings, raw-row evidence, chart scenario/scale contract |
| 안정성/SRE and security | PASS | Fail-fast libvips readiness, run-owned temporary residue, metadata scrubbing, generator allowlist, SVG/PNG QA |
| Architecture/scope | PASS | 새 근거와 archived 근거 lifecycle, GC addendum policy, 모든 README surface, 좁은 regression guard |
| 개발자/API | PASS | Cross-backend execution contract, configuration precedence, raw-data traceability, artifact generation boundary |
| 라이브러리 사용자 | PASS | benchmark README 양쪽 위치와 locale, root README locale, report supersession, chart label과 rendered visual check |

### 비차단 후속 작업

- implementation plan이 exception-safe benchmark-setup cleanup을 설명하고,
  fail-fast error에 libvips initialization cause를 보존하도록 한다.
- raw-data validation이 예상 backend/scenario row를 enumerate하게 한다.
- Scrimage path를 확인할 때 shared transform에 grayscale filter가 없고, 영향을
  받는 모든 benchmark method가 그 transform 뒤에도 JPEG-encode하는지 검증한다.
