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
- **Optional object metadata**: read size, ETag, content type, and last-modified without downloading the body.
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
        # Runtime parent creation is fail-closed; provision fixed prefixes at startup.
        bootstrap-prefixes: [originals, thumbnails]
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
Local parent directories are not created during an upload. Configure
`local.bootstrap-prefixes` with fixed relative prefixes, or provision them
before constructing `LocalImageStorage`; missing parents fail with
`ValidationException` without creating files or directories.

Runtime writes require a filesystem provider that exposes `SecureDirectoryStream`
and atomically replaces an existing target through the descriptor-relative
`move` operation. The upload stages bytes, forces the channel when supported,
and replaces the target only after the staged write completes; a failed or
cancelled write removes the partial stage and never falls back to an unsafe
path-based move. Providers without this capability (for example, JDK ZipFS)
are unsupported and fail closed with `ImageStorageException` rather than
silently weakening the storage contract. The `LocalFileSystemContractTest`
matrix records the provider probe, root/nested replacement, symlink, permission,
missing-parent, and cancellation result; POSIX-only checks are reported as N/A
when the provider or process cannot enforce them.

#### S3

Requires `bluetape4k-aws-spring-boot` dependency and an `S3Operations` bean
provided by that module. When `backend=s3` is configured and no `S3Operations`
bean exists, startup fails instead of silently falling back to local filesystem
storage. Provide a custom `ImageStorage` bean if an application intentionally
replaces the S3 storage implementation.

`Path` uploads first create a bounded streaming snapshot and then use the
optional `S3TransferOperations` file-transfer capability when it is available;
without that capability they fail closed instead of loading the entire source
into a `ByteArray`. `Path` downloads stream through an S3 resource and
atomically replace the destination file.

#### Shared storage contract matrix

Local and S3 run the same provider-neutral contract tests for the behavior below.
Provider-specific tests retain filesystem capability and AWS SDK interaction details.

| Contract | Local fixture | S3 fixture | CI coverage |
| --- | --- | --- | --- |
| Basic CRUD and overwrite | Real temporary filesystem | Stateful in-memory operations | Module test |
| `Path` atomicity and destination preservation | Descriptor-relative staging | Transfer snapshot and resource stream | Module test |
| Cold listing and cancellation cleanup | Secure directory traversal | Observable Flow collector | Module test |
| Filesystem capability matrix | Linux/macOS provider | N/A | Linux/macOS matrix |

Listing uses a rendezvous boundary between the IO producer and collector. A cancelled
collector may leave at most one in-flight item, but it does not materialize the remaining
listing. `CancellationException` is propagated without conversion to
`ImageStorageException`; dispatcher boundaries preserve its type and message rather than
object identity.

### Object metadata capability

`ImageStorage` keeps its original method set. Providers that support metadata also
implement the optional `ImageObjectMetadataReader` capability:

```kotlin
val metadata = (storage as? ImageObjectMetadataReader)?.readMetadata(key)
```

`ImageObjectMetadata` is provider-neutral and contains `sizeBytes`, nullable
`contentType`/`lastModified`, and an opaque nullable `etag`. ETag quotes and other
backend tokens are preserved; callers must not treat an ETag as an MD5 or content
hash. `lastModified` precision is backend/filesystem-defined and is not guaranteed
to retain sub-second precision. Local storage reads filesystem attributes only, so
its ETag and content type are `null`. The Micrometer decorator preserves this capability for supported
providers and does not advertise it for custom storage implementations that do not
implement the interface.

S3 metadata uses one `S3Operations.headObject` snapshot without opening the body.
Both byte-array and `Path` downloads perform the same HEAD size pre-check and then
compare the streamed byte count with that snapshot before exposing the result. A
HEAD failure or a size race fails closed; there is no `listPage` or resource-size
fallback. The aligned `bluetape4k-aws-spring-boot` artifact must contain the
`headObject` contract from upstream PR [#516](https://github.com/bluetape4k/bluetape4k-aws/pull/516)
(`24c8039006220de654c732f722f3c7beb9b5b74f`); consumers should select it through
the `bluetape4k-dependencies` catalog rather than coordinating an individual
artifact version.

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

### 0.5.0 Serialization Boundary

`LocalImageStorage`, `S3ImageStorage`, URL signers, and `CdnProperties` are runtime
configuration/collaborator objects, not Java-serializable state. Do not put them in an
`ObjectOutputStream` graph; reconstruct them from Spring configuration on startup. In
addition, CloudFront private-key PEM and path properties are excluded from Jackson wire
views and redacted from `toString()`/Actuator diagnostics. Existing runtime serialization
must be migrated to explicit application snapshots; a stale attempt fails with
`NotSerializableException`.

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
bean with `MetricImageStorage` (or its capability-preserving metadata variant) which records:

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
