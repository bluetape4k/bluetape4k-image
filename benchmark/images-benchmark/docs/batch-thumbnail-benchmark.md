# Batch and Thumbnail Benchmark (Issue #206)

`ImageBatchBenchmark` measures multi-input thumbnail fan-out and chained
thumbnail-to-JPEG work. The fixture count is parameterized so scaling can be
observed without committing generated outputs.

## Commands

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkBatchPipelineBenchmark
```

Filter generated JMH output to `ImageBatchBenchmark` when running the full
target. The class uses one, four, and eight natural-photo fixtures, three
thumbnail sizes (`320`, `640`, `1280`), and a `640x480` JPEG output path.

## Workload map

| Method | Boundary | Concurrency | Backend |
|--------|----------|-------------|---------|
| `scrimage_thumbnailFanout` | resize only, three outputs/input | sequential | Scrimage |
| `scrimage_batchSequential` | resize + JPEG encode | sequential | Scrimage |
| `scrimage_batchBoundedConcurrency` | resize + JPEG encode | coroutine dispatcher limited to 2 | Scrimage |
| `vips_thumbnailFanout` | thumbnail only, three outputs/input | sequential | libvips |

The Scrimage and libvips rows are intentionally not a single ranking: the
Scrimage chained rows include JPEG encoding while the libvips row isolates
thumbnail transforms. Compare only equivalent rows or use the table to select
the pipeline shape that matches the application boundary.

## Metrics and limits

JMH reports `AverageTime ms/op`; lower is better. Fixture count and output count
are reported through benchmark parameters, while allocation requires a separate
GC-profiler run. The bounded coroutine row measures CPU-oriented parallelism;
it does not model remote storage IO.

On the local Java 25/macOS smoke run, the Scrimage chained batch was about
`77 ms/op` for one input and about `625 ms/op` for eight inputs, while the
bounded-concurrency row stayed near `94 ms/op` at eight inputs. This supports
using bounded coroutine concurrency for multi-input CPU work, while keeping a
sequential path for a single image. These values describe this fixture and
host only, not a universal throughput guarantee.

![Batch and thumbnail scaling benchmark chart](../../../docs/images/readme-charts/images-benchmark-batch-pipeline-chart-01.png)

Raw output: [`batch-pipeline.json`](raw/issue-206-20260726-macos-java25/batch-pipeline.json).
