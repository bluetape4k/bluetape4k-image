# OCI export 전에 Docker image store를 확인한다

## 실패한 가정과 근거

`docker buildx build --output type=oci`를 지정하면 기본 runner에서도 OCI archive가
생성된다고 가정했다. 그러나 [PRODUCE 34231854899](https://github.com/bluetape4k/bluetape4k-image/actions/runs/34231854899)의
image-build 단계는 `OCI exporter is not supported for the docker driver.`로 실패했다.
입력·모델·artifact 검증은 통과했지만 이미지 생성과 게시에는 도달하지 못했다.

## 수정 결정

새 builder 이미지를 추가하지 않고, GitHub의 임시 `ubuntu-24.04` image-build runner에서
containerd image store를 활성화한다. 기존 daemon 설정과 다른 feature를 보존하고,
후보 설정을 검증한 후 설치·재시작한다. 실제 DriverStatus에서 snapshotter를 확인하지
못하면 빌드를 중단한다. 빌드는 `--builder default`를 명시한다.

Docker 설정 변경은 이 job에만 적용한다. 개발자의 로컬 Docker나 Colima 설정은 변경하지 않는다.
기존의 network-none, no-cache, linux/amd64, base image digest 고정 조건도 유지한다.

## 검증과 재발 방지

워크플로에 설정 단계가 없는 상태에서 회귀 테스트의 실패를 확인했다. 수정 후 테스트는
기존 설정 보존, 설정 파일 부재, 후보 설정 검증 실패 시 설치·재시작 중단,
snapshotter 확인 실패 시 빌드 중단을 검증한다. Docker 명령은 테스트에서 대체하므로
실제 OCI archive 생성의 증거는 아니다. 병합 후 hosted PRODUCE에서 다시 확인해야 한다.

앞으로 exporter를 선택할 때 CLI 옵션뿐 아니라 선택된 driver와 image store의 지원 조건을
함께 확인한다. workflow 정적 검사 통과만으로 native build 성공을 선언하지 않는다.

## 기준 정보

2026-09-08에 공식 문서와 Buildx 구현을 확인했다.

- [Docker containerd image store](https://docs.docker.com/engine/storage/containerd/)
- [Buildx docker driver 기능 판정](https://github.com/docker/buildx/blob/master/driver/docker/driver.go)
