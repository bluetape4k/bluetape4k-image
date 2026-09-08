# PaddleOCR artifact digest 표기 경계

## 배경과 원인

[실행 34227504119.1](https://github.com/bluetape4k/bluetape4k-image/actions/runs/34227504119)은
validation, source 재현성, model staging에 성공한 뒤 `validate-build-artifacts`에서
`SCHEMA_INVALID`로 종료됐다. Docker build와 registry push는 시작하지 않았고,
최종 결과는 `FAILED`, `cleanupVerified=true`였다.

고정된 upload-artifact action의 출력은 접두사 없는 64자리 SHA-256이었다.
내부 `validate_same_run_artifact`는 `sha256:` 접두사가 있는 digest를 요구한다.
기존 CLI 테스트는 이미 접두사를 붙인 값을 사용해 실제 action 출력과의 차이를 놓쳤다.

## 결정

model, wheelhouse, OCI를 내보내는 job의 출력에서 `sha256:`를 붙인다.
내부 검증기의 엄격한 digest 계약과 파일 무결성 검증은 유지한다.
같은 해시라도 외부 도구 출력과 내부 문서의 표기 형식을 구분한다.

## 검증과 남은 작업

실제 run에서 관찰한 접두사 없는 값을 workflow 출력에 대입하고 내부 receipt
검증기로 전달하는 회귀 테스트를 추가했다. 수정 전 세 job에서 같은 예외가
발생했고, 수정 후 통과했다. Python 3.9와 3.13에서 workflow 11개 및 evidence
13개 테스트가 통과했다. Python 3.9의 producer CLI 70개 테스트와 actionlint,
Ruff, diff 검사도 통과했다.

이 검증은 새 hosted PRODUCE 성공을 증명하지 않는다. 병합 후 새 커밋에서
실행하고 image, registry, attestation, consumer 인계를 확인해야 한다.

## 이후 변경 시 확인

외부 action 버전을 변경할 때는 실제 출력 형식을 검증한다. 테스트 fixture를
내부 계약에 맞춰 미리 보정하지 말고, 외부 출력부터 소비 검증기까지 연결한다.
