# Kover Coverage 정책

## 현재 상태

`bluetape4k-image`는 image module 전체의 Kover report를 집계한다. 현재는 어떤
module도 실패를 유발하는 Kover threshold를 강제하지 않는다.

## 정책

상태: report-only transition.

순수 image processing code와 native libvips/Panama variant는 testability와 platform
constraint가 서로 다르다. 따라서 repository 전체에 하나의 threshold를 적용하지
않는다.

## Threshold 계획

- Kover는 build gate가 아니라 trend signal로 다룬다.
- Nightly XML report와 기존 coverage artifact upload를 사용해 coverage regression을
  찾는다.
- 특정 module의 coverage 보강이 필요하면 focused issue를 연다. 기본 enforcement
  mechanism으로 실패 threshold를 도입하지 않는다.
- benchmark module은 production coverage gate에서 제외한다.

## CI/Nightly 계약

Nightly coverage는 정보 제공용으로 유지한다. Kover XML report와 기존 coverage
artifact upload는 가시성을 위해 유지하지만, future issue가 해당 gate를 명시적으로
되살리지 않는 한 CI와 Nightly는 고정 coverage percentage 미달만으로 실패하지
않아야 한다.
