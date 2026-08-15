# #493 STORAGE-1 설계·계획 통합 리뷰

## 리뷰 범위와 근거

이번 리뷰는 ImageStorage metadata capability와 stacked train 선행
bluetape4k-aws HEAD 계약을 대상으로 한다. 다음 자료를 독립적으로
대조했다.

- issue #493, epic #507, 그리고 merged #480/#497의 live GitHub 상태
- ImageStorage, LocalImageStorage, S3ImageStorage,
  ImagesStorageAutoConfiguration, metrics decorator의 현재 구현
- S3Operations, S3Resource, S3CoroutinesTemplate,
  MicrometerS3Operations의 upstream 구현
- 2026-08-15 설계 문서와 구현 계획
- API/호환성, 안정성/성능, 보안/운영, 테스트/검증, Kotlin/Spring 패턴,
  문서/릴리스의 여섯 관점

## 독립 관점별 판정

### API·호환성

기존 ImageStorage에 abstract method를 추가하면 외부 구현체와 decorator가
source/ABI에서 깨진다. 별도 ImageObjectMetadataReader capability와
provider-neutral ImageObjectMetadata를 선택한 것은 기존 소비자 호환성과
지원 여부의 명시성을 모두 보존한다. metrics BPP가 capability를 잃는 문제가
있으므로 capability 전용 wrapper를 조건부로 선택해야 한다.

upstream S3Operations.headObject는 기존 custom 구현체를 즉시 깨지 않도록
unsupported default를 제공하되, 성공한 것처럼 보이는 resource/list fallback은
두지 않는다. 이것이 compileOnly 구 runtime의 조용한 correctness 저하를 막는다.

### 안정성·성능

기존 S3Resource.contentLength()와 lastModified()를 각각 호출하면 서로 다른
HEAD snapshot을 읽을 수 있다. 한 번의 async headObject 응답을 metadata와
download pre-check에 재사용하고, 실제 body byte 수를 post-check하는 계획은
교체 race와 size drift를 모두 fail closed한다. Local은 attributes만 읽어 body
materialization을 피한다.

모든 blocking Local/S3 경계는 Dispatchers.IO에 두고, upstream은
S3AsyncClient와 await를 사용한다. CancellationException은 양쪽에서
변환하지 않는다. Path download mismatch 시 기존 destination을 유지하고 staged
파일을 정리하는 테스트가 계획에 포함되어 있다.

### 보안·운영

image 모듈의 AWS 의존성은 compileOnly이므로 오래된 runtime에서 직접 method
호출하면 NoSuchMethodError가 발생할 수 있다. S3 bean 생성 전에
headObject capability를 점검해 안정적인 compatibility failure로 전환하고,
구 artifact로 되돌릴 때 AWS artifact/BOM을 먼저 복구하는 순서를 명시했다.
HEAD failure를 listPage 또는 S3Resource로 감추지 않는 fail-closed 정책도
수용 기준에 고정했다.

ETag은 MD5나 content hash가 아닌 opaque token으로 유지하고, Local의 추측값은
null로 둔다. public exception에 새 backend 경로·credential·SDK raw message를
노출하지 않는다.

### 테스트·검증

단일 HEAD 호출, metadata body-read 0회, HEAD→download 순서, 양방향 size mismatch,
취소·권한·일시 오류, staged cleanup, metrics capability 보존, upstream ETag/Instant
정밀도를 모두 targeted acceptance로 정의했다. public model의 음수 size와 Java
signature도 검증한다. 구현 후 모듈 compile/detekt/build와 six-lane final review를
순서대로 실행한다.

### Kotlin·Spring 패턴

immutable data class와 suspend capability를 사용하고 AWS SDK 타입을 public image
API로 유출하지 않는다. ImageStorageMetricsBeanPostProcessor는 이미 metric
wrapper인 bean을 다시 감싸지 않으며, unsupported custom storage에 metadata
capability를 거짓으로 광고하지 않는다. 기존 생성자와 delegation을 유지한다.

### 문서·릴리스

