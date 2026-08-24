# Issue #544 OCR 비교 benchmark corpus 연구

| 항목 | 내용 |
| --- | --- |
| Issue | [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544) |
| 상위 연구 | [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) PaddleOCR backend 평가 |
| Main epic | [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513) AI/ML backend 연구 train |
| Train 단계 | RESEARCH-1: #169 PaddleOCR corpus·metric·재현성 계약 |
| 조사일 | 2026-08-19 |
| 문서 유형 | Type E 연구 문서 |
| 범위 | Tesseract/Tess4J baseline과 PaddleOCR 후보를 비교할 corpus, 측정 절차, CI 계층, 채택 gate |
| 결정 | **benchmark 계약은 채택, 비교 실행은 PENDING, PaddleOCR provider 채택은 DEFER** |

이 문서는 #544의 corpus·metric·protocol 산출물을 고정하는 연구 기록이다. 동일
입력으로 Tesseract와 PaddleOCR를 실제 실행한 결과 보고서는 아직 아니며, 실패 사례와
품질·지연·RSS 개선 여부는 후속 benchmark 실행에서 채워야 한다. 이 문서의 범위에서는
PaddleOCR dependency, pretrained model, service container, production adapter를
추가하지 않는다. 따라서 이 문서가 완료되어도 live Issue #544의 비교 완료 조건은
`PENDING`으로 남는다.

## 결정 요약

현재 0.5.0의 OCR 기본선은 기존 Tesseract/Tess4J로 유지한다. 저장소에는 이미
Tesseract용 4개 시나리오 benchmark와 fixture manifest가 있지만, 그것은 PaddleOCR와
동일한 ground truth·geometry·운영 자원 조건을 비교한 증거가 아니다. 따라서 기존
결과를 PaddleOCR 채택 근거로 재사용하지 않고, 아래 corpus와 protocol을 만족하는
별도 실행 결과가 생길 때만 재평가한다.

| 판단 대상 | 판정 | 의미 |
| --- | --- | --- |
| repo-local synthetic corpus | **ADOPT** | PR에서 해시·ground truth·metric 계약을 결정적으로 검증하는 기본 corpus |
| 공개 문서 corpus | **CONDITIONAL** | 품질 보강용으로만 사용하며 license·notice·재배포 범위를 통과한 fixture만 선택 |
| 실제 PaddleOCR 비교 실행 | **PENDING** | 별도 benchmark 실행에서 수행하고, #545의 service/security 결과와 #546의 API 설계 입력으로 사용 |
| PaddleOCR 0.5.0 기본 provider | **DEFER** | 품질·성능·RSS·공급망·운영 gate를 아직 충족하지 않음 |

DEFER는 PaddleOCR 모델의 품질을 부정하는 결론이 아니다. 현재 저장소에서
재현 가능하고 license가 명확하며 동일 조건으로 Tesseract를 이기는 증거가 아직
없다는 뜻이다.

## 범위와 비범위

### 이번 연구의 범위

- 한국어·영어·일본어가 포함된 printed/mixed 문서 입력
- clean, 저해상도, noise, rotation, 표, 다단 편집, blank, malformed 입력
- OCR text의 CER/WER와 문장·문자 순서, 지원되는 경우 bounding box 정확도
- cold start, model load, warm p50/p95/p99, throughput, RSS/peak memory
- fixture provenance, license, font, generator, hash, 실행 환경, raw artifact 보존
- PR, scheduled CPU, nightly/manual, GPU/manual의 검증 계층 분리

### 이번 연구에서 하지 않는 일

- `paddleocr`, PaddleX, Paddle runtime 또는 model artifact를 Gradle dependency로 추가
- Python embedding, JNI binding, 호출별 CLI subprocess, production HTTP adapter 구현
- 기존 `OcrEngine`/`OcrOptions` 공개 API 변경
- 외부 dataset을 license 확인 없이 repository에 복사하거나 모델을 자동 다운로드
- 현재 benchmark 숫자를 다른 host, engine, model의 순위로 일반화

## 저장소의 현재 근거

### 이미 존재하는 Tesseract benchmark

기존 Issue #203 산출물은 다음 경로에 있다.

- fixture loader: `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/OcrBenchmarkFixtures.kt`
- benchmark: `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/TesseractOcrExtractionBenchmark.kt`
- manifest: `benchmark/images-benchmark/src/main/resources/bench/ocr/manifest.json`
- contract tests: `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/OcrBenchmarkFixturesTest.kt`, `OcrBenchmarkContractTest.kt`
- historical report: `benchmark/images-benchmark/docs/ocr-extraction-benchmark.md`
- historical raw output: `benchmark/images-benchmark/docs/raw/issue-203-20260726-macos-java25/`

현재 구현이 이미 보장하는 것은 다음과 같다.

1. fixture resource가 `bench/ocr/` 아래에 있고 최대 byte 수를 넘지 않는지 확인한다.
2. PNG byte의 SHA-256과 manifest의 width/height를 decode 전에 확인한다.
3. trial setup에서 Tesseract 언어와 tessdata 경로를 검사하여 누락된 언어를 조용히
   건너뛰지 않는다.
4. `clean-text`, `noisy-scan`, `rotated-document`, `multilingual-text`에 대해 직접
   추출과 명시적 전처리 경로를 각각 측정한다.
5. latency와 throughput task를 분리하고 1 thread/1 fork를 사용한다.

이 구조는 새 corpus의 출발점으로 적절하지만, 다음 기능은 아직 없다.

- 전체 ground-truth text 파일과 문자 단위 정규화 계약
- 표·다단 layout의 annotation과 geometry matching
- valid blank와 malformed/truncated 입력의 별도 결과 계약
- Tesseract와 PaddleOCR의 동일 workload·host·resource envelope 비교
- provider별 model identity, container digest, native RSS, cold-start ledger
- 같은 실행 결과를 덮어쓰지 않는 run-id와 artifact hash ledger

### 기존 결과의 publication receipt mismatch

