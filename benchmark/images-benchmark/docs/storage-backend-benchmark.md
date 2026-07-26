# Storage Backend Benchmark (Issue #204)

This suite measures the `ImageStorage` adapter boundary, not a production S3
network. The local lane uses `LocalImageStorage` with a temporary filesystem
root. The S3 lane uses `S3ImageStorage` backed by an in-memory `S3Operations`
double, so it is deterministic and requires no credentials.

## Commands

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkStorageLocalBenchmark
./gradlew :bluetape4k-images-benchmark:benchmarkStorageS3Benchmark \
  -Pstorage.s3.enabled=true
```

The S3 command is intentionally opt-in. It measures adapter and byte
materialization overhead; it must not be presented as cloud latency or
throughput. A live S3-compatible endpoint can be evaluated separately with
the same `ImageStorage` contract and an environment-specific harness.

## Workload

| Dimension | Value |
|-----------|-------|
| Payloads | deterministic `homer.jpg` JPEG and PNG encodings |
| Size guard | 4 MiB maximum; below-limit and 4 MiB + 1 byte rejection |
| Object count | 9 objects per backend (one payload plus eight list fixtures) |
| Operations | byte upload/download, path download, prefix list, over-limit upload |
| Cleanup | temporary local root deleted in JMH tear-down; in-memory S3 map is trial-scoped |

The benchmark methods keep setup uploads outside the measured iteration. This
separates API operation cost from fixture creation and cleanup cost.

Raw Java 25/macOS output: [`storage-local.json`](raw/issue-204-20260726-macos-java25/storage-local.json)
and [`storage-s3-inmemory.json`](raw/issue-204-20260726-macos-java25/storage-s3-inmemory.json).

![Storage backend benchmark chart](../../../docs/images/readme-charts/images-benchmark-storage-backend-chart-01.png)

## Interpretation

Latency is `AverageTime ms/op` and lower is better. Results are local snapshots:
filesystem rows include OS cache effects, while in-memory S3 rows represent
adapter overhead only. Allocation or network conclusions require a separate
GC-profiler run and a real S3-compatible service.
