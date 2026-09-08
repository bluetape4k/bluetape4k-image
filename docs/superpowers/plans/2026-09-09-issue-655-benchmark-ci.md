# Issue #655 benchmark CI 연결 계획

> **목표:** `benchmark/images-benchmark`만 변경한 PR에서도 benchmark source set compile, native 불필요 단위 테스트, 커밋된 receipt 검증이 실행되고 required-check 집계와 artifact로 근거를 보존하도록 한다.

## 범위와 계약

- `ci.yml`의 기존 `dorny/paths-filter`, module test job, `ci-status` required-test 판정, JUnit artifact 관례를 재사용한다.
- `nightly-tests.yml`에도 동일한 benchmark compile·unit·receipt lane을 추가해 일반 build의 `benchmark:build` 제외를 보완한다.
- 실행 대상은 `benchmarkClasses`, `testClasses`, `test`, `validateOcrBenchmarkReceipt`, `validateOcrProtocolReceipt`, `validateVipsTransformReceipt`다.
- JMH 성능 측정, libvips/JNI native smoke, host Tesseract OCR 측정은 이 lane에서 호출하지 않는다. 해당 측정은 명시적인 별도 실행과 기존 순차 제약에 남긴다.
- benchmark 결과는 Kover aggregate에 섞지 않고 전용 JUnit·receipt artifact와 step summary로 보존한다.

## 구현 단계

1. `ci.yml` changes filter에 benchmark output을 추가하고 benchmark 전용 test job을 연결한다.
2. `ci-status`가 benchmark 변경 또는 build-logic 변경 시 benchmark job의 skip/failure를 required 결과로 판정하도록 한다.
3. `nightly-tests.yml`에 동일한 검증 lane과 artifact를 추가하고 nightly status needs에 포함한다.
4. `actionlint`, YAML parse, benchmark compile·unit·receipt 검증을 순차 실행한다.
5. exact implementation head에서 7-Tier 독립 리뷰를 수행하고, receipt·lesson을 기록한다.

## 검증 명령

```bash
actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml
./gradlew :bluetape4k-images-benchmark:benchmarkClasses :bluetape4k-images-benchmark:testClasses \
  --no-configuration-cache --no-daemon --max-workers=1
./gradlew :bluetape4k-images-benchmark:test \
  --rerun-tasks --no-build-cache --no-configuration-cache --no-daemon --max-workers=1
./gradlew :bluetape4k-images-benchmark:validateOcrBenchmarkReceipt \
  :bluetape4k-images-benchmark:validateOcrProtocolReceipt \
  :bluetape4k-images-benchmark:validateVipsTransformReceipt \
  --rerun-tasks --no-build-cache --no-configuration-cache --no-daemon --max-workers=1
git diff --check
```

## 종료 조건

- benchmark-only path가 `images-benchmark` output과 전용 test job으로 연결된다.
- benchmark 변경 시 compile·124개 단위 테스트·세 receipt validator가 실행되고, skip/failure가 `ci-status`에서 required failure로 반영된다.
- JUnit과 receipt validation log artifact가 남으며 성능/native/OCR 측정은 호출되지 않는다.
- hosted PR CI가 stacked branch filter로 생성되지 않으면 PASS로 주장하지 않고 `N/A/PENDING`으로 기록한다.
