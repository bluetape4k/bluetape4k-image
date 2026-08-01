# WIP - bluetape4k-image

스냅샷: 2026-08-01 KST

이 로드맵은 `debop`에게 할당된 열린 GitHub issue를 추적한다. 현재 release
line closeout과 Backlog milestone으로 미룬 작업을 분리해서 본다.

## 현재 개발 상태

- [`0.3.0`](https://github.com/bluetape4k/bluetape4k-image/releases/tag/0.3.0)은
  2026-06-27에 게시된 최신 stable release이다.
- `develop`은 `baseVersion=0.4.0`을 사용한다. `0.4.0` milestone은 아직 개발
  중이며 release되지 않았다.
- 현재 `0.4.0` milestone에는 열린 issue가 없다. 문서 후속 작업인
  [#462](https://github.com/bluetape4k/bluetape4k-image/issues/462)은
  [PR #463](https://github.com/bluetape4k/bluetape4k-image/pull/463)으로
  merge되어 한국어 `Fixed` category를 `버그 수정`으로 통일했다. 이 작업은
  runtime behavior, public API, dependency, release를 변경하지 않는다.
- [#270](https://github.com/bluetape4k/bluetape4k-image/issues/270)의 changelog
  backfill과 [#271](https://github.com/bluetape4k/bluetape4k-image/issues/271)의
  roadmap refresh는 완료되었다.

## 0.4.0 마감 작업

- [#270](https://github.com/bluetape4k/bluetape4k-image/issues/270)은 완료되고
  추적 가능한 `0.4.0` 작업을 기준으로 `CHANGELOG.md`를 보강했다. release를
  게시하거나 artifact version을 바꾸지 않았다.
- [#271](https://github.com/bluetape4k/bluetape4k-image/issues/271)의 roadmap
  refresh도 완료되어 현재 milestone 상태를 반영한다.
- [#462](https://github.com/bluetape4k/bluetape4k-image/issues/462)은
  [PR #463](https://github.com/bluetape4k/bluetape4k-image/pull/463)으로
  merge되어 한국어 changelog의 `Fixed` heading을 `버그 수정`으로 표준화했다.
  이 문서 작업은 release publication이나 version 변경을 수행하지 않았고,
  milestone은 현재 열린 issue 없이 유지된다.

## 연기된 Backlog

다음 issue는 고정 release target이 없고 `0.4.0` closeout 범위에 포함되지 않는다.

- [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) - image
  classification ML model 통합.
- [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) - 고급 문서
  OCR을 위한 PaddleOCR 평가.
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

`0.4.0` release date는 아직 정해지지 않았다.
[#462](https://github.com/bluetape4k/bluetape4k-image/issues/462)와
[PR #463](https://github.com/bluetape4k/bluetape4k-image/pull/463)의 문서 작업
완료는 release publication, version 변경, milestone closure를 의미하지 않는다.
이러한 작업은 changelog가 완료된 milestone 작업을 반영한 뒤 별도의 검증된
release workflow로만 진행한다.
