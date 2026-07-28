# WIP - bluetape4k-image

스냅샷: 2026-07-14 KST

이 로드맵은 `debop`에게 할당된 열린 GitHub issue를 추적한다. 현재 release
line closeout과 Backlog milestone으로 미룬 작업을 분리해서 본다.

## 현재 개발 상태

- [`0.3.0`](https://github.com/bluetape4k/bluetape4k-image/releases/tag/0.3.0)은
  2026-06-27에 게시된 최신 stable release이다.
- `develop`은 `baseVersion=0.4.0`을 사용한다. `0.4.0` milestone은 아직 개발
  중이며 release되지 않았다.
- 이 로드맵 갱신은
  [#271](https://github.com/bluetape4k/bluetape4k-image/issues/271)에서
  추적한다. 이 변경이 merge된 뒤에는 아래 changelog backfill이 남은
  `0.4.0` milestone closeout 항목이다.

## 0.4.0 마감 작업

- [#270](https://github.com/bluetape4k/bluetape4k-image/issues/270)은 완료되고
  추적 가능한 `0.4.0` 작업을 기준으로 `CHANGELOG.md`를 보강한다. release를
  게시하거나 artifact version을 바꾸지 않는다.

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

`0.4.0` release date는 아직 정해지지 않았다. release publication, version 변경,
milestone closure는 changelog가 완료된 milestone 작업을 반영한 뒤 별도의 검증된
release workflow로만 진행한다.
