# images-spring-boot

`images` 모듈을 위한 Spring Boot 4 자동 구성 — S3/CDN 스토리지, 리액티브 헬스 인디케이터, Micrometer 메트릭.

## 아키텍처

![아키텍처 1](../docs/images/readme-diagrams/images-spring-boot-ko-diagram-01.svg)

## 주요 기능

- 이미지 스토리지, CDN 서명, 헬스 체크, 메트릭 **자동 구성**
- **두 가지 스토리지 백엔드**: 로컬 파일시스템 및 `bluetape4k-aws-spring-boot` 기반 AWS S3
- **CDN URL 서명**: S3 사전 서명 URL 또는 CloudFront 서명 URL
- **리액티브 헬스 인디케이터**: 스토리지 접근성을 확인하는 `ReactiveHealthIndicator`
- **Micrometer 메트릭**: `BeanPostProcessor`를 통한 업로드/다운로드 타이머 및 오류 카운터
- **Actuator 보호**: `/actuator/configprops`에서 `privateKeyPem` 값을 마스킹하는 `SanitizingFunction`

## 사용법

### 의존성

```kotlin
implementation("io.github.bluetape4k.image:bluetape4k-images-spring-boot")
```

### 설정

```yaml
bluetape4k:
  images:
    processing:
      enabled: true
      default-format: jpeg
      default-quality: 85

    storage:
      enabled: true
      backend: local            # 또는: s3
      max-size-bytes: 52428800  # 50 MB
      health-probe-key: .health-probe
      local:
        root-dir: /tmp/images
      # S3 백엔드 (bluetape4k-aws-spring-boot 필요):
      bucket: my-image-bucket
      key-prefix: images/

    cdn:
      enabled: false            # 기본값: 비활성
      provider: s3_presign      # 또는: cloudfront
      cloudfront:
        distribution-domain: d1234abcd.cloudfront.net
        key-pair-id: APKABC123
        private-key-path: /etc/secrets/cloudfront.pem
        default-expiry: PT10M
        max-expiry: PT1H

    health:
      enabled: true

    metrics:
      enabled: true
```

### 스토리지 백엔드

#### 로컬 (기본값)

추가 의존성 없음. `local.root-dir` 아래에 파일 저장.
`ImageObjectKey`가 `..` 경로 세그먼트를 거부하여 경로 탐색 공격 방지.

#### S3

`bluetape4k-aws-spring-boot` 의존성과 `S3Operations` 빈 필요.

```yaml
bluetape4k.images.storage:
  backend: s3
  bucket: my-bucket
  key-prefix: uploads/
```

### CDN URL 서명

#### S3 사전 서명 URL

```yaml
bluetape4k.images.cdn:
  enabled: true
  provider: s3_presign
```

#### CloudFront 서명 URL

```yaml
bluetape4k.images.cdn:
  enabled: true
  provider: cloudfront
  cloudfront:
    distribution-domain: d1234abcd.cloudfront.net
    key-pair-id: APKABC123
    private-key-path: /etc/secrets/cloudfront.pem
```

`privateKeyPem`은 인라인 설정 가능하지만 파일 경로 사용을 권장합니다. 힙 메모리의 PEM 값은 초기화할 수 없어 힙 덤프에 노출될 수 있습니다.

### 헬스 체크

`ImageStorageHealthIndicator`가 `storage.exists()` 호출로 접근성을 확인합니다.
Spring Boot 4 `spring-boot-health` 모듈의 `ReactiveHealthIndicator`로 통합됩니다.

```
GET /actuator/health
{
  "status": "UP",
  "components": {
    "imageStorage": { "status": "UP" }
  }
}
```

### 메트릭

`MeterRegistry` 빈이 있으면 `BeanPostProcessor`가 모든 `ImageStorage` 빈을 `MetricImageStorage`로 래핑합니다.

| 메트릭 | 타입 | 설명 |
|--------|------|------|
| `images.storage.upload.duration` | Timer | 업로드 지연 시간 |
| `images.storage.upload.errors` | Counter | 업로드 오류 횟수 |
| `images.storage.download.duration` | Timer | 다운로드 지연 시간 |
| `images.storage.download.errors` | Counter | 다운로드 오류 횟수 |

## 자동 구성 순서

| 클래스 | 이후에 실행 |
|--------|-------------|
| `ImagesProcessingAutoConfiguration` | — |
| `ImagesStorageAutoConfiguration` | `S3AutoConfiguration`, `ImagesProcessingAutoConfiguration` |
| `ImagesCdnAutoConfiguration` | `ImagesStorageAutoConfiguration` |
| `ImagesHealthAutoConfiguration` | `ImagesStorageAutoConfiguration` |
| `ImagesMetricsAutoConfiguration` | `ImagesStorageAutoConfiguration` |

## 예외 계층

```
ImageStorageException (sealed)
├── NotFoundException       — 객체 없음
├── AccessDeniedException   — 권한 없음
├── ConflictException       — 이미 존재
├── TransientException      — 재시도 가능한 오류
└── ValidationException     — 입력 검증 실패
```

## 키 모델

`ImageObjectKey.of(prefix, name)` — 두 세그먼트 모두 `[A-Za-z0-9._/-]+` 패턴을 만족해야 하며 `..`를 포함할 수 없습니다.

```kotlin
val key = ImageObjectKey.of("thumbnails", "photo-001.webp")
// key.fullKey == "thumbnails/photo-001.webp"
```
