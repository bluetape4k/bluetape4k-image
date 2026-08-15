# STORAGE-1 ImageStorage metadata capability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `test-driven-development` and the matching Kotlin pattern skill for every production behavior change. Execute tasks in order and keep each checkbox evidence-backed.

**Goal:** `ImageStorage` 소비자가 body download 없이 object metadata를 조회하도록 Local/S3 optional capability를 추가하고, upstream S3 HEAD snapshot과 source-compatible Micrometer decoration을 고정한다.

**Architecture:** `ImageStorage`에는 abstract method를 추가하지 않는다. `ImageObjectMetadataReader`와 immutable `ImageObjectMetadata`를 별도 capability로 두고 Local/S3 backend가 구현한다. S3는 upstream `S3Operations.headObject`의 단일 `S3ObjectMetadata` snapshot을 사용하며, HEAD 이후 실제 byte count가 snapshot과 다르면 fail closed한다.

**Tech Stack:** Kotlin/JVM 25, Spring Boot 4, kotlinx.coroutines, AWS SDK v2, Micrometer, JUnit 5, MockK, Gradle catalog/BOM.

---

## Task 0: 선행 upstream 계약과 release train 고정

**Files:**
- Modify: `../bluetape4k-aws/aws-spring-boot/src/main/kotlin/io/bluetape4k/aws/spring/s3/S3Operations.kt`
- Create: `../bluetape4k-aws/aws-spring-boot/src/main/kotlin/io/bluetape4k/aws/spring/s3/S3ObjectMetadata.kt`
- Modify: `../bluetape4k-aws/aws-spring-boot/src/main/kotlin/io/bluetape4k/aws/spring/s3/S3CoroutinesTemplate.kt`
- Modify: `../bluetape4k-aws/aws-spring-boot/src/main/kotlin/io/bluetape4k/aws/spring/s3/MicrometerS3Operations.kt`
- Modify: `../bluetape4k-aws/aws-spring-boot/src/test/kotlin/io/bluetape4k/aws/spring/s3/NoopS3Operations.kt`
- Test: `../bluetape4k-aws/aws-spring-boot/src/test/kotlin/io/bluetape4k/aws/spring/s3/S3CoroutinesTemplateTest.kt` (existing S3 test slice or new focused test)

- [x] **Step 1: Write the failing upstream contract tests.**

  Assert a fake `S3AsyncClient` receives one `HeadObjectRequest` and maps
  `contentLength`, quoted `eTag`, nullable `contentType`, and `lastModified` to
  `S3ObjectMetadata`. Assert `MicrometerS3Operations.headObject` delegates once.

- [x] **Step 2: Run the focused upstream test and observe RED.**

  Run `../bluetape4k-aws/gradlew :bluetape4k-aws-spring-boot:test --tests '*S3*Template*' --no-daemon`.
  Expected: compile/test failure because `headObject` and DTO do not exist.

- [x] **Step 3: Add the DTO and source-compatible unsupported default.**

  Add `S3ObjectMetadata(sizeBytes: Long, etag: String?, contentType: String?, lastModified: Instant?) : Serializable` with `sizeBytes >= 0` validation and `serialVersionUID`. Add the exact default method:

  ```kotlin
  suspend fun headObject(bucket: String, key: String): S3ObjectMetadata =
      throw UnsupportedOperationException("S3Operations.headObject is not supported by this implementation")
  ```

- [x] **Step 4: Implement the async template and decorator.**

  `S3CoroutinesTemplate.headObject` calls one `s3AsyncClient.headObject { bucket(bucket).key(key) }.await()` and maps all fields without stripping ETag quotes. `MicrometerS3Operations` adds `headObject` delegation and the `head_object` operation tag. `NoopS3Operations` keeps the default unsupported behavior.

- [x] **Step 5: Run upstream GREEN and ABI checks.**

  Run the focused test, `../bluetape4k-aws/gradlew :bluetape4k-aws-spring-boot:compileKotlin --no-daemon`, and `javap` on `S3Operations` to verify the default method signature. Record the upstream PR SHA and artifact version used by the image build.

- [x] **Step 6: Commit the upstream prerequisite.**

  Use a Lore-formatted Korean commit explaining that one async HEAD snapshot prevents metadata/download drift. Do not push or merge until the current task's PR authority and upstream CI gates are complete.

