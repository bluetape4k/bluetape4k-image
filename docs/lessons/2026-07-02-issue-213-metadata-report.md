# Issue 213 Metadata report

## 배경

#213은 public response를 기본적으로 안전하게 유지하면서 기존 EXIF-only `readExif()` API를
넘어서는 metadata extraction이 필요했다. acceptance criteria에는 XMP, IPTC, ICC,
dimensions, orientation, page count, HDR/gain-map hint, byte/path/stream entry point, size
guard, sensitive-field stripping이 포함됐다.

## 결정

`bluetape4k-images`에 pure JVM `ImageMetadataReport` API를 추가한다.

- `readExif()`는 변경하지 않고 normalized EXIF mapping을 재사용한다.
- `ImageMetadataReadOptions`는 기본적으로 GPS를 strip하고 raw diagnostic을 생략한다.
- XMP, IPTC, ICC, dimensions, orientation, page count, HDR, gain-map hint의 scalar flag와
  summary를 노출한다.
- `InputStream`은 caller-owned로 유지하고, stream size guard에는 bounded in-memory reading을
  사용한다.
- optional backend adapter가 core module에 native libvips dependency를 추가하지 않고
  `withBackendHeaderFields()`를 통해 sanitized scalar header hint를 추가하게 한다. Diagnostic
  header field는 여전히 `includeDiagnosticTags = true`가 필요하다.

## 결과

caller는 API response용 public-safe report 또는 internal tooling용 explicit diagnostic report를
선택할 수 있다. report는 raw metadata blob, source path, native pointer, unbounded backend
payload를 담지 않는다.

## 검증

- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.analysis.ImageMetadataReportTest'`
- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.analysis.*'`
- `./gradlew :bluetape4k-images:test`
- `git diff --check`

## 향후 방지책

metadata enrichment를 위해 `bluetape4k-images`에 native backend dependency나 raw metadata
payload를 추가하지 않는다. backend module에 adapter-specific parsing을 추가하고 sanitized
scalar header fact만 `ImageMetadataReport.withBackendHeaderFields()`로 전달한다. backend
diagnostic field는 explicit opt-in 뒤에 둔다.
