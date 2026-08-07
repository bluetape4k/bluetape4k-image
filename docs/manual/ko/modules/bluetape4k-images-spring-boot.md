---
manualId: "bluetape4k-images-spring-boot"
id: "bluetape4k-images-spring-boot"
title: "Spring Boot 이미지 플랫폼"
locale: "ko"
kind: "library"
gradlePath: ":bluetape4k-images-spring-boot"
sourceDir: "images-spring-boot"
releaseRef: "0.4.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-spring-boot
---

# Spring Boot 이미지 플랫폼

> 라이브러리 모듈

## 제공하는 기능 {#problem}

Spring Boot 4에서 이미지 객체 저장소, 서명 URL, reactive health와 Micrometer 메트릭을 구성합니다. 코루틴 기반 `ImageStorage` 계약을 제공하고 설정에 따라 로컬 또는 S3 구현체를 선택합니다.

이름과 달리 `0.4.0`의 처리 자동 구성은 프로퍼티만 바인딩하며 실제 이미지 처리기나 필터 파이프라인 빈은 등록하지 않습니다.

## 사용하기 좋은 경우 {#when-to-use}

Spring Boot 서비스에 로컬/S3 이미지 저장과 운영 기능이 필요할 때 사용합니다. 이미지 변환 자체는 `bluetape4k-images`로 수행하고, 저장소·CDN·상태 점검·메트릭이 필요할 때 이 모듈을 더하세요.

## 의존성 좌표 {#coordinates}

Maven 좌표: `io.github.bluetape4k.image:bluetape4k-images-spring-boot`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-spring-boot")
}
```

## 핵심 개념 {#concepts}

`ImageObjectKey`가 prefix/name을 검증하고 `..`를 막습니다. `ImageStorage`는 suspend 업로드·다운로드·삭제·존재 확인과 cold `Flow` 목록을 제공합니다. `ImageStorageException`은 없음, 권한, 충돌, 일시 오류, 검증 오류로 나뉩니다.

## 빠르게 시작하기 {#quick-start}

```yaml
bluetape4k:
  images:
    storage:
      backend: local
      max-size-bytes: 52428800
      local:
        root-dir: /var/lib/app/images
```

```kotlin
val key = ImageObjectKey.of("avatars", "user-42.jpg")
storage.upload(key, bytes, UploadOptions(contentType = "image/jpeg"))
val saved = storage.download(key)
```

## 작업별 API {#api-by-task}

- `LocalImageStorage`를 쓰거나 `backend=s3`와 `S3Operations` Bean으로 S3를 선택합니다.
- 바이트나 경로를 업로드·다운로드하고 키 목록을 `Flow`로 수집합니다.
- S3 presigned read/write URL 또는 CloudFront read URL을 만듭니다.
- 관련 클래스가 있으면 reactive health와 Micrometer decorator를 활성화합니다.
- 애플리케이션 Bean으로 storage와 signer를 교체할 수 있습니다.

## 권장 패턴 {#patterns}

로컬 저장소는 임시 디렉터리 기본값 대신 영속 경로를 명시하세요. `ValidationException`은 입력 문제, `NotFoundException`은 없음으로 처리하고 일반적인 재시도는 `TransientException`에만 적용합니다. 사용자 구현체도 코루틴 취소를 그대로 전파해야 합니다.

인터페이스에는 원자적 업로드 계약이 있지만 `0.4.0` 로컬 구현은 임시 파일과 atomic rename이 아니라 `Files.write`/`Files.copy(REPLACE_EXISTING)`를 사용합니다. 장애 시 원자성을 보장한다고 문서화하면 안 됩니다.

## 연동 {#integrations}

S3는 `bluetape4k-aws-spring-boot`의 `S3Operations`를 사용합니다. CloudFront와 S3 presign은 선택적 클래스패스 연동입니다. health는 Spring Boot health와 coroutines-reactor, metrics는 Micrometer가 필요합니다.

## 설정 {#configuration}

저장소는 기본 활성화, `LOCAL`, 업로드·다운로드 50MiB 제한, JVM 임시 디렉터리 아래 로컬 경로를 사용합니다. `backend=s3`이고 `S3Operations`가 있으면 버킷이 필수입니다. S3를 선택했지만 해당 빈이 없으면 `0.4.0`은 로컬 저장소로 대체합니다.

CDN은 기본 비활성화이며 `s3_presign`, `cloudfront` 중에서 고릅니다. CloudFront 개인 키는 한 경로만 설정하고 가능하면 `privateKeyPath`를 사용하세요.

## 실패 유형과 해결 방법 {#failures}

S3 401/403은 권한 오류, 404는 없음, 409는 충돌, 나머지는 일시 오류로 바뀝니다. `exists`는 권한 오류를 `false`로 숨기지 않습니다. 잘못된 키, 크기, MIME은 검증 오류이며 SVG는 저장 XSS 위험 때문에 허용 목록에서 제외됩니다.

## 운영 {#operations}

메트릭은 `images.storage.*` 아래 업로드·다운로드 시간과 오류를 기록합니다. 상태 점검은 `_health/<probeKey>`에 `exists`를 호출해 접근 가능성을 확인하며 실제 객체 존재 여부는 따지지 않습니다. CloudFront 개인 키 속성은 로그와 Actuator에서 마스킹합니다.

## 테스트 {#testing}

자동 구성은 `ApplicationContextRunner`, 저장소는 로컬 계약 테스트로 검증합니다. 메트릭, 상태 점검, 키, 민감 정보 마스킹 테스트도 포함됩니다. 배포 대상 S3 호환 서비스에 대한 통합 테스트는 애플리케이션에서 별도로 추가해야 합니다.

## 학습 경로와 예제 {#workshops}

`examples/spring-boot-image-api`로 로컬 저장을 익힌 뒤 명시적인 S3 환경, 서명 URL, 운영 상태 점검·메트릭 순서로 넓혀 가세요.

## 제약 사항 {#limitations}

`S3Operations`에 스트리밍 업로드가 없어 경로 업로드도 바이트 배열로 읽습니다. `UploadOptions.cacheControl`과 metadata도 S3 구현에서 전달하지 않습니다. HEAD API가 없어 크기 확인에는 목록 조회를 사용합니다. 큰 스트림이나 필수 헤더가 있으면 AWS SDK를 직접 사용하세요.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.4.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Images Spring Boot 아키텍처

[![Images Spring Boot 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-spring-boot-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-spring-boot-architecture-01.svg)

_배포본 README: [`images-spring-boot/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [저장소 계약](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/ImageStorage.kt)
- [저장소 자동 구성](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesStorageAutoConfiguration.kt)
- [S3 구현과 제약](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorage.kt)