2026-07-26 historical report의 표와 raw artifact 표는 현재 tracked bytes와 일치하지
않는다. Git history를 확인하면 report, fixture, raw artifact는 최초 benchmark
커밋 `1a5dc3774931aaf6b46181b07863437f11e97b54`에서 함께 추가되었다. 이후
fixture와 raw JSON byte는 변경되지 않았고, `d80259332dfef4fe5de2e198ad1e05fe31a1a82a`는
report의 한국어 현지화만 수행했다. 따라서 확인된 사실은 실행 결과가 나중에 변조되었다는
`hash drift`가 아니라, 최초 publication receipt가 실제 파일을 잘못 가리킨
`report-reference mismatch`이다.

| 항목 | 현재 tracked 값 | report에 기록된 값 | 처리 |
| --- | --- | --- | --- |
| `clean-text.png` | `f036a0ec994554fa6c214fe883603bea79c399c934b4674d84f77737ea0322b8` | `eeae6d9dc34fa8281befad9b288196a4fac955ca0b25bda77102b5b1b6079bb0` | 기존 결과 재사용 금지 |
| `ocr-latency.json` | `ad70206ba3dc4b0ec0afb0891b9cb680b73405e6102a481043c3a2ca02aad846` | `9b1a9bcbe0a6543b979eda577d74281ef4ada6e4bcc84d9e4db769c248e01151` | raw ledger 재생성 전 격리 |
| `ocr-throughput.json` | `c2b4bf93a721e5bcbf2a27e73a5e2abed842cde2c99e2ea8a807299d3e0bec88` | `fe3f93b8c53f20d5bd7dff6f43995c3c953901e21cb459da4fe951ba62c44137` | raw ledger 재생성 전 격리 |
| `ocr-gc-clean-text.json` | `134b9222c96f6574a74394242ef6f90191bed7722c1200a44c5fe79a40d3bbef` | `0c644e551d569d29ad4e8df7e0e2c4385caabc15ef0999b6bf6be1f4bd1d3e52` | raw ledger 재생성 전 격리 |

이 불일치는 파일 손상이나 사후 변경을 증명하지 않는다. 다만 report가 가리키는
receipt가 현재 파일을 검증하지 못하므로, 해당 수치를 새 provider의 baseline으로
사용하지 않는다.
이후 실행은 `docs/raw/issue-544-<run-id>/`에 새 결과를 만들고, 입력·환경·raw
JSON을 하나의 SHA-256 ledger로 묶어야 한다.

현재 CI의 OCR smoke는 `.github/workflows/ci.yml`에서 Ubuntu runner와
`-Docr.container.enabled=true`, 20분 timeout, 최대 5회 retry를 사용한다. nightly
경로는 35분 timeout과 별도 retry를 사용한다. 이 경계는 portable smoke에는
유용하지만, apt의 최신 Tesseract/Leptonica와 digest가 고정되지 않은 상태이므로
benchmark 기준선으로는 부족하다.

## Corpus 후보와 선택

| 후보 | 장점 | 위험·비용 | 이번 사용 |
| --- | --- | --- | --- |
| repo-local deterministic synthetic | 생성기·폰트·입력·ground truth를 모두 고정할 수 있고 PR에서 재현 가능 | 실제 스캔 artifact와 layout 다양성이 제한됨 | **기본 채택** |
| FUNSD | 영어 noisy form과 word/box annotation 제공 | 199개 규모, 한국어·일본어 없음; 원저자 저장소에서 명시적 license terms를 확인하지 못함 | license 확인 전 URL·평가 방법만 보존 |
| XFUND | 다국어 form과 layout annotation, 일본어 포함 | 한국어 없음, CC BY-NC-SA 4.0이라 기본 artifact 재배포에 제약 | 일본어 layout 보강만 조건부 |
| ICDAR 2015 SR | 저해상도/scene text 평가 자료 | 문서 OCR corpus가 아니며 ODbL 조건과 평가 목적 불일치 | 기본 corpus에서 제외 |
| 익명화된 내부 corpus | 실제 제품 문서와 가장 가까움 | 개인정보, 보존·접근권한·재배포 경계가 큼 | 외부 공개 artifact로는 거부 |

### 기본 corpus 구성

기본 corpus는 repository가 직접 생성하고 배포하는 deterministic fixture로 구성한다.
각 fixture는 다음 시나리오 class를 갖는다. full acceptance corpus에서는 각 class를
최소 3개 fixture로 확장한다.

- `clean-text`: 높은 대비의 단일 언어 문서
- `low-resolution`: 글자 높이와 입력 byte가 작은 문서
- `noisy-scan`: deterministic noise, blur, ruled artifact가 있는 문서
- `rotated-document`: 90도와 작은 각도 기울기를 분리한 문서
- `table`: 셀 경계와 숫자·문자 혼합이 있는 표
- `multi-column`: 두 개 이상 column의 읽기 순서가 명확한 문서
- `multilingual-text`: `eng`, `kor`, `jpn`이 함께 있는 문서
- `valid-blank`: 유효한 이미지지만 OCR 결과가 비어야 하는 문서
- `malformed-input`: truncated 또는 decode 실패 입력; provider가 명시적 오류를 반환해야 함

PR에는 작은 deterministic subset만 두고, 표·다단·다국어 전체와 반복 실행은
scheduled/nightly artifact로 분리한다. blank와 malformed는 품질 점수에 섞지 않고
결과 분류 계약을 검증하는 negative lane으로 유지한다.

### 생성과 provenance 계약

synthetic fixture를 추가할 때 다음을 함께 고정한다.

1. generator 이름과 정확한 version, CLI/설정 파일, seed
2. 렌더링에 사용한 font 파일의 source URL, license, byte size, SHA-256
3. 원문 text의 NFC 정규화, LF line ending, encoding
4. PNG/JPEG resource의 byte size, width, height, SHA-256
5. 변환 순서와 파라미터(noise seed, rotation, blur, resize, crop)
6. ground-truth text와 geometry JSON의 SHA-256
7. fixture를 repository에 배포할 수 있는 license와 NOTICE 경로

실제 개인정보가 포함된 입력은 기본 corpus에 넣지 않는다. 공개 dataset을 사용하면
resource를 복사하기 전에 해당 release의 license·notice·attribution·재배포 조건을
검토하고, 조건을 만족하지 못하는 dataset은 URL과 평가 방법만 기록한다.

## Manifest v2 제안

