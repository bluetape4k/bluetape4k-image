# 이슈 #194 Snapshot 전체 검증 관문

## 배경

`publish-snapshot.yml`은 성공한 모든 `Nightly` workflow 실행을 트리거로
사용했다. Nightly 스모크 일정은 OCR과 VIPS job을 건너뛰어도 성공할 수
있으므로 native/OCR 모듈을 검증하지 않고도 snapshot 게시가 진행될 수
있었다.

## 결정

게시 job을 시작하기 전에 트리거가 된 Nightly 실행의 job conclusion을
검사하도록 snapshot 게시 자격 판단을 `publish-snapshot.yml`로 옮긴다.

## 결과

이제 snapshot을 게시하려면 트리거가 되었거나 수동으로 지정한 Nightly
실행에서 전체 OCR 및 VIPS job이 성공해야 한다. 수동 dispatch에서는
명시적인 override를 사용할 수 있지만, 이 우회 여부는 workflow input에
드러난다.

## 검증

- `actionlint .github/workflows/publish-snapshot.yml`
- `git diff --check`
- `rg -n -F "\\'" .github/workflows/publish-snapshot.yml`
- OCR을 건너뛴 결과와 모든 OCR/VIPS job이 성공한 결과에 대한 셸 시뮬레이션

## 향후 지침

상위 workflow에 스모크 범위와 전체 범위가 함께 있다면 workflow 전체의
`workflow_run.conclusion == success`를 게시 관문으로 사용하지 않는다.
필수 job의 conclusion을 검사하거나 게시 가능 여부를 나타내는 명시적인
신호를 전달해야 한다.
