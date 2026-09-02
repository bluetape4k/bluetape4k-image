# WIP - bluetape4k-image

기준 시점: 2026-09-02 KST

이 로드맵은 `debop`에게 할당된 열린 GitHub issue와 deferred decision record를
추적한다. 현재 release line closeout과 Backlog milestone으로 미룬 작업을
분리해서 본다.

## 현재 개발 상태

- [`0.4.0`](https://github.com/bluetape4k/bluetape4k-image/releases/tag/0.4.0)은
  2026-08-06에 게시된 최신 stable release이다.
- `develop`은 `baseVersion=1.0.0`을 사용한다. `0.4.0` release와 milestone
  closeout은 완료되었고, 이슈
  [#619](https://github.com/bluetape4k/bluetape4k-image/issues/619)에서
  `1.0.0` 정식 배포 source를 stable Dependencies catalog에 고정한다.
- 문서 후속 작업인
  [#462](https://github.com/bluetape4k/bluetape4k-image/issues/462)은
  [PR #463](https://github.com/bluetape4k/bluetape4k-image/pull/463)으로
  merge되어 한국어 `Fixed` category를 `버그 수정`으로 통일했다. 이 작업은
  runtime behavior, public API, dependency, release를 변경하지 않는다.
- [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513)은
  `OPEN / Backlog / BACKLOG / DEFERRED` 상태의 AI/ML 연구 umbrella epic이다.
  현재 실행 가능한 AI/ML child train은 없다.
- [#270](https://github.com/bluetape4k/bluetape4k-image/issues/270)의 changelog
  backfill과 [#271](https://github.com/bluetape4k/bluetape4k-image/issues/271)의
  roadmap refresh는 완료되었다.

## 0.4.0 마감과 다음 개발선

- [#270](https://github.com/bluetape4k/bluetape4k-image/issues/270)은 완료되고
  추적 가능한 `0.4.0` 작업을 기준으로 `CHANGELOG.md`를 보강했다. release를
  게시한 뒤에도 artifact version을 임의로 바꾸지 않았다.
- [#271](https://github.com/bluetape4k/bluetape4k-image/issues/271)의 roadmap
  refresh도 완료되어 현재 milestone 상태를 반영한다.
- [#462](https://github.com/bluetape4k/bluetape4k-image/issues/462)은
  [PR #463](https://github.com/bluetape4k/bluetape4k-image/pull/463)으로
  merge되어 한국어 changelog의 `Fixed` heading을 `버그 수정`으로 표준화했다.
  이 문서 작업은 추가 release publication이나 version 변경을 수행하지 않았다.
- 다음 개발선에서 [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513)은
  model license, immutable artifact, producer provenance, SBOM/signature,
  offline receipt, 운영 비용을 모두 확보할 때까지 Backlog에 보존한다.

## 연기된 Backlog

다음 issue는 고정 release target이 없고 현재 active train에 포함되지 않는다.

- [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513) - AI/ML
  backend 연구 umbrella (`OPEN / Backlog / BACKLOG / DEFERRED`).
- [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) - 고급 문서
  OCR을 위한 PaddleOCR 평가 (`Backlog / DEFERRED`).
- [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3)와
  [#551](https://github.com/bluetape4k/bluetape4k-image/issues/551) - image
  classification ONNX backend 채택 검증 (`DEFER`).
- [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544),
  [#545](https://github.com/bluetape4k/bluetape4k-image/issues/545),
  [#609](https://github.com/bluetape4k/bluetape4k-image/issues/609),
  [#611](https://github.com/bluetape4k/bluetape4k-image/issues/611) - PaddleOCR
  benchmark·공급망·trusted producer 재개 gate.
- [#547](https://github.com/bluetape4k/bluetape4k-image/issues/547) - PaddleOCR
  최종 adoption gate (`CLOSED / DEFER`); 재개 증거는 새로운 결정의 입력으로만
  사용한다.
- [#203](https://github.com/bluetape4k/bluetape4k-image/issues/203) - OCR 추출
  benchmark 추가.
- [#204](https://github.com/bluetape4k/bluetape4k-image/issues/204) - storage
  upload/download 경로 benchmark.
- [#205](https://github.com/bluetape4k/bluetape4k-image/issues/205) - Ktor
  multipart thumbnail route benchmark.
- [#206](https://github.com/bluetape4k/bluetape4k-image/issues/206) - batch와
  thumbnail pipeline throughput benchmark.
- [#207](https://github.com/bluetape4k/bluetape4k-image/issues/207) -
  algorithmic hot path benchmark.

## Release 경계

`0.4.0` release는 2026-08-06에 게시되었고, `develop`은 `baseVersion=1.0.0`의
정식 배포 후보이다. #619의 release-prep와 exact-head CI/Nightly 검증이 끝난 뒤에만
`1.0.0` tag를 생성한다.
[#462](https://github.com/bluetape4k/bluetape4k-image/issues/462)와
[PR #463](https://github.com/bluetape4k/bluetape4k-image/pull/463)의 문서 작업
완료와 #513의 backlog 전환은 추가 release publication이나 version 변경을
의미하지 않는다. 다음 release는 changelog가 완료된 milestone 작업을 반영한
뒤 별도의 검증된 release workflow로만 진행한다.
