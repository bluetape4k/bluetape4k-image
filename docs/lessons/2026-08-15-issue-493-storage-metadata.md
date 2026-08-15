# #493 스토리지 객체 메타데이터 capability 교훈

## 배경

`ImageStorage` 소비자가 body를 내려받지 않고 object size, ETag, content type,
last-modified를 확인할 수 있어야 했다. 기존 `ImageStorage`에 abstract method를
추가하면 외부 구현체와 decorator의 source/ABI가 깨지고, S3의
`S3Resource.contentLength()`와 `lastModified()`를 조합하면 서로 다른 HEAD
snapshot을 볼 수 있었다.

## 결정

- `ImageObjectMetadata`와 `ImageObjectMetadataReader`를 별도 optional capability로
  두어 기존 `ImageStorage` 계약을 보존했다.
- Local은 `BasicFileAttributes`만 읽고 ETag/content type은 추측하지 않는다.
- upstream `bluetape4k-aws`에 단일 `S3Operations.headObject` snapshot을 먼저
  추가했다. 이미지 모듈은 upstream PR [#516](https://github.com/bluetape4k/bluetape4k-aws/pull/516)
  commit `24c8039006220de654c732f722f3c7beb9b5b74f`를 기준으로 통합한다.
- S3 byte/Path download는 HEAD → body → snapshot size 비교 순서를 지키며, HEAD
  실패·크기 불일치·limit 초과를 모두 fail closed한다.
- Micrometer decorator는 metadata capability를 가진 delegate에만
  `MetricImageStorageWithMetadata`를 선택한다. 지원하지 않는 custom storage에는
  capability를 광고하지 않는다.
- compileOnly upstream 경계에서는 구현체가 `headObject`를 실제로 선언했는지
  startup reflection guard로 확인한다. 구 runtime에서 첫 요청까지 기다리며
  `NoSuchMethodError`/unsupported default를 노출하지 않는다.

## 검증 결과

- AWS upstream focused tests 6건 통과 및 `S3Operations` `javap` signature 확인.
- 이미지 S3 race/cancellation/streaming focused tests 16건 통과.
- storage auto-configuration 11건, metrics/autoconfiguration 7건, model/capability
  4건이 통과했다.
- Local metadata tests는 body를 열지 않고 attributes를 반환하며 missing/directory
  매핑을 확인한다.
- 로컬 이미지 검증은 upstream PR artifact를 `mavenLocal`의
  `0.6.0-issue493-SNAPSHOT`으로 고정한 임시 catalog를 사용했다. 안정 release
  version을 임의로 문서화하지 않았고, 현재 image catalog ref는
  `45235aa22184b6a2280f530fb90c82a94e31c59d`, dependency catalog 검증 ref는
  `9db9c2c65d8d4663f2658b0f0cf1a15b43d02a15`이다.
- 기본 catalog 검증은 upstream PR이 아직 merge/publish되지 않아
  `S3ObjectMetadata`/`headObject`에서 실패했다. 따라서 임시 catalog GREEN은
  train 내부 검증으로만 취급하고, upstream PR #516 merge 이후 기본 catalog와
  exact-head CI를 다시 실행해야 한다.

## 예상 밖의 점과 후속 조치

Kotlin interface의 default suspend method는 새 compiler로 구현체를 다시 컴파일할
때 bridge method가 생길 수 있다. 따라서 source-level “override를 쓰지 않은
class”만으로 구 runtime을 재현하면 안 되며, Java consumer fixture처럼 실제
구 binary에 method가 없는 경우를 reflection guard 테스트로 고정해야 한다.

upstream PR이 merge되어 aligned catalog에 반영되기 전까지 이미지 PR의 기본
repository build는 해당 snapshot을 직접 가져오지 못할 수 있다. 이 기간에는
stack 순서(AWS PR → image PR)를 유지하고, 각 PR의 exact head와 CI를 별도로
확인한다.

## 다음 수정자를 위한 경고

ETag은 opaque token이다. multipart ETag을 MD5로 바꾸거나 Local에서 size/hash를
ETag으로 추측하지 말 것. S3 download의 HEAD pre-check 또는 post-download size
검사를 제거하거나 `S3Resource` property fallback을 되살리면 object 교체 경합과
limit 우회가 다시 열린다.
