# Issue #175 OCR native CI 연구

- 이슈: [#175](https://github.com/bluetape4k/bluetape4k-image/issues/175)
- 날짜: 2026-06-06
- 범위: `images-ocr`가 GitHub Actions에서 host-native OCR을 복원해야 하는지, 아니면 해당 lane을 local/manual로 유지해야 하는지 결정한다.

## evidence

- PR #174 CI run `27026749188`은 library alias가 존재하기 전 `ubuntu-24.04`에서 실패했다. Lept4J/JNA가 예상한 Leptonica library name을 load하지 못했다.
- PR #174 CI run `27027373907`은 `liblept.so.5`에서 `libleptonica.so`로 alias를 추가했지만, 이후 `/lib/x86_64-linux-gnu/liblept.so.5`에 `pixAddMultipleBlackWhiteBorders`가 없어 실패했다.
- local `dependencyInsight`:
  - `net.sourceforge.tess4j:tess4j:5.19.0`
  - `net.sourceforge.lept4j:lept4j:1.24.0`
- `lept4j-1.24.0.jar`에 대한 local `javap` 결과 `Leptonica1.pixAddMultipleBlackWhiteBorders(...)`가 native API registration surface의 일부임을 확인했다.
- Ubuntu Noble은 Leptonica `1.82.0-3build4` 기반 `liblept5`를 publish한다.
- Lept4J release notes는 Ubuntu Noble package line보다 최근 runtime 이동을 보여준다. Lept4J 1.20은 Leptonica 1.85.0, 1.22는 1.86.0, 1.23은 1.87.0으로 upgrade했다. 현재 1.24.0 line은 더 새로운 native API expectation을 상속한다.
- local macOS validation environment는 Tesseract 5.5.2와 Leptonica 1.87.0을 보고한다.

## 결정

이 issue에서는 CI에서 Leptonica/Tesseract를 source build하지 않는다. 그렇게 하면 OCR job이 느려지고 두 번째 native-runtime maintenance surface가 생긴다. GitHub CI와 Nightly OCR gate는 portable Testcontainers smoke path를 실행하는 `-Docr.container.enabled=true`로 유지한다. `-Docr.enabled=true`는 호환 native library가 있는 developer machine용 local/manual host-native Tess4J check로 남긴다.

## 영향

- CI/Nightly behavior가 정렬된다. 둘 다 container-backed OCR gate를 실행한다.
- host-native quickstart는 local validation에 계속 유용하며 필요한 native runtime compatibility를 문서화한다.
- 향후 host-native CI 복원은 유지되는 native runtime source를 먼저 선택해야 한다. 후보는 더 새로운 runner image, source-built Leptonica/Tesseract cache, 또는 runner package set과 의도적으로 호환되는 Tess4J/Lept4J version line이다.

## 출처

- Lept4J release notes: https://tess4j.sourceforge.net/lept4j.html
- Tess4J 5.19.0 metadata: https://mvnrepository.com/artifact/net.sourceforge.tess4j/tess4j/5.19.0
- Ubuntu Noble `liblept5`: https://launchpad.net/ubuntu/noble/arm64/liblept5
- Leptonica `pixAddMultipleBlackWhiteBorders` API reference: https://tpgit.github.io/Leptonica/pix2_8c.html
