# images-spring-boot

Spring Boot 4 auto-configuration for the `images` module — S3/CDN storage,
reactive health indicators, and Micrometer metrics.

## Architecture

![Images Spring Boot Architecture diagram](../docs/images/readme-diagrams/images-spring-boot-architecture-01.png)

## Features

- **Auto-configuration** for image storage, CDN signing, health, and metrics.
- **Two storage backends**: local filesystem and AWS S3 via `bluetape4k-aws-spring-boot`.
- **CDN URL signing**: S3 pre-signed URLs or CloudFront signed URLs.
- **Reactive health indicator**: `ReactiveHealthIndicator` probing storage reachability.
- **Micrometer metrics**: upload/download duration timers and error counters via `BeanPostProcessor`.
- **Actuator protection**: `SanitizingFunction` redacting `privateKeyPem` from `/actuator/configprops`.

## Usage

### Dependency

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k.image:bluetape4k-image-bom:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-spring-boot")
}
```

If you do not import the BOM, declare the module version directly:

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-spring-boot:<version>")
}
```

### Configuration

```yaml
bluetape4k:
  images:
    processing:
      enabled: true
      default-format: jpeg
      default-quality: 85

    storage:
      enabled: true
      backend: local            # or: s3
      max-size-bytes: 52428800  # 50 MB
      health-probe-key: .health-probe
      local:
        root-dir: /tmp/images
      # S3 backend (requires bluetape4k-aws-spring-boot):
      bucket: my-image-bucket
      key-prefix: images/

    cdn:
      enabled: false            # disabled by default
      provider: s3_presign      # or: cloudfront
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

### Storage Backends

#### Local (default)

No additional dependencies. Files stored under `local.root-dir`.
Path traversal is prevented — `ImageObjectKey` rejects `..` segments.

#### S3

Requires `bluetape4k-aws-spring-boot` dependency and an `S3Operations` bean
provided by that module. When `backend=s3` is configured and no `S3Operations`
bean exists, startup fails instead of silently falling back to local filesystem
storage. Provide a custom `ImageStorage` bean if an application intentionally
replaces the S3 storage implementation.

```yaml
bluetape4k.images.storage:
  backend: s3
  bucket: my-bucket
  key-prefix: uploads/
```

### CDN URL Signing

#### S3 Pre-signed URLs

```yaml
bluetape4k.images.cdn:
  enabled: true
  provider: s3_presign
```

#### CloudFront Signed URLs

```yaml
bluetape4k.images.cdn:
  enabled: true
  provider: cloudfront
  cloudfront:
    distribution-domain: d1234abcd.cloudfront.net
    key-pair-id: APKABC123
    private-key-path: /etc/secrets/cloudfront.pem
```

`privateKeyPem` can be set inline but a file path is preferred — PEM values in heap memory
cannot be zeroed and will appear in heap dumps.

### Health

The `ImageStorageHealthIndicator` calls `storage.exists()` to probe reachability.
It integrates as a `ReactiveHealthIndicator` (Spring Boot 4 `spring-boot-health` module).

```
GET /actuator/health
{
  "status": "UP",
  "components": {
    "imageStorage": { "status": "UP" }
  }
}
```

### Metrics

When a `MeterRegistry` bean is present, the `BeanPostProcessor` wraps every `ImageStorage`
bean with `MetricImageStorage` which records:

| Metric | Type | Description |
|--------|------|-------------|
| `images.storage.upload.duration` | Timer | Upload latency |
| `images.storage.upload.errors` | Counter | Upload error count |
| `images.storage.download.duration` | Timer | Download latency |
| `images.storage.download.errors` | Counter | Download error count |

## Auto-Configuration Order

| Class | Ordered after |
|-------|---------------|
| `ImagesProcessingAutoConfiguration` | — |
| `ImagesStorageAutoConfiguration` | `S3AutoConfiguration`, `ImagesProcessingAutoConfiguration` |
| `ImagesCdnAutoConfiguration` | `ImagesStorageAutoConfiguration` |
| `ImagesHealthAutoConfiguration` | `ImagesStorageAutoConfiguration` |
| `ImagesMetricsAutoConfiguration` | `ImagesStorageAutoConfiguration` |

## Exception Hierarchy

```
ImageStorageException (sealed)
├── NotFoundException       — object not found
├── AccessDeniedException   — insufficient permissions
├── ConflictException       — object already exists
├── TransientException      — retryable error
└── ValidationException     — input validation failure
```

## Key Model

`ImageObjectKey.of(prefix, name)` — both segments must match `[A-Za-z0-9._/-]+` and must not contain `..`.

```kotlin
val key = ImageObjectKey.of("thumbnails", "photo-001.webp")
// key.fullKey == "thumbnails/photo-001.webp"
```
