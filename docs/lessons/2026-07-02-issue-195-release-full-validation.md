# 이슈 #195 Release 전체 검증 관문

## 배경

release workflow는 release metadata를 검증한 뒤, tag commit이 전체 이미지
모듈 검증을 통과했는지 확인하지 않고 Maven Central 아티팩트를 게시했다.

## 결정

release commit을 대상으로 필수 이미지, OCR, VIPS job이 모두 성공한 Nightly
실행이 있는지 확인하는 release 사전 검사 job을 추가한다.

## 결과

이제 Maven Central release 게시는 `release-validation`에 의존한다. Tag
push에서는 tag commit에 성공한 Nightly 실행이 있는지 조회한다. 수동
dispatch에서는 검증 run ID를 제공하거나 명시적인 override를 사용할 수
있다.

## 검증

- `actionlint .github/workflows/release.yml`
- `git diff --check`
- `rg -n -F "\\'" .github/workflows/release.yml`
- OCR을 건너뛴 결과와 모든 이미지 job이 성공한 결과에 대한 셸 시뮬레이션

## 향후 지침

stable 게시 workflow에서는 metadata 검사를 런타임 검증의 대체 수단으로
사용하면 안 된다. Native/OCR/VIPS 의존도가 높은 release는 Maven Central
게시 전에 정확한 release commit에 대한 job 수준 검증 근거가 필요하다.
