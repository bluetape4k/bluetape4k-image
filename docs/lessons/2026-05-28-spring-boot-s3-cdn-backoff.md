# 교훈: Spring Boot S3/CDN 대체 구성(Issue #109)

날짜: 2026-05-28
브랜치: work/0.2.0-remaining-issues

## 요약

Issue #109는 `images-spring-boot`의 선택적 S3/CDN 자동 구성 동작을
더 엄격하게 만들었다.

## L1: 선택 클래스의 존재만으로 선택 Bean의 준비 상태를 보장할 수 없다

### 문제

`@ConditionalOnClass(name = ["io.bluetape4k.aws.spring.s3.S3Operations"])`
는 선택 통합 타입을 로드할 수 있다는 점만 증명하며 `S3Operations` Bean의
존재까지 보장하지 않는다. `backend=s3` 또는 CDN `s3_presign` 구성에서는
자동 구성이 존재하지 않는 `S3Operations` Bean을 메서드 인자로 요구하는
Bean을 생성하려 할 수 있다.

### 교훈

선택적 Spring 통합에서는 중첩 구성을 클래스 이름으로 제한하고, 구체적인 Bean은
선택 Bean 타입으로 제한한다:

```kotlin
@ConditionalOnClass(name = ["io.bluetape4k.aws.spring.s3.S3Operations"])
class S3Configuration {
    @Bean
    @ConditionalOnBean(type = ["io.bluetape4k.aws.spring.s3.S3Operations"])
    fun s3Bean(...)
}
```

## L2: 대체 Bean에는 백엔드별 조건이 필요하다

### 문제

`@ConditionalOnMissingBean(ImageStorage::class)`만 적용한 무조건부 로컬 대체
Bean은 두 Bean이 모두 후보일 때 S3 Bean보다 먼저 선택될 수 있다. 선택 S3
Bean이 있어도 요청한 S3 백엔드가 가려진다.

### 교훈

기본/로컬 스토리지와 S3 대체 스토리지를 별도의 조건부 구성 클래스로 나눈다:

- `backend=local`이거나 값이 없음 -> 로컬 스토리지
- `backend=s3`이고 `S3Operations` Bean이 없음 -> 로컬 대체 구성
- `backend=s3`이고 `S3Operations` Bean이 있음 -> S3 스토리지

## 근거

- `./gradlew :bluetape4k-images-spring-boot:test --tests "io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfigurationTest" --tests "io.bluetape4k.images.spring.autoconfigure.ImagesCdnAutoConfigurationTest"`: 15개 통과
- `./gradlew :bluetape4k-images-spring-boot:test`: 118개 통과
