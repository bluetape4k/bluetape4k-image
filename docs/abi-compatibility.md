# 공개 API 호환성 기준

이 저장소의 published JVM 모듈은 `api/*.api`에 커밋된 Kotlin ABI dump를
호환성 기준으로 사용합니다. `buildSrc/src/main/kotlin/PublicationInventory.kt`가
published JVM 모듈, BOM, benchmark, example의 분류를 단일 source of truth로
제공하며, `bom/`의 dependency constraint와 root ABI gate가 같은 분류를
사용합니다.

## 검증 명령

```bash
# 현재 코드와 커밋된 baseline 비교
./gradlew checkProductionAbi

# ordinary check와 publish task에도 포함됨
./gradlew check
./gradlew publishAggregationToCentralSnapshots
./gradlew publishAggregationToCentralPortal
```

`checkProductionAbi`는 모든 published JVM 프로젝트의 `checkKotlinAbi`를 먼저
실행하고 다음 집합을 fail-closed로 비교합니다.

- published 프로젝트 이름
- `api/<module>.api` baseline 파일
- `build/kotlin/abi/<module>.api` 현재 dump

누락, 고아 파일, 빈 baseline, 빈 inventory가 하나라도 있으면 성공으로
처리하지 않습니다. 검증 결과는 `build/abi/reports/production-abi.txt`에
기록됩니다. benchmark와 examples는 실행/배포 전용 모듈이므로 baseline에서
제외하고, `bluetape4k-image-bom`은 JVM ABI 대상이 아닌 platform artifact이므로
제외합니다. BOM constraint는 같은 `isPublishedJvmModule()` 분류를 사용합니다.

## Baseline 갱신

API 변경은 먼저 해당 이슈와 migration note를 준비하고 review 가능한 dump
diff를 확인해야 합니다.

```bash
./gradlew updateProductionAbiBaseline
git diff -- api/
./gradlew checkProductionAbi
```

의도하지 않은 public signature 변경을 baseline 갱신으로 숨기지 않습니다.
호환성을 깨는 변경은 별도 이슈, consumer migration 근거, release note를
같은 PR train에 남깁니다.

## Incubating 정책

`@IncubatingImageApi`와 `@VipsIncubatingApi`가 붙은 declaration은 아직 안정
계약이 아니므로 production ABI dump에서 제외합니다. 이 정책은 root
`configureProductionAbiValidation()`의 annotation filter와 이 문서에 함께
고정되어 있습니다. incubating API를 stable로 승격할 때는 annotation 제거와
baseline 추가를 별도 review diff로 남겨야 하며, 반대로 annotation만 제거해
호환성 검사를 우회해서는 안 됩니다.
