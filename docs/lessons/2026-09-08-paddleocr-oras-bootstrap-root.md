# ORAS 추출 디렉터리는 추출 함수가 생성한다

## 실패한 가정과 증거

호출부가 도구 디렉터리를 미리 만들어도 된다고 가정했다.
[PRODUCE 34236595144](https://github.com/bluetape4k/bluetape4k-image/actions/runs/34236595144)는
OCI build와 handoff 검증까지 성공했지만 ORAS bootstrap에서 `SCHEMA_INVALID`로 중단됐다.
고정된 ORAS 1.3.4 archive로 직접 확인한 내부 오류는
`archive destination already exists`였다. 같은 archive는 새 목적지에 정상 추출됐다.

`bootstrap_oras_archive`는 hash·파일 목록을 검증한 뒤 `extract_archive`를 호출한다.
이 함수가 새 디렉터리를 만들고 파일을 기록한다. 기존 디렉터리를 거부하는 것은
이전 파일이나 심볼릭 링크가 섞이는 것을 막는 계약이다.

## 수정과 검증

5개 bootstrap 호출부에서 `.producer-state/tools` 대신 상위 `.producer-state`만 만든다.
ORAS 버전·hash·파일 허용 목록·실행 권한과 기존 목적지 거부 동작은 변경하지 않는다.

회귀 테스트는 각 job의 실제 준비 shell 명령을 실행하고 다운로드만 로컬 tar fixture로
대체한 뒤 실제 bootstrap 함수를 호출한다. 수정 전에는 5곳 모두 기존 목적지 오류로
실패했고, 수정 후에는 추출에 성공했다. 같은 목적지로 다시 추출하면 거부되며
기존 binary가 보존되는 것도 확인한다. fixture의 hash 치환은 테스트 범위에만 적용한다.

Python 3.9와 3.13에서 workflow 13개, producer 93개 테스트가 각각 통과했다.
실제 ORAS 1.3.4 archive도 고정 hash 검증 후 5개 호출 경로에서 정상 추출됐다.
이 검증은 원격 registry 게시·attestation 성공을 의미하지 않는다.
병합 후 새 PRODUCE에서 후속 단계를 확인해야 한다.

## 재발 방지

파일·디렉터리 생성 주체를 함수 계약에서 먼저 확인한다. bootstrap처럼 shell과 Python을
연결하는 경로는 helper 단위 테스트뿐 아니라 호출부의 준비 명령까지 포함해 검증한다.
기존 목적지 거부를 완화하거나 사전 삭제로 우회하지 않는다.
