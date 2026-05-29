# Spring Boot Image API Quickstart

English | [한국어](./README.ko.md)

Compact Spring Boot 4 example for local image upload, thumbnail generation, and
filesystem-backed storage with `bluetape4k-images-spring-boot`.

## What It Shows

- `LocalImageStorage` auto-configuration through `bluetape4k-images-spring-boot`
- Multipart upload validation through the shared `UploadOptions` content-type allowlist
- Original image storage under `originals/`
- PNG thumbnail generation with `bluetape4k-images`
- Local read URLs for stored original and thumbnail objects
- Controller tests without S3, CDN, Docker, or external infrastructure

This is the small repo-owned quickstart. For an advanced workflow with public
URLs, S3/CDN concerns, and a larger service shape, use
`bluetape4k-workshop/image-processing/advanced-workflow`.

## Run

```bash
./gradlew :spring-boot-image-api:bootRun
```

Upload an image:

```bash
curl -F "file=@images/src/test/resources/images/cafe.jpg;type=image/jpeg" \
  "http://localhost:8080/api/images?maxSide=320"
```

Example response:

```json
{
  "original": {
    "key": "originals/AbCdEf123456.jpg",
    "url": "/api/images/originals/AbCdEf123456.jpg"
  },
  "thumbnail": {
    "key": "thumbnails/AbCdEf123456.png",
    "url": "/api/images/thumbnails/AbCdEf123456.png"
  },
  "originalBytes": 73543,
  "thumbnailBytes": 18412
}
```

Download the thumbnail:

```bash
curl -o thumbnail.png "http://localhost:8080/api/images/thumbnails/AbCdEf123456.png"
```

## Configuration

The default example stores files under:

```yaml
bluetape4k:
  images:
    storage:
      backend: local
      max-size-bytes: 10485760
      local:
        root-dir: build/tmp/spring-boot-image-api/storage
```

The quickstart intentionally keeps S3 and CDN setup out of the default path.
Switching to S3 belongs in the advanced workshop because it requires bucket,
credentials, public URL, and operational policy decisions.

## Test

```bash
./gradlew :spring-boot-image-api:test
```

The tests upload an in-memory JPEG, verify original and thumbnail storage keys,
download both local URLs, check PNG thumbnail bytes, and reject unsupported
content types.