현재 manifest v1은 `scenario`, resource, dimensions, image hash, language,
expected token만 표현한다. 비교 benchmark에서는 아래와 같은 v2 형태로 확장한다.

```json
{
  "schemaVersion": 2,
  "hashAlgorithm": "SHA-256",
  "generator": {
    "name": "repo-owned-ocr-fixtures",
    "version": "<pinned>",
    "command": "<exact CLI invocation>",
    "seed": 17,
    "config": {
      "path": "bench/ocr-v2/generator.toml",
      "bytes": 512,
      "sha256": "<64 lowercase hex>",
      "encoding": "UTF-8",
      "normalization": "LF",
      "spdx": "Apache-2.0",
      "noticePath": "docs/licenses/ocr-fixtures.txt"
    }
  },
  "fixtures": [
    {
      "fixtureId": "table-kor-eng-001",
      "scenario": "table",
      "sourceType": "synthetic",
      "resource": {
        "path": "bench/ocr-v2/table-kor-eng-001.png",
        "bytes": 123456,
        "width": 1600,
        "height": 1000,
        "sha256": "<64 lowercase hex>"
      },
      "languages": ["eng", "kor"],
      "transformations": ["grayscale", "deterministic-noise:seed-17"],
      "groundTruth": {
        "text": {
          "path": "bench/ocr-v2/table-kor-eng-001.txt",
          "bytes": 987,
          "sha256": "<64 lowercase hex>",
          "encoding": "UTF-8",
          "normalization": "NFC+LF",
          "whitespacePolicy": "preserve"
        },
        "boxes": {
          "path": "bench/ocr-v2/table-kor-eng-001.boxes.json",
          "bytes": 3456,
          "sha256": "<64 lowercase hex>",
          "schema": "ocr-boxes-v1",
          "schemaResource": {
            "path": "bench/ocr-v2/ocr-boxes-v1.schema.json",
            "bytes": 2048,
            "sha256": "<64 lowercase hex>"
          },
          "coordinateSpace": "pixel",
          "order": "reading-order"
        }
      },
      "licenses": [
        {
          "component": "generator",
          "spdx": "Apache-2.0",
          "sourceUrl": "<pinned source URL>",
          "noticePath": "docs/licenses/ocr-fixtures.txt"
        }
      ],
      "provenance": {
        "font": {
          "name": "<font name>",
          "sourceUrl": "<font source URL>",
          "bytes": 1234567,
          "sha256": "<64 lowercase hex>",
          "spdx": "<font license expression>",
          "noticePath": "docs/licenses/fonts.txt"
        }
      },
      "expectedOutcome": "TEXT"
    }
  ]
}
```

`licenses`는 단일 `licenseSpdx` 문자열이 아니다. 여러 font·dataset·generator가
섞이는 fixture를 표현하려면 각 component의 SPDX expression, 원본 URL, NOTICE를
보존해야 한다. `expectedOutcome`은 `TEXT`, `EMPTY`, `ERROR`를 구분한다. malformed
fixture는 정상 image resource와 같은 entry로 포장하지 말고, 입력 bytes·SHA-256과
기대 reason code를 별도 negative manifest에 둔다.

negative manifest의 각 항목은 `fixtureId`, `path`, `bytes`, `sha256`, `expectedReason`
(`DECODE_FAILED` 또는 고정된 limit reason), `sourceType`만 허용한다. 이 항목에도
absolute path·path traversal·unknown reason 거부를 적용하여, malformed byte를 정상
`TEXT` fixture로 우회할 수 없게 한다.

