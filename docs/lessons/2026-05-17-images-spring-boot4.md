# Lessons: images-spring-boot4 (Issue #5)

Date: 2026-05-17
Branch: feat/images-spring-boot4

## 요약

Spring Boot 4 + AWS S3/CDN 자동 구성 통합 모듈(`images-spring-boot4`)을 신규 구현하면서 얻은 핵심 교훈.

---

## 1. Spring Boot 4 API 변경사항

### ReactiveHealthIndicator 이전

**문제**: Spring Boot 3에서 `ReactiveHealthIndicator`는 `spring-boot-actuator` 안에 있었다.
Spring Boot 4에서는 별도 모듈 `spring-boot-health`로 이전됐고 패키지도 변경됐다.

- **이전**: `org.springframework.boot.actuate.health.ReactiveHealthIndicator`
- **이후**: `org.springframework.boot.health.contributor.ReactiveHealthIndicator` (in `spring-boot-health`)

**해결**:
- `build.gradle.kts`에 `compileOnly(libs.spring.boot.health)` 추가
- `@ConditionalOnClass(name = ["org.springframework.boot.health.contributor.ReactiveHealthIndicator"])` 사용

### `@PostConstruct` (jakarta.annotation) 비전이 의존성

Spring Boot 4 `spring-boot-autoconfigure`가 `jakarta.annotation-api`를 더 이상 전이 의존성으로 제공하지 않는다.
`@PostConstruct` 사용 시 명시적으로 추가해야 한다.

```kotlin
compileOnly(libs.jakarta.annotation.api)
```

---

## 2. compileOnly 의존성 격리 패턴

### 문제

`S3Operations`, `CloudFrontUtilities`, `MeterRegistry`, `ReactiveHealthIndicator` 모두 `compileOnly` 의존성이다.
이를 직접 참조하는 `@Bean` 메서드가 외부 `@AutoConfiguration` 클래스에 있으면 class-load 시점에 `NoClassDefFoundError`가 발생한다.

### 해결: 중첩 `@Configuration` + `@ConditionalOnClass(name=[String])` 패턴

```kotlin
// ❌ 위험: outer class에 compileOnly 타입 직접 참조
@AutoConfiguration
class ImagesStorageAutoConfiguration {
    @Bean fun s3ImageStorage(ops: S3Operations): ImageStorage = ...  // NoClassDefFoundError 위험
}

// ✅ 안전: 중첩 클래스로 격리
@AutoConfiguration
class ImagesStorageAutoConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["io.bluetape4k.aws.spring.s3.S3Operations"])  // string FQCN!
    class S3StorageConfiguration {
        @Bean fun s3ImageStorage(ops: S3Operations): ImageStorage = ...
    }
}
```

**규칙**:
- `afterName` (string) 사용, `after` (KClass) 금지 — optional 모듈의 KClass 참조는 `NoClassDefFoundError`
- `@ConditionalOnClass(name=[...])` 사용, `@ConditionalOnClass(value=[...])` 금지

---

## 3. kotlin-spring 플러그인 설정 오류

### 문제

서브모듈 `build.gradle.kts`에서 `alias(libs.plugins.kotlin.spring)` 사용 시:
> "Plugin 'org.jetbrains.kotlin.plugin.spring' is already on the classpath"

### 해결

루트 `build.gradle.kts`에 `apply false`로 선언해야 서브모듈이 활성화할 수 있다:

```kotlin
// 루트 build.gradle.kts
alias(libs.plugins.kotlin.spring) apply false
alias(libs.plugins.spring.boot) apply false
```

---

## 4. S3Operations 실제 API 확인

S3Operations (bluetape4k-aws-spring-boot)의 메서드명이 pseudocode와 달랐다:

- `putObject()` → `upload()`
- `getObject()` → `download()`
- `headObject()` 없음 → `exists()` 구현에 `listPage` 사용
- `presignGet/presignPut` → `URL` 반환, `URI`로 변환 필요

**교훈**: 실제 소스를 먼저 읽고 구현해야 한다. SDK pseudocode를 그대로 사용하면 컴파일 오류.

---

## 5. Micrometer `recordSuspend` 없음

`micrometer-core-kotlin` 확장 라이브러리를 추가하지 않았으므로 `Timer.recordSuspend {}` 사용 불가.

대신:
```kotlin
val sample = Timer.start(registry)
try {
    val result = delegate.upload(key, bytes, options)
    sample.stop(registry.timer(UPLOAD_TIMER))
    result
} catch (e: CancellationException) {
    sample.stop(registry.timer(UPLOAD_TIMER))
    throw e
} catch (e: Throwable) {
    sample.stop(registry.timer(UPLOAD_TIMER))
    registry.counter(UPLOAD_ERRORS).increment()
    throw e
}
```

---

## 6. SanitizingFunction — Actuator 보안

CloudFront `privateKeyPem`이 `/actuator/configprops` 엔드포인트에 노출될 수 있다.
`SanitizingFunction` 빈을 등록해 `private-key-pem` 키를 포함한 모든 속성을 `"******"`으로 마스킹해야 한다.

`CdnProperties.CloudFront.toString()`도 `privateKeyPem=[REDACTED]`로 오버라이드.

---

## 7. 3-R Plan Review에서 발견된 주요 이슈 (3 rounds)

| Round | 발견 | 해결 |
|-------|------|------|
| 1 | 13 HIGH: FQCN 오류, ReactiveHealthIndicator 위치, LocalImageStorage 생성자, S3Exception 격리, BPP 메트릭, URL→URI, SanitizingFunction, SDK timeout, 테스트 누락 | 모두 스펙/플랜 반영 |
| 2 | 2 HIGH: S3PreSignedUrlSigner 생성자 불일치, nullable bucket 처리 | 반영 |
| 3 | 수렴 (CRITICAL=0, HIGH=0) | — |

---

## 체크리스트 (미래 Spring Boot 4 모듈 참고)

- [ ] `spring-boot-health` 별도 의존성 확인 (`ReactiveHealthIndicator` 위치 확인)
- [ ] `jakarta.annotation-api` compileOnly 명시
- [ ] `kotlin-spring` 플러그인 루트에 `apply false` 선언
- [ ] 모든 optional 의존성 → 중첩 `@Configuration` + `@ConditionalOnClass(name=[String])`
- [ ] `afterName` 사용 (string), `after` (KClass) 금지
- [ ] `@ConditionalOnProperty` 모든 auto-config 클래스에 적용 (entrypoint만 아님)
- [ ] SanitizingFunction으로 민감 속성 마스킹