**Upstream evidence:** PR [#516](https://github.com/bluetape4k/bluetape4k-aws/pull/516),
exact head `24c8039006220de654c732f722f3c7beb9b5b74f`; image focused verification
uses local artifact `0.6.0-issue493-SNAPSHOT` generated from that head. Catalog
source/evidence refs are `45235aa22184b6a2280f530fb90c82a94e31c59d` (image) and
`9db9c2c65d8d4663f2658b0f0cf1a15b43d02a15` (dependencies). These are train
anchors, not an invented stable release version.

## Task 1: Public provider-neutral capability model

**Files:**
- Modify: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/ImageStorage.kt`
- Create: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/ImageObjectMetadata.kt`
- Create: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/ImageObjectMetadataReader.kt`
- Test: `images-spring-boot/src/test/kotlin/io/bluetape4k/images/spring/ImageObjectMetadataTest.kt`
- Test: `images-spring-boot/src/test/kotlin/io/bluetape4k/images/spring/storage/ImageObjectMetadataReaderTest.kt`

- [x] **Step 1: Add RED tests** for negative size rejection, nullable fields, exact ETag quote preservation, serialization, and capability discovery without changing `ImageStorage`.
- [x] **Step 2: Run `./gradlew :bluetape4k-images-spring-boot:test --tests '*ImageObjectMetadata*'` and confirm RED.**
- [x] **Step 3: Implement immutable model and suspend capability** with Korean KDoc, `Serializable`, `serialVersionUID`, and no AWS imports.
- [x] **Step 4: Run the focused tests and `javap`** to prove the public JVM signature and GREEN behavior.

## Task 2: Local metadata implementation

**Files:**
- Modify: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/LocalImageStorage.kt`
- Modify: `images-spring-boot/src/test/kotlin/io/bluetape4k/images/spring/storage/LocalImageStorageTest.kt`
- Test: `images-spring-boot/src/test/kotlin/io/bluetape4k/images/spring/storage/LocalImageStorageMetadataTest.kt`

- [x] **Step 1: Add RED tests** for existing object metadata, missing object, symlink/non-regular rejection, no body read, and nullable Local ETag/content type. Cancellation remains covered on the S3 path because Local attribute reads have no injectable blocking boundary.
- [x] **Step 2: Run the focused Local test and confirm RED.**
- [x] **Step 3: Implement `readMetadata` under `withContext(Dispatchers.IO)`** using `resolveKey` and `readObjectAttributes`; use `size`, `lastModifiedTime().toInstant()`, and null unsupported fields.
- [x] **Step 4: Run Local tests plus `git diff --check`.**

## Task 3: S3 metadata, HEAD pre-check, and runtime compatibility guard

**Files:**
- Modify: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorage.kt`
- Modify: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesStorageAutoConfiguration.kt`
- Modify: `images-spring-boot/src/test/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorageTest.kt`
- Modify: `images-spring-boot/src/test/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesStorageAutoConfigurationTest.kt`

- [x] **Step 1: Add RED S3 tests** with a fake/mock `headObject` response. Verify one HEAD call, zero `listPage`/body calls for metadata, cancellation/error mapping, and exact `headObject → downloadBytes` order.
- [x] **Step 2: Add RED race tests** for both smaller and larger actual download counts; assert `ValidationException`, no returned byte array, existing destination preserved, and staged file cleanup.
- [x] **Step 3: Add RED compatibility test** with an old-style `S3Operations` implementation or reflection seam; assert auto-configuration fails with a stable compatibility message instead of `NoSuchMethodError`.
- [x] **Step 4: Implement `readMetadata` and shared HEAD helper** under `withContext(Dispatchers.IO)`. Replace byte-download `listPage` pre-check with `headObject`; compare actual count to both the HEAD snapshot and `maxSizeBytes`. Apply the same comparison to Path download.
- [x] **Step 5: Implement auto-configuration guard** that checks an implementation-declared `headObject` method before creating S3 storage. Preserve the existing missing-bean guard and never fall back to list or resource metadata.
- [x] **Step 6: Run S3/autoconfiguration tests and compile.**

  Run `./gradlew :bluetape4k-images-spring-boot:test --tests '*S3ImageStorageTest' --tests '*ImagesStorageAutoConfigurationTest' --no-daemon` and `./gradlew :bluetape4k-images-spring-boot:compileKotlin --no-daemon`.

## Task 4: Capability-preserving metrics decorator

**Files:**
- Modify: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/metrics/MetricImageStorage.kt`
- Create: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/metrics/MetricImageStorageWithMetadata.kt`
- Modify: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/metrics/ImageStorageMetricsBeanPostProcessor.kt`
- Modify: `images-spring-boot/src/test/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesMetricsAutoConfigurationTest.kt`

- [x] **Step 1: Add RED wrapper tests** for supported capability preservation, unsupported custom storage absence, and one-layer double-wrap prevention.
- [x] **Step 2: Implement the conditional wrapper branch** while retaining existing `MetricImageStorage` constructor and delegation behavior.
- [x] **Step 3: Run metrics/autoconfiguration tests and module compile.**

## Task 5: Public docs, release train metadata, and compatibility evidence

**Files:**
- Modify: `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/ImageUploadResult.kt`
- Modify: `images-spring-boot/README.md`
- Modify: `images-spring-boot/README.ko.md`
- Modify: `docs/superpowers/specs/2026-08-15-issue-493-storage-metadata-design.md`
- Modify: `docs/superpowers/plans/2026-08-15-issue-493-storage-metadata.md`
- Create: `docs/lessons/2026-08-15-issue-493-storage-metadata.md`

- [x] **Step 1: Update Korean KDoc and both README locales.** Document opaque ETag (including multipart non-MD5), quote preservation, nullable unsupported fields, last-modified precision, capability discovery, HEAD→download race guard, and AWS catalog minimum version.
- [x] **Step 2: Record the exact upstream PR SHA and `bluetape4k-dependencies` catalog ref** in the release-train evidence; do not invent a version absent from live metadata.
- [x] **Step 3: Write the Korean lesson** with context, decision, outcome, verification, surprise (compileOnly upstream ABI), and future guard (runtime reflection gate).
- [x] **Step 4: Run `git diff --check` and read back all Markdown/KDoc** with the Korean naturalness checklist.

## Task 6: Full verification and review gates

- [ ] **Step 1: Run targeted tests** for model, Local, S3, metrics, and auto-config sequentially.
- [ ] **Step 2: Run module compile, detekt, and `./gradlew :bluetape4k-images-spring-boot:build --no-daemon`.**
- [ ] **Step 3: Run verifier mapping** from every spec acceptance criterion to source/test/doc evidence; record gaps as issue/follow-up, not hidden assumptions.
- [ ] **Step 4: Run six independent final code-review lanes per module slice** (public model/core, Local, S3, metrics/docs) and main integration; P0/P1 must be zero.
- [ ] **Step 5: Commit implementation and lesson with Lore trailers**, push exact head, create Korean PR linked to #493 and #507, mirror assignee/milestone/labels, and end the body with `## DoD Status`.
- [ ] **Step 6: Wait for exact-head CI and fresh review.** Report merge-ready evidence; merge, local sync, and cleanup remain blocked until fresh approval.

## Rollback

If upstream CI or image CI fails, revert the image branch first only after the
AWS artifact is restored to the prior compatible version; for a full rollback,
restore the AWS upstream artifact/BOM first and then the image artifact. The
runtime compatibility guard must fail startup rather than invoke a missing
method. Do not use list/resource fallback to bypass a failed HEAD contract.

## Traceability

| Spec requirement | Plan task | Proof |
|---|---|---|
| Source-compatible optional capability | 1, 4 | model ABI + wrapper tests |
| Local no-body metadata | 2 | Local attributes/no-body tests |
| Single S3 HEAD snapshot | 0, 3 | upstream request count + image order tests |
| HEAD/download race fail-closed | 3 | small/large mismatch tests |
| Unsupported fields nullable, opaque ETag | 1, 5 | model/KDoc/README tests/read-back |
| Runtime compatibility and rollback | 3, 5, Rollback | reflection guard + release evidence |
| Korean docs and lesson | 5 | SPW gate + read-back |

## SPW writer gate

- SPW-01: PASS — spec, current source, issue #493/#507, and upstream contract are the sources.
- SPW-02: PASS — ordered files, RED/GREEN commands, rollback, traceability, and stop conditions are concrete.
- SPW-03: PASS — Korean technical register, code tokens, commands, and URLs are preserved.
- SPW-04: PASS — every accepted spec item maps to a task and proof command.
- SPW-05: PASS — final plan read-back found no placeholders or unresolved file/symbol names.
