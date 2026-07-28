## 배경

`Examples`, `CI`, `Nightly`가 GitHub-hosted Ubuntu runner에서 test 시작 전에
실패했다. 실패한 job은 Central snapshot에서 `1.11.0-SNAPSHOT` Maven metadata를 읽지
못하고 HTTP 403을 반환했지만, sibling job과 local check는 같은 artifact를 여전히
resolve할 수 있었다.

## 결정

public anonymous snapshot resolution을 기본값으로 유지하되 scheduled, manual,
same-repository workflow run에서는 `CENTRAL_USERNAME`과 `CENTRAL_PASSWORD`를 Gradle에
전달할 수 있게 한다. Gradle repository config는 두 값이 모두 있을 때만 Basic auth를
켠다. changing snapshot module은 하루 동안 cache하고, routine CI, Examples, Nightly
job에는 `--refresh-dependencies`를 전달하지 않는다. 매 시도마다 refresh를 강제하면
cache를 우회하고 Central snapshot metadata endpoint를 반복 호출한다. Matrix 또는
parallel build는 concurrent Central snapshot metadata request를 줄여야 한다. Central
connect timeout이 Gradle 시도마다 몇 분을 소비한다면 workflow job timeout은 retry loop가
끝날 만큼 충분히 길어야 한다.

## 결과

secret이 없는 local build와 PR은 anonymous Central snapshot access를 계속 사용한다.
Central credential이 있는 repository workflow는 upstream bluetape4k snapshot resolve
중 runner-specific 403 실패를 피할 수 있다. changing module metadata를 하루 동안
재사용하면 각 retry가 fresh Central snapshot metadata request를 강제하지 않는다.
Examples matrix job을 직렬화하고 build worker parallelism을 줄이면 Central snapshot
metadata contention 증폭을 피할 수 있다. Gradle job timeout을 늘리면 회복 가능한
Central connect timeout 때문에 이후 시도가 실행되기 전에 workflow가 취소되는 일을
막을 수 있다.

## 검증

- `actionlint .github/workflows/ci.yml .github/workflows/Examples.yml .github/workflows/nightly-tests.yml`
- `./gradlew help --no-daemon`
- `CENTRAL_USERNAME=dummy CENTRAL_PASSWORD=dummy ./gradlew help --no-daemon`
- `./gradlew :basic-processing:test --no-configuration-cache --no-daemon`

## 향후 지침

workflow가 unreleased upstream bluetape4k snapshot을 소비하고 GitHub runner에서 Central
snapshot metadata 403으로 실패하면, 먼저 workflow가 Central credential을 Gradle에
전달하는지 확인한다. credential이 있는데도 job이 실패하면 `--refresh-dependencies`가
하루짜리 changing-module cache를 쓰지 못하게 하고 매 retry마다 Central을 다시 조회하게
만드는지 확인한다. 일부 parallel job만 실패한다면 retry를 늘리기 전에 concurrent
snapshot metadata resolution을 줄인다. log에 `Connect timed out`이 보이면 job timeout이
retry loop의 최악 시간보다 짧은지 확인한다.
