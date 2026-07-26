# Algorithmic Hot Paths Benchmark (Issue #207)

`ImageAlgorithmBenchmark` covers utility APIs that do not appear in the core
resize/encode tables. It uses the checked-in natural photo and document
fixtures, plus a small deterministic SVG string.

## Command

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkAlgorithmicHotPathsBenchmark
```

## Production API map

| Benchmark | Production API | Fixture and parameters |
|-----------|----------------|------------------------|
| `crop` | `ImmutableImage.subimage` | top-left `min(1024,w) x min(768,h)` |
| `tileSplit` | `TileProcessor.split` | `TileSize(512,512)`, max 256 tiles |
| `dominantColors` | `ImmutableImage.dominantColors` | five median-cut colors |
| `histogramSimilarity` | `histogramSimilarityTo` | source fixture against a 512x512 comparison, default metric |
| `phashDistance` | `phashDistanceTo` | source fixture against a 512x512 comparison |
| `svgRasterize` | `BatikSvgRasterizer.rasterize` | 1024x1024 SVG, 512x512 output |

The suite reports `AverageTime ms/op`; lower is better. Crop, tiling, color
analysis, and similarity are CPU/allocation-sensitive. SVG includes the
coroutine bridge required by the suspend-facing rasterizer API. These are
focused local measurements, not cross-host production rankings.

![Algorithmic hot paths benchmark chart](../../../docs/images/readme-charts/images-benchmark-algorithmic-hot-paths-chart-01.png)

Raw Java 25/macOS output: [`algorithmic-hot-paths.json`](raw/issue-207-20260726-macos-java25/algorithmic-hot-paths.json).
