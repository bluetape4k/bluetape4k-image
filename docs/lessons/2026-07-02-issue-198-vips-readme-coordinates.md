# Issue #198 VIPS README 좌표

## 배경

Java 21과 Java 25 VIPS module README pair는 `1.7.0` dependency coordinate를
문서화하고 있었지만, image repository는 이미 다른 release line으로 이동해 있었다.

## 결정

module-level dependency example에는 `gradle.properties`와 BOM README에서 조용히
드리프트할 수 있는 release number를 hard-code하지 않고 `<version>` placeholder를
사용한다.

## 결과

VIPS Java 21과 Java 25 README pair는 이제 repository의 module README convention과
일치하고, Java 21 BOM snippet도 BOM README 사용 방식과 맞는다.

## 검증

- `git diff --check`
- 변경된 VIPS README pair, root README, BOM README에서 stale `1.7.0` 검색

## 향후 방지책

module README dependency snippet에서는 문서가 release-specific copy snippet을 명시적으로
보여주는 경우가 아니라면 `<version>` 또는 BOM-managed coordinate를 우선한다.