한국어 KDoc과 양쪽 README에 nullable field, opaque ETag, last-modified 정밀도,
capability 탐색, HEAD/download race 방어, AWS 최소 버전을 함께 기록한다. upstream
PR SHA와 dependency catalog ref는 live metadata에서 확인한 값만 release-train
evidence에 넣는다. lesson에는 compileOnly ABI 경계와 runtime guard를 남긴다.

## 결정과 잔여 위험

이전 리뷰의 P1은 다음 결정으로 해소됐다.

1. ImageStorage 공개 abstract API 확장은 거부하고 optional capability로 분리한다.
2. 두 번의 S3Resource HEAD는 upstream 단일 async headObject로 대체한다.
3. HEAD pre-check만 믿지 않고 실제 byte count와 snapshot size를 비교한다.
4. compileOnly 구 runtime은 startup compatibility guard로 fail closed한다.
5. metrics wrapper는 capability를 조건부 보존한다.

잔여 관찰성 metric(readMetadata 전용 이름)은 이번 범위에서 의도적으로
추가하지 않고 후속 train으로 추적한다. 이는 기능 correctness를 막는 P1이 아니다.

## 통합 판정

- P0: 0
- P1: 0
- P2: 0 (관찰성 metric은 명시된 후속 범위)
- P3: 0
- 설계 상태: **CLEAR — 구현 진행**

## 구현 후 독립 code-review 결과

구현 branch의 24개 변경 경로를 두 개의 독립 lane에서 다시 대조했다.

- P0: 0건
- P1/PENDING: 기본 image catalog가 아직 upstream `headObject`를 포함하지 않는
  `bluetape4k-aws-spring-boot:0.6.0-SNAPSHOT`을 해석하므로, catalog 기본값의
  `compileKotlin`이 `S3ObjectMetadata`와 `headObject`에서 실패한다. upstream PR
  [#516](https://github.com/bluetape4k/bluetape4k-aws/pull/516)의 exact head
  `24c8039006220de654c732f722f3c7beb9b5b74f`가 merge·publish된 뒤 동일한
  catalog로 재검증해야 한다.
- P2/PENDING: Java fixture는 현재 interface의 default를 상속하는 source fixture이므로
  pre-`headObject` classloader 조합을 완전히 재현하지 않는다. 또한 byte-array 경로는
  upstream `downloadBytes` 계약상 body materialization 뒤 size를 검사한다.
- P2: Local metadata의 symlink/non-regular 경계와 capability-preserving metrics
  double-wrap을 추가 테스트했으며, `lastModified`의 backend/filesystem 정밀도와
  sub-second 비보장 문구를 README/KDoc에 고정했다.
- P3: 새 테스트 이름을 descriptive backtick 형식으로 정규화했다.

따라서 구현 review의 현재 상태는 **REQUEST CHANGES / PENDING**이다. 이는
`listPage`·resource fallback으로 우회할 결함이 아니라 stacked train의 upstream
artifact 정렬 hold이다. 기본 catalog compile, image targeted tests, image-side
ABI 및 exact-head CI가 fresh하게 통과하기 전에는 PR을 merge-ready로 표시하지 않는다.

설계 rerun lane 세 개와 계획 rerun lane 세 개는 bounded 대기 내 독립 결과를
반환하지 않아 PENDING으로 기록하고 중단했다. 이를 PASS로 간주하지 않는다.
architecture lane의 중간 판정과 main session의 원문 대조에서는 P0/P1이
확인되지 않았으므로 reversible한 upstream TDD 선행 작업은 진행하되, 최종
구현/PR 판정은 fresh 독립 code-review 결과 없이는 완료 처리하지 않는다.

## SPW writer gate

- SPW-01: PASS — issue/epic와 현재 Local/S3/upstream 구현을 근거로 삼았다.
- SPW-02: PASS — finding, disposition, acceptance, 잔여 위험을 분리했다.
- SPW-03: PASS — 한국어 기술 문체로 작성하고 code token/URL을 보존했다.
- SPW-04: PASS — 여섯 독립 관점과 독립 rerun 상태를 명시했다.
- SPW-05: PASS — heading/table/code token read-back에서 누락과 scope drift가 없다.
