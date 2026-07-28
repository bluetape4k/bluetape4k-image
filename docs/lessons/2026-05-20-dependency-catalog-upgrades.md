# 의존성 카탈로그 갱신

## 배경

`bluetape4k-dependencies`는 AWS SDK Java Dependabot PR을 중앙 의존성 갱신
작업에 포함했다.

## 결정

중앙 AWS SDK Java 카탈로그 버전을 이 저장소에 반영한다.

## 결과

`gradle/libs.versions.toml`이 AWS SDK Java `2.44.9`를 사용한다.

## 검증

- `./gradlew build -x test --no-daemon`
