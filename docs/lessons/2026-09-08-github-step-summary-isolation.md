# GitHub Actions step summary 격리 lesson

## 배경

Issue #638의 첫 `PRODUCE` 실행인 run `34221583515.1`에서 validation 본문과
`write-step-summary` 명령은 성공했지만, 다음 `Emit terminal result` step의
`test -s "$GITHUB_STEP_SUMMARY"`가 실패했다. 이 실패로 image build와 registry
push는 시작되지 않았고 producer는 `FAILED`로 종료됐다.

GitHub Actions의
[`GITHUB_STEP_SUMMARY`](https://docs.github.com/en/actions/reference/workflows-and-actions/variables#default-environment-variables)는
현재 step에만 고유한 파일 경로다. 같은 job의 다음 step에서는 다른 경로를 받으므로
이 변수를 step 간 성공 receipt로 사용할 수 없다.

## 결정

- job summary는 `Write step summary` step에서만 작성한다.
- 같은 step에서 CLI의 canonical 결과 envelope를
  `.producer-state/step-summary-result.json`에 저장한다.
- 다음 `Emit terminal result` step은 이 안정적인 파일을 읽어 `command`, `status`,
  `errorCode`를 검증한다.
- summary 작성이 실패하거나 failure envelope를 반환하면 terminal step도 fail closed한다.
- workflow run 조회 adapter에는 GitHub API가 받는 workflow basename을 전달하고,
  선택된 run의 실제 path는 `--expected-workflow`로 별도 검증한다.

## 결과

per-step 환경 파일을 다른 step에서 읽는 잘못된 생명주기 가정을 제거했다. producer의
terminal 검증은 화면용 Markdown 경로 대신 machine-readable receipt에 결합된다.

## 검증

- 실패 증거: GitHub Actions run `34221583515.1`, validation의
  `Emit terminal result` exit code 1.
- Python 3.9와 3.13에서 producer 관련 8개 suite가 각각 통과했다.
- `actionlint`, Ruff, `git diff --check`, local terminal receipt smoke가 통과했다.

## 향후 지침

`GITHUB_STEP_SUMMARY`, `GITHUB_OUTPUT`, `GITHUB_ENV`, `GITHUB_PATH`처럼
GitHub가 제공하는 environment file은 문서화된 생명주기 밖에서 재사용하지 않는다.
step 간 전달이 필요하면 artifact, job output 또는 저장소가 검증하는 명시적 receipt 파일을
사용하고, 소비 step에서 schema와 성공 상태를 다시 확인한다.
