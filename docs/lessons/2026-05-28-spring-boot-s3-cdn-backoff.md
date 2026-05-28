# Lessons: Spring Boot S3/CDN Backoff (Issue #109)

Date: 2026-05-28
Branch: work/0.2.0-remaining-issues

## Summary

Issue #109 tightened optional S3/CDN auto-configuration behavior in
`images-spring-boot`.

## L1: Optional class presence is weaker than optional bean readiness

### Problem

`@ConditionalOnClass(name = ["io.bluetape4k.aws.spring.s3.S3Operations"])`
proved that the optional integration type was loadable, but it did not prove
that an `S3Operations` bean existed. With `backend=s3` or CDN `s3_presign`,
auto-configuration could still try to create beans whose method parameters
required a missing `S3Operations` bean.

### Lesson

For optional Spring integrations, gate the nested configuration by class name
and gate the concrete bean by optional bean type:

```kotlin
@ConditionalOnClass(name = ["io.bluetape4k.aws.spring.s3.S3Operations"])
class S3Configuration {
    @Bean
    @ConditionalOnBean(type = ["io.bluetape4k.aws.spring.s3.S3Operations"])
    fun s3Bean(...)
}
```

## L2: Fallback beans need backend-specific conditions

### Problem

An unconditional local fallback bean with only
`@ConditionalOnMissingBean(ImageStorage::class)` can win before an S3 bean when
both are candidates. That hides the requested S3 backend even when the optional
S3 bean is present.

### Lesson

Split default/local storage and S3 fallback storage into separate conditional
configuration classes:

- `backend=local` or missing -> local storage
- `backend=s3` and no `S3Operations` bean -> local fallback
- `backend=s3` and `S3Operations` bean -> S3 storage

## Evidence

- `./gradlew :bluetape4k-images-spring-boot:test --tests "io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfigurationTest" --tests "io.bluetape4k.images.spring.autoconfigure.ImagesCdnAutoConfigurationTest"`: 15 passing
- `./gradlew :bluetape4k-images-spring-boot:test`: 118 passing