`ocr-boxes-v1` schema resource는 다음 구조를 고정한다.

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["schema", "coordinateSpace", "entries"],
  "properties": {
    "schema": { "const": "ocr-boxes-v1" },
    "coordinateSpace": { "const": "pixel" },
    "entries": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["boxId", "pageIndex", "text", "x", "y", "width", "height", "order"],
        "properties": {
          "boxId": { "type": "string", "minLength": 1 },
          "pageIndex": { "type": "integer", "minimum": 0 },
          "text": { "type": "string" },
          "x": { "type": "integer", "minimum": 0 },
          "y": { "type": "integer", "minimum": 0 },
          "width": { "type": "integer", "minimum": 1 },
          "height": { "type": "integer", "minimum": 1 },
          "order": { "type": "integer", "minimum": 0 }
        }
      }
    }
  }
}
```

`schema`, `coordinateSpace`, `entries`와 entry의 `boxId`, `pageIndex`, `text`, `x`,
`y`, `width`, `height`, `order`는 필수다. `pageIndex`·`x`·`y`·`order`는 0 이상인
정수이고, `width`·`height`는 양수다. `entries` 배열 안에서 `order`와 `boxId`는
각각 유일해야 한다. 알 수 없는 필드는 거부한다. schema resource 자체도 manifest의
path/bytes/SHA-256으로 검증하며, schema hash가 바뀌면 새 manifest version과 새 run-id를
사용한다.

manifest loader는 다음을 fail-closed로 검사해야 한다.

- schema version과 hash algorithm을 허용 목록으로 제한
- resource path의 `..`, absolute path, backslash를 거부
- manifest가 선언한 path를 한 번 연 `InputStream`/file descriptor에서 bytes·size·hash를
  함께 계산하고, 검증한 동일 byte sequence를 decoder에 전달한다. 검증 뒤 path를 다시
  열어 다른 bytes를 읽는 구현은 허용하지 않는다.
- text/box resource도 동일한 방식으로 hash·size·encoding·정규화·whitespace·geometry
  order 규칙을 검증하며, generator `config`와 font source도 bytes·hash·license receipt가
  없으면 거부한다.
- 존재하지 않는 language, license, ground-truth resource를 성공으로 취급하지 않음
- unknown `expectedOutcome`과 malformed geometry schema를 거부
- JSON Schema로 표현하기 어려운 semantic 조건도 검사한다. `boxId`와 `order`의
  cross-entry uniqueness, `x + width <= resource.width`, `y + height <= resource.height`,
  `0 <= pageIndex < declared page count`를 위반하면 loader가 fail-closed한다.

## 품질 metric 계약

### Text normalization

비교 전에 입력과 결과를 다음 순서로 정규화한다.

1. UTF-8 decode 실패는 `ERROR`이며 대체 문자로 바꾸지 않는다.
2. Unicode NFC를 적용한다.
3. `CRLF`와 `CR`을 `LF`로 바꾼다.
4. 줄 끝 공백과 다중 공백은 fixture 정책에 따라 보존하거나 하나로 줄인다.
   정책은 fixture별로 manifest에 기록하고 실행 중 임의로 바꾸지 않는다.
5. 비교용 text와 사람이 읽는 raw provider output을 모두 artifact에 보존하되,
   원문 이미지나 민감한 OCR text는 CI log에 출력하지 않는다.

### CER/WER

- CER는 Unicode grapheme cluster sequence의 edit distance를 정답 길이로 나눈다.
- WER는 공백으로 안정적으로 tokenise할 수 있는 영어·숫자 fixture에만 적용한다.
- 한국어·일본어처럼 공백이 의미 단위와 일치하지 않는 입력에는 WER를 강제로
  계산하지 않고 `N/A`로 표시한다.
- 빈 정답과 빈 결과는 CER 0으로 흡수하지 않는다. `valid-blank`는 별도의 expected
  outcome으로 100% 일치해야 한다.
- provider가 일부 page만 반환하면 전체 결과를 조용히 성공으로 만들지 않고
  `PARTIAL` 또는 `ERROR`로 분류한다.

### Geometry

geometry를 제공하는 provider에 한해 one-to-one matching을 수행한다.

- 예측 box와 정답 box를 pixel coordinate로 변환한 뒤 `IoU >= 0.50`을 유효 match로
  고정한다. 이 threshold는 v2 manifest와 결과 보고서에 반복해서 기록한다.
- 유효 match는 Hungarian algorithm으로 IoU 합을 최대로 하는 one-to-one assignment를
  구한다. 동률이면 정답 box index, 예측 box index의 오름차순을 차례로 적용하는
  deterministic tie-break를 사용한다. threshold 미만 assignment는 unmatched로 둔다.
- precision, recall, F1을 계산하고 지원하지 않는 provider는 `N/A`로 표시한다.
- text와 box의 순서가 다르면 text score와 geometry score를 분리해 보고한다.

### Empty와 error

`valid-blank`, `malformed-input`, timeout, provider unavailable을 하나의 “empty”로
합치지 않는다.

| 입력/결과 | 기대 분류 |
| --- | --- |
| 유효한 blank image, 빈 OCR text | `EMPTY` |
| 잘린 이미지 또는 decode 실패 | `ERROR/DECODE_FAILED` |
| model/service가 제한 시간 초과 | `ERROR/TIMEOUT` |
| schema가 맞지 않는 provider 응답 | `ERROR/INVALID_RESPONSE` |
| 일부 page만 처리 | `PARTIAL` |

각 분류는 stable reason code를 보존하고 raw native exception, 경로, credential은
외부 결과와 CI log에 노출하지 않는다.

## 성능·자원 metric 계약

성능은 quality gate를 통과한 결과끼리만 비교한다. 낮은 CER를 얻기 위해 입력을
누락하거나 retry를 숨기는 측정은 유효하지 않다.

| metric | 측정 경계 | 보고 방법 |
| --- | --- | --- |
| cold start | 새 process/container 시작부터 model-ready | median, p95, run별 raw value |
| model load | model bytes read부터 ready | cold와 warm을 분리 |
| warm latency | ready 상태에서 request 시작부터 structured result | p50/p95/p99, scenario별 |
| throughput | 고정 concurrency와 payload에서 완료 requests/s | queue wait와 service time을 별도 기록 |
| RSS/peak memory | JVM heap + native/service process | JVM은 `-XX:NativeMemoryTracking=summary`와 `jcmd`, service는 `/usr/bin/time -v`; sampling interval 100ms와 tool version 고정 |
| model/cache size | model 및 runtime cache의 bytes | image/model manifest와 함께 hash |
| cancellation/timeout | deadline 초과 후 반환·정리 시간 | timeout reason과 cleanup 상태 |

입력 read/decode, network serialization, OCR engine 시간을 섞지 않는다. 각각을
포함한 end-to-end 수치와 provider-only 수치를 별도 열로 보존한다. 단일 host의
absolute rank를 다른 OS/CPU로 일반화하지 않으며, host/architecture가 바뀌면 새
run-id를 만든다.

## 실행 protocol

### 고정할 환경

각 run manifest에 다음을 기록한다.

- OS, kernel, architecture, CPU model/count, physical memory
- JVM distribution/version, Gradle version, locale, timezone
- Tesseract binary version, Tess4J version, traineddata 파일별 hash, OEM/PSM, DPI,
  language order, preprocessing chain, per-request timeout, 그리고 `OcrOptions`의
  `variables`(정렬된 key/value), `configs`(순서 보존), `trimText`, `structuredDetail`,
  `regions`(pixel box와 id)
- Paddle service image digest, Python/Paddle/PaddleX/inference engine version, pipeline
  name, model id, device(`cpu`), thread count, batch size, preprocessing options,
  request/read timeout
- detector/recognizer/orientation model id, source, byte size, SHA-256, license
- font inventory와 fixture manifest SHA-256
- benchmark commit SHA와 clean/dirty 상태
- network policy, cache root, container resource limit

model은 first-use network download가 아니라 사전에 준비된 local artifact 또는
digest-pinned image에서 읽는다. run 중 network egress가 관찰되면 해당 결과는
성능 evidence가 아니라 재현성 실패로 분류한다.

### 입력·실행 limit profile

benchmark 비교는 provider가 서로 다른 기본값을 사용하지 않도록 다음 profile을
공통으로 적용한다. 값은 기존 image-intelligence 예제와 `ImageDecodeLimits`의 방어
기본값에서 가져온 측정 profile이며 production SLO를 의미하지 않는다.

| 축 | 공통 profile v1 | 초과 시 판정 |
| --- | --- | --- |
| encoded image bytes | `5 MiB` payload (multipart envelope는 별도 `6 MiB` transport cap) | `INPUT_TOO_LARGE` 전에 decode/engine 호출 금지 |
| pages | 단일 이미지 corpus는 `maxPages=1`; TIFF 후속 lane은 기존 `maxPages=16`을 별도 보고 | `PAGE_LIMIT_EXCEEDED` |
| decoded dimensions | `maxPixels=16_777_216`, `maxSide=8_192` | `PIXELS_PER_PAGE_LIMIT_EXCEEDED` 또는 `SIDE_LIMIT_EXCEEDED` |
| OCR deadline | request 시작부터 결과까지 `3 s`, provider timeout과 client deadline을 각각 기록 | `TIMEOUT`, retry로 성공 대체 금지 |
| concurrency | baseline `1`, bounded stress matrix `{1, 2, 4}`; queue wait와 service time 분리 | `CONCURRENCY_LIMIT_EXCEEDED` |

각 provider가 이 profile을 적용할 수 없으면 해당 row는 성공 수치가 아니라
`LIMIT_PROFILE_MISMATCH`로 기록하고 gate에서 제외한다. image/page/pixel/time/concurrency
초과 fixture는 정상 quality 평균에 섞지 않고 negative/error lane으로 유지한다.

### 반복·순서·통계

- in-process warm latency는 JMH `SampleTime`을 사용한다. warmup 3회×1초,
  measurement 5회×1초, `1 thread/1 fork`를 고정하고 raw samples에서 p50/p95/p99를
  산출한다. 기존 #203의 `avgt` 결과는 평균값만 제공하므로 percentile 근거로
  재사용하지 않는다.
- in-process cold/model-load는 JMH `SingleShotTime`으로 3 fork에서 fork당 10회
  invocation을 기록한다. 각 invocation은 provider/model lifecycle을 새로 만들고
  model-ready까지 읽어야 하며, lifecycle을 재사용한 값은 warm으로만 분류한다. JVM
  fork 재사용과 첫 invocation 비용을 별도 열로 보존하며, process 경계를 새로 여는
  cold-start 수치는 별도 harness에서 측정한다.
- HTTP service cold run은 매회 새 process/container를 시작하고 model-ready 신호를
  받은 뒤에만 요청한다. 독립 run마다 정확히 10회 launch(총 3 run이면 30회)를 수행하고,
  성공·실패·timeout attempt를 모두 raw ledger에 남긴다. launch부터 ready까지의 raw
  값을 p50/p95로 보고하고, warm 요청 latency와 섞지 않으며 retry로 표본을 대체하지
  않는다.
- 서로 다른 provider를 비교할 때 최소 3개의 독립 run을 수행한다.
- 기본 CPU baseline은 1 thread/1 fork로 시작하고, bounded concurrency 실험은 별도
  matrix로 둔다.
- provider 실행 순서는 번갈아 바꾸거나 random seed를 기록하여 host thermal/cache
  순서 편향을 줄인다.
- p50/p95/p99와 run 간 분산 및 실패 수를 숨기지 않는다. percentile을 계산할 수
  없는 측정은 `N/A`가 아니라 protocol 위반으로 판정한다.
- raw JSON과 summary를 수정하거나 덮어쓰지 않고 `issue-544-<run-id>` 디렉터리에
  보존한다.

### 결과 artifact contract

각 `issue-544-<run-id>/`에는 다음 파일을 만들고, 파일 목록 자체를
`ledger.sha256`에 기록한다.

| 파일 | 필수 필드와 규칙 |
| --- | --- |
| `run-manifest.json` | `schemaVersion`, `runId`, `provider`, `fixtureManifestSha256`, `inputHash`, `configHash`, `modelHash`, `environmentHash`, `limitProfile`, `processBoundary` |
| `raw/<fixture-id>/<attempt-id>.json` | attempt당 정확히 한 row: `fixtureId`, `provider`, `attemptId`, `status`, `reasonCode`, `startedAt`, `finishedAt`, `latencyMs`, `rssBytes`, `normalizedTextPath`, `normalizedTextSha256`, `rawTextPath`, `rawTextSha256`, `predictedGeometryPath`, `predictedGeometrySha256`, geometry metrics; 민감한 원문은 CI log에 쓰지 않음 |
| `summary.json` | scenario·language별 CER/WER/geometry, warm p50/p95/p99, cold p50/p95, throughput, RSS peak, 실패 수, gate 판정과 제외 row |
| `failures.jsonl` | 실패·timeout·limit mismatch의 `attemptId`, stable reason, exit code, cleanup 상태; raw native/path/credential는 제외 |
| `payload/<fixture-id>/<attempt-id>.normalized.txt` 및 `.raw.txt` | 비교용 normalized text와 provider raw output. synthetic corpus에서는 평문을 허용하고, 민감 corpus에서는 암호화·restricted artifact로 보존하며 row의 path/hash가 동일 payload를 가리켜야 함 |
| `payload/<fixture-id>/<attempt-id>.geometry.json` | provider predicted boxes의 원본 JSON; row의 path/hash와 `ocr-boxes-v1` semantic validation을 적용 |
| `ledger.sha256` | 위 파일과 모든 attempt/payload의 상대경로, byte 수, lowercase SHA-256; 생성 후 덮어쓰기 금지 |

Tesseract process-cold harness도 독립 run당 정확히 10회 process launch(총 3 run이면
30회)를 수행한다. 각 attempt는 성공 여부와 무관하게 `attemptId`, start/ready/end
timestamp, exit code, timeout, reason을 기록하며, 실패 attempt를 retry 성공값으로
대체하지 않는다. 10회 미만이면 해당 cold metric은 `PENDING`이고 gate를 통과하지
못한다. HTTP service cold와 동일한 artifact schema를 사용하되 `processBoundary`를
`tesseract-jvm` 또는 `paddle-http`로 구분한다.

기존 #203의 3 warmup/5 measurement/1 thread/1 fork는 latency/throughput의
출발점으로 재사용할 수 있지만, 새 비교에서는 위 환경 manifest와 provider별
model identity를 추가해야 한다.

## provisional 채택 gate

아래 수치는 production SLO가 아니라 “더 조사할 가치가 있는지”를 판정하는
provisional gate다. 모든 수치는 동일 workload·corpus·host·resource envelope에서
계산하며, in-process Tesseract와 별도 HTTP process인 Paddle의 경계 차이는 별도
열로 보고한다. 서로 다른 engine을 같은 process 조건이라고 표현하지 않는다.

### 품질 gate

- full acceptance corpus는 아홉 개 scenario class마다 최소 3개 fixture(최소 27개)를
  갖는다. ground-truth에 해당 언어의 grapheme cluster가 20개 이상인 fixture만 언어
  coverage로 세며, `eng`, `kor`, `jpn` 각각 최소 9개 fixture를 포함한다. 각 언어의
  coverage에는 single-language fixture 최소 3개와 mixed-language fixture 최소 3개가
  포함되어야 하고, nonblank text fixture 하나에는 grapheme cluster가 최소 80개 있어야
  한다. table과 multi-column은 서로 다른 layout을 최소 3개씩 포함한다.
- 각 fixture의 CER를 동일 가중치로 평균한 macro CER를 사용한다. `EMPTY`, `ERROR`,
  `PARTIAL` 결과는 CER 분모에서 제외하고 별도 분류 gate로 평가한다.
- macro CER 상대 개선률은 `(CER_baseline - CER_candidate) / max(CER_baseline, 1e-9)`로
  계산하며, baseline이 0이면 candidate도 0이어야 하고 상대 개선률은 `N/A`로 둔다.
  required corpus 전체에서 상대 10% 이상 개선해야 한다.
- 필수 언어 또는 scenario의 candidate CER는 `candidate <= baseline + 0.05`를 만족해야
  한다. `0.05`는 CER 절대 5 percentage points이며 상대 5%가 아니다.
- geometry를 지원하는 경우 macro F1이 최소 `+0.05` 개선되거나, 개선이 없는
  이유와 구조적 이점을 별도로 설명해야 함
- valid blank/error/partial 분류가 expected outcome과 100% 일치
- malformed 및 limit 초과 입력에서 성공으로 위장하지 않음

### 성능·자원 gate

- quality gate를 통과한 동일 scenario의 warm p95가 Tesseract의 1.5배를 넘지 않음
  (품질 개선이 큰 경우 초과 사유와 운영 trade-off를 별도 승인)
- cold start와 model load를 별도 보고하고, startup budget을 넘는 경우 기본
  request path에 넣지 않음
- peak RSS와 native memory가 측정되어야 하며, 최소한 baseline 대비 2배 이내를
  목표로 한다. 관찰 도구가 native memory를 포함하지 못하면 gate 미충족이다.
- timeout, cancellation, bounded concurrency, retry budget이 결정적으로 동작해야 함
- 3개 독립 run에서 required row, 입력/config/model/환경 hash가 모두 동일해야 한다.
  metric 값은 동일할 필요가 없으며, warm p95와 warm RSS의 relative MAD가 10% 이하,
  cold-start의 relative MAD가 20% 이하라는 기본 허용 오차를 적용한다. 초과하면
  host noise를 기록하고 재실행하거나 해당 결과를 재현성 실패로 판정한다. 여기서
  relative MAD는 `median(abs(x - median(x))) / median(x)`로 계산하고, median이 0이면
  metric별 절대 허용 오차를 manifest에 명시한다.

하나라도 충족하지 못하거나 결과를 재현할 수 없으면 판정은 `DEFER`다. 숫자를
완화하여 채택하는 대신 corpus·환경·model 변경 여부를 먼저 검토한다.

## CI 계층

| 계층 | 검증 범위 | 외부 model/network |
| --- | --- | --- |
| PR required | manifest/hash/path, ground-truth schema, normalization·metric engine, fake provider contract, no-network assertion | 금지 |
| scheduled CPU | digest-pinned Tesseract와 Paddle tiny/small service smoke, 대표 subset, limit/error contract | pre-baked local artifact만 허용 |
| nightly/manual | 전체 multilingual/table/multi-column, 3회 반복, p95/RSS/cold-start raw artifact | 고정 image/model만 허용 |
| GPU/manual | CUDA/provider별 성능과 품질 | self-hosted hardware, required CI 아님 |

현재 `.github/workflows/ci.yml`의 Ubuntu OCR job은 portable container smoke이며,
apt 최신 package와 retry를 사용한다. benchmark의 reproducibility 기준으로 승격하려면
Tesseract/Leptonica image digest와 traineddata hash를 먼저 고정해야 한다. Paddle
full model을 PR required 경로에 넣으면 모델 다운로드와 native startup이 flaky
failure를 일으킬 수 있으므로, #545의 service/security issue와 함께 scheduled
경로로 제한한다.

## 장단점과 대안

| 대안 | 장점 | 단점·위험 | 결론 |
| --- | --- | --- | --- |
| Tesseract baseline만 유지 | 현재 API·native gate·CI가 이미 있고 비용이 낮음 | layout·일부 언어 품질 개선을 탐색하지 못함 | 단기 기본선 |
| synthetic만 사용 | hash, font, ground truth, run을 완전히 재현 가능 | 실제 scan artifact와 layout 편향 | PR 기본 corpus |
| public dataset만 사용 | 실제 문서 noise와 annotation 다양성 | license·notice·재배포·PII 위험 | scheduled 보강만 |
| Paddle self-hosted HTTP | model lifecycle과 JVM boundary를 분리하고 warm model 사용 | service 운영, auth/TLS, payload·timeout, version skew | 조건부 후보 |
| Python in-process | 호출 경계가 짧아 보임 | Python ABI, native allocator, GIL, process-wide cache가 JVM lifecycle과 결합 | 거부 |
| JNI 또는 요청별 CLI | 격리 또는 직접 호출 가능 | ABI/cold-start/model reload/cleanup과 tail latency 위험 | 거부 |
| hosted Paddle API | 시작 비용이 작음 | 이미지 egress, token/quota, data residency, 재현성 문제 | 기본 거부 |
| Paddle-to-ONNX 변환 | JVM direct provider 가능성 | op 변환 fidelity와 preprocessing/postprocessing drift를 별도 증명 | 별도 연구 |

repo-local synthetic과 license가 승인된 public supplemental corpus를 함께 사용하되,
두 결과를 하나의 score로 섞지 않는다. synthetic은 regression·재현성, public corpus는
외부 realism을 검증하는 서로 다른 역할이다.

## 위험과 완화책

| 위험 | 완화 |
| --- | --- |
| font version/OS rasterizer drift | font file hash와 generator version 고정, image 생성은 pinned tool에서 수행 |
| OCR model 또는 traineddata drift | model/traineddata SHA-256과 container digest를 run manifest에 기록 |
| CJK WER tokenization 편향 | CJK WER를 `N/A`로 두고 grapheme CER·geometry를 사용 |
| 표·다단 reading order bias | ground-truth boxes와 order annotation을 별도로 보존 |
| native RSS가 JVM metric에서 누락 | service/native process RSS 측정 도구와 sampling contract를 고정 |
| thermal/cache/host noise | warm/cold 분리, 3회 독립 run, run 순서 교대, host metadata 기록 |
| 공개 dataset license 위반 | source release의 license·notice·재배포 조건을 fixture별로 기록하고 불충족 시 URL만 보존 |
| stale raw artifact가 새 결과로 오인됨 | immutable run-id 디렉터리, 입력·환경·summary의 SHA ledger, 덮어쓰기 금지 |
| 모델 자동 다운로드와 egress | pre-baked image/local cache, offline source probe disable, network deny assertion |
| timeout/retry가 실패를 숨김 | stable reason code, attempt 수, cleanup 결과, partial/error를 raw 결과에 기록 |

## 후속 train과 의존성

이 문서는 #169의 품질·재현성 조사 결과이며, #3 이미지 분류 연구의 compile
dependency가 아니다. 두 train이 공유해야 하는 정책은 model provenance/license,
checksum, offline cache, native runtime CI tier뿐이다.

권장 순서는 다음과 같다.

1. #543 common supply-chain policy가 license 표현, checksum, cache, offline, CI
   정책을 확정한다.
2. #544가 고정한 corpus·metric·run manifest를 기준으로 #545가 Paddle HTTP
   service의 security/operability 계약을 연구한다.
3. 별도 benchmark 실행 issue가 production API와 독립적으로 Tesseract/Paddle 후보를
   같은 workload·corpus·host·resource envelope에서 실행하고, process/provider 경계와
   함께 모든 provisional gate의 raw artifact를 생성한다.
4. #546이 benchmark 결과와 #545의 service 경계를 입력으로 provider-neutral OCR API와
   Tesseract/Paddle boundary를 설계한다. API 설계가 benchmark 실행의 선행 조건은 아니다.
5. gate를 통과하지 못하면 production provider를 추가하지 않고 `DEFER`를 유지한다.

## 재평가 전 필수 조건

- [ ] repo-local corpus 생성기, font, config, text, boxes의 version/hash 고정
- [x] manifest v2와 representative negative fixture의 schema/loader contract 구현
      (9개 시나리오·최소 27개 fixture 확장은 아직 PENDING)
- [ ] historical #203 report의 publication receipt mismatch를 정정하거나
      quarantine한 새 baseline report 생성
- [ ] Tesseract binary/traineddata와 Paddle service/model/container digest 고정
- [ ] 동일 host에서 최소 3회 독립 run, warm p50/p95/p99, cold/model load, RSS 측정
- [ ] CER/WER/geometry/empty/error/partial 결과와 raw JSON/summary ledger 보존
- [ ] PR no-network contract, scheduled CPU smoke, nightly/manual full matrix 통과
- [ ] #545 service security와 #546 provider-neutral API 설계 승인
- [ ] quality gate와 performance gate를 동시에 만족하거나, 초과 사유를 별도
      architecture decision으로 승인

## 조사 근거와 source-to-claim ledger

### 저장소 근거

| 주장 | 근거 |
| --- | --- |
| 기존 fixture loader가 path/size/hash/dimensions를 검사 | `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/OcrBenchmarkFixtures.kt` |
| 현재 scenario와 Tesseract trial 구조 | `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/TesseractOcrExtractionBenchmark.kt` |
| manifest v1의 제한된 schema | `benchmark/images-benchmark/src/main/resources/bench/ocr/manifest.json` |
| latency/throughput task의 warmup·iteration·fork | `benchmark/images-benchmark/build.gradle.kts`의 `ocrLatency`, `ocrThroughput` |
| 현재 OCR CI의 timeout/retry/container 경계 | `.github/workflows/ci.yml`, `.github/workflows/nightly-tests.yml` |
| historical report와 raw artifact publication receipt mismatch | `benchmark/images-benchmark/docs/ocr-extraction-benchmark.md`, `docs/raw/issue-203-20260726-macos-java25/`, git history `1a5dc377` |
| Paddle runtime이 Tesseract-neutral API가 아님 | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt`, `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt`, `images-ocr/build.gradle.kts` |
| Paddle backend 평가와 service 방향 | `docs/superpowers/research/2026-08-18-issue-169-paddleocr-backend-evaluation.md` |

