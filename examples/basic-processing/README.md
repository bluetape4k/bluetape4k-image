# Basic Image Processing Quickstart

English | [한국어](./README.ko.md)

Small runnable example for pure JVM image processing with `bluetape4k-images`.
It uses the natural photo fixtures `cafe.jpg` and `landscape.jpg`, plus the root
README representative image `docs/images/image-workbench.png`, and writes all
generated files under `build/tmp/basic-processing` by default.

## What It Shows

- Load images from file-backed resources without copying the whole compressed file into your own `ByteArray`
- Create a bounded thumbnail
- Smart-crop a landscape photo to an exact 16:9 output
- Convert JPEG input to PNG output
- Add a simple text watermark
- Reuse the root README representative image as a 16:9 preview output
- Write encoded images through suspend-aware `bluetape4k-images` writers

## Diagrams

### Example Scenario

![Basic Processing Scenario](../../docs/images/readme-diagrams/examples-basic-processing-scenario-01.png)

### Architecture

![Basic Processing Architecture](../../docs/images/readme-diagrams/examples-basic-processing-architecture-01.png)

### Sequence

![Basic Processing Sequence](../../docs/images/readme-diagrams/examples-basic-processing-sequence-01.png)

## Run

```bash
./gradlew :basic-processing:run
```

Use a custom output directory:

```bash
./gradlew :basic-processing:run --args="/tmp/bluetape4k-basic-processing"
```

Expected outputs:

| File | Source | Operation | Size |
| --- | --- | --- | --- |
| `01-cafe-thumbnail.jpg` | `cafe.jpg` | fit thumbnail | `320x240` |
| `02-landscape-smart-crop.jpg` | `landscape.jpg` | saliency smart crop | `640x360` |
| `03-cafe-converted.png` | `cafe.jpg` | JPEG to PNG conversion | `800x600` |
| `04-landscape-watermarked.jpg` | `landscape.jpg` | fit and text watermark | `960x540` |
| `05-readme-workbench-preview.jpg` | `image-workbench.png` | root README visual preview | `960x540` |

## Smoke Test

```bash
./gradlew :basic-processing:test
```

The test calls the same generator used by the `run` task and verifies that every
output file exists, is decodable, and has the expected dimensions.