### 공식·외부 근거

모든 외부 페이지는 2026-08-19에 확인했다.

| 주장 또는 설계 입력 | 공식·원출처 | 이 문서에서의 사용 |
| --- | --- | --- |
| PaddleOCR v3.7.0 release와 dependency 범위 | [release](https://github.com/PaddlePaddle/PaddleOCR/releases/tag/v3.7.0), [pyproject](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.7.0/pyproject.toml), [installation](https://www.paddleocr.ai/main/en/version3.x/installation.html) | Python/PaddleX runtime을 JVM dependency로 넣지 않는 근거 |
| PaddleX serving 경계와 기본 배포 동작 | [serving](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/serving/serving.html) | HTTP service를 별도 process로 격리하고 auth/TLS를 perimeter에서 설계하는 근거 |
| CPU/GPU matrix와 runtime cache | [high-performance inference](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/local_inference/high_performance_inference.html), [PaddleX FAQ](https://paddlepaddle.github.io/PaddleX/3.7/FAQ.html) | device/thread/cache를 run manifest에 고정하고 offline artifact를 요구하는 근거 |
| OCR pipeline과 model size | [OCR pipeline](https://www.paddleocr.ai/main/en/version3.x/pipeline_usage/OCR.html) | tiny/small/medium model을 별도 CI tier로 나누는 근거 |
| model source와 connectivity probe | [model source update](https://www.paddleocr.ai/latest/en/update/update.html) | first-use download와 egress를 금지하고 source/hash를 고정하는 근거 |
| PaddleOCR 및 PP-OCRv6 license | [PaddleOCR license](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.7.0/LICENSE), [detector card](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_det_safetensors), [recognizer card](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_safetensors) | 모델별 SPDX·NOTICE·SHA-256 receipt를 요구하는 근거 |
| 영어 form/box annotation | [FUNSD original-author repository](https://github.com/guillaumejaume/FUNSD) | 공개 supplemental corpus의 provenance·geometry 확인; license terms는 미확정 |
| 다국어 form annotation과 평가 한계 | [XFUND repository](https://github.com/doc-analysis/XFUND), [XFUND paper](https://aclanthology.org/2022.findings-acl.253) | 일본어 layout 보강을 조건부로 두고 한국어 부재를 명시하는 근거 |
| ICDAR 2015 SR 범위와 terms | [ICDAR 2015 SR terms](https://projet.liris.cnrs.fr/sr2015/index.php?p=3) | 문서 OCR 목적과 달라 기본 corpus에서 제외하는 근거 |
| Tesseract release·설치·traineddata | [release history](https://github.com/tesseract-ocr/tesseract/releases), [installation](https://tesseract-ocr.github.io/tessdoc/Installation.html), [data files](https://tesseract-ocr.github.io/tessdoc/Data-Files.html) | Tesseract binary/traineddata version·hash를 고정하는 근거 |
| Tesseract benchmark 측정 주의사항 | [benchmark guidance](https://tesseract-ocr.github.io/tessdoc/Benchmarks.html) | host, preprocessing, model 차이를 숨기지 않는 benchmark 경계 |
| JMH benchmark mode semantics | [OpenJDK `JMHSample_02_BenchmarkModes`](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_02_BenchmarkModes.java) | `SampleTime` percentile과 `SingleShotTime` cold protocol을 고정하는 근거 |

## 2026-08-24 구현 slice

이번 slice는 비교 실행의 선행 조건인 manifest v2 계약을 benchmark 모듈에
구현했다. `OcrBenchmarkCorpusV2`는 기존 `immutableImageOf`와 benchmark fixture
경계를 재사용하면서 다음 입력을 하나의 receipt로 검증한다.

- generator config의 bytes·SHA-256·encoding·정규화·license receipt와 historical
  ImageMagick provenance; replay가 검증되지 않은 경우 `replayStatus=PENDING`
- image의 bytes·dimensions·SHA-256과 단일 read 결과의 decoder 전달
- NFC+LF ground-truth text와 `TEXT`/`EMPTY`/`ERROR` outcome
- `ocr-boxes-v1` schema·boxId/order uniqueness·single-page pixel bounds
- malformed input의 별도 `DECODE_FAILED` negative manifest

대표 fixture는 기존 `clean-text.png`를 재사용하고, v2 전용 text·boxes·schema·
generator receipt를 `bench/ocr-v2/`에 고정한다. 기존 v1 manifest의
`ImageMagick 7.1.2-27` historical provenance를 receipt로 정정하며, 저장소에
재현 generator가 없으므로 replay 상태는 `PENDING`이다. `OcrBenchmarkCorpusV2Test`는
정상 receipt, malformed negative와 실제 decode 실패, path traversal, wrong hash,
unknown outcome, contiguous geometry order와 ground-truth text 일치를 검증한다. 이 slice는 Tesseract/PaddleOCR 실행, 9개
시나리오의 27개 corpus 확장, CER/WER·latency·RSS 결과를 완료했다고 주장하지
않는다. 그 항목은 별도 scheduled/nightly benchmark train의 PENDING gate다.

## Issue #544 완료조건 매핑

| 완료조건 | 상태 | 근거 |
| --- | --- | --- |
| corpus provenance·license·hash와 정답 라벨 고정 | **HARNESS PARTIAL** | manifest v2 loader와 대표 synthetic fixture의 receipt·ground truth; 9개 시나리오 전체 확장은 PENDING |
| 최소 3회 반복·warm-up·허용 오차·artifact 형식 문서화 | **SPEC COMPLETE** | 실행 protocol과 warm/cold relative MAD 허용 오차 |
| Tesseract 대비 품질·지연·RSS 개선과 실패 사례 기록 | **PENDING** | 실제 PaddleOCR model/service 실행과 raw result가 아직 없음 |
| 수용 가능한 결과가 없으면 DEFER 유지 | **DECIDED** | quality/performance/reproducibility gate 중 하나라도 미충족하면 DEFER |

이 매핑 때문에 이 문서의 연구 산출물은 준비되었지만, live Issue #544 자체는
비교 실행과 실패 사례가 추가될 때까지 닫지 않는다.

## 문서 DoD

- [x] Issue #544, parent #169, Epic #513의 live 범위와 비범위를 반영
- [x] 기존 Tesseract benchmark 구조와 CI 경계를 source path로 연결
- [x] 기존 report/raw/fixture publication receipt mismatch를 git history와 함께 기록
- [x] 가능성, 위험성, 장점, 단점, 대안을 비교
- [x] corpus 후보와 선택, manifest, ground truth, metric, 성능 protocol을 고정
- [x] PR/scheduled/nightly/GPU CI 계층과 no-network 정책을 분리
- [x] corpus 대표성, CER 집계·산식, 반복 metric 허용 오차, DEFER 조건을 명시
- [x] 공식 URL과 저장소 source-to-claim ledger를 제공
- [x] dependency/model/production code mutation 없음
- [x] manifest v2 loader와 대표 `DECODE_FAILED` negative fixture의 계약 테스트
- [ ] 9개 시나리오·최소 27개 fixture와 세 언어별 floor 확장
- [ ] 실제 Tesseract/Paddle 동일 corpus benchmark 실행
- [ ] 선택 model/container digest와 SBOM/NOTICE receipt 생성
- [ ] #545 service/security와 #546 provider-neutral API 설계 승인

**최종 상태: RESEARCH-1 CONTRACT SPECIFICATION / v2 harness PARTIAL / 전체 corpus·비교 실행 PENDING / PaddleOCR provider DEFER**
