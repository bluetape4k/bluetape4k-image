# Ktor Multipart Thumbnail Route Benchmark (Issue #205)

`KtorThumbnailRouteBenchmark` measures multipart parsing, direct image
processing, and the complete production thumbnail route with Ktor's in-process
test host. The test application starts once per JMH trial and never binds a
network port.

## Command

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkKtorRouteBenchmark
./gradlew :bluetape4k-images-benchmark:benchmarkKtorRouteConcurrencyBenchmark
```

The concurrency task succeeds only when one fresh JSON report contains all 15
expected parameter rows in `sample` mode with p50, p95, and p99 data. This
prevents a JMH workload exception from being hidden as a partially populated
Gradle-success report.

## Environment

| Item | Value |
|------|-------|
| Host | Apple M4 Pro, 48 GiB |
| OS | macOS 26.5.2 (25F84) |
| JVM | Oracle GraalVM 25.0.3, Java 25 |
| Ktor | 3.5.1 test host |
| JMH, single request | 1 fork, 1 x 1 s warmup, 3 x 1 s measurement |
| JMH, concurrency | 1 fork, 3 x 3 s warmup, 5 x 3 s measurement |
| Metrics | single request: `AverageTime ms/op`; concurrency: `SampleTime ms/batch` |
| Source fixture | `landscape.jpg`, SHA-256 `bd674fb8518311c3d9add76b54d4a05baef3328991a0214360c2a8cf62716f05` |

## Fixtures And Limits

Each accepted JPEG is generated once during trial setup from the checked-in
natural-photo fixture with JPEG quality 82.

| Fixture | Dimensions | Encoded payload |
|---------|------------|----------------:|
| `avatar` | `256x256` | 15,656 bytes |
| `medium` | `1920x1080` | 419,392 bytes |
| `photo4k` | `3840x2160` | 1,434,914 bytes |
| rejected oversize | streamed bytes | 1,048,577 bytes |
| mixed rejected oversize | streamed bytes | 2,097,153 bytes |

The accepted route allows 16 MiB, 16,777,216 decoded pixels, and an 8,192-pixel
side. It returns a maximum `320x320` PNG using `PngWriter.MaxCompression`. The
rejected route lowers `maxInputBytes` to 1 MiB and sends one additional byte.
The mixed lane uses a 2 MiB limit so both accepted fixtures remain valid, then
sends one additional byte for exactly 10% of each batch.

## Results

| Fixture | Multipart parse only | Decode + thumbnail + PNG | Full production route |
|---------|---------------------:|------------------------:|----------------------:|
| `avatar` | 0.122 +/- 0.648 ms | 14.589 +/- 4.781 ms | 16.900 +/- 6.966 ms |
| `medium` | 0.196 +/- 0.468 ms | 34.572 +/- 5.693 ms | 37.070 +/- 9.441 ms |
| `photo4k` | 0.369 +/- 0.691 ms | 98.645 +/- 38.459 ms | 102.588 +/- 13.227 ms |
| rejected oversize | N/A | N/A | 0.351 +/- 1.505 ms |

![Ktor multipart thumbnail route benchmark chart](../../../docs/images/readme-charts/images-benchmark-ktor-thumbnail-route-chart-01.png)

The full-route delta over direct image work is about `2.3 ms` for the avatar,
`2.5 ms` for the medium photo, and `3.9 ms` for the 4K photo. On this host,
decode, resize, and PNG encoding dominate accepted-request latency; multipart
parsing alone remains below `0.4 ms/op`.

The oversize request is rejected in about `0.35 ms/op`, before image decode.
This supports keeping byte-limit validation ahead of dimension probing and
thumbnail generation. The full-route delta includes in-process Ktor request
handling, multipart validation, coroutine dispatch, and response
serialization. It excludes sockets, TLS, reverse proxies, and real network IO.

The error intervals are broad because this is a short local evidence run.
Use the raw samples for comparison, rerun on deployment hardware, and do not
treat these values as a production capacity guarantee.

Raw output:
[`ktor-route.json`](raw/issue-205-20260726-macos-java25/ktor-route.json)
(SHA-256 `4cb260b1967785f7e6e3ddef3e2e87f240ee8715dd799d9a0abec313babef134`).

## Concurrent Accepted Requests

Each JMH sample releases one closed-loop batch from a shared coroutine gate and
completes only after every response body is consumed. The reported percentiles
are **batch completion latency**, not individual-request latency. Derived
requests per second are `concurrency * 1000 / mean ms/batch`; this normalization
is useful for locating saturation in this test host but is not open-loop server
throughput.

| Fixture | Concurrency | Mean ms/batch | p50 | p95 | p99 | Derived req/s |
|---------|------------:|--------------:|----:|----:|----:|--------------:|
| `medium` | 1 | 36.630 | 36.176 | 38.558 | 52.203 | 27.30 |
| `medium` | 5 | 43.097 | 41.157 | 50.070 | 94.110 | 116.02 |
| `medium` | 10 | 63.537 | 61.833 | 74.200 | 88.993 | 157.39 |
| `medium` | 30 | 233.102 | 229.638 | 290.770 | 470.286 | 128.70 |
| `photo4k` | 1 | 103.119 | 101.515 | 113.725 | 134.159 | 9.70 |
| `photo4k` | 5 | 116.847 | 113.836 | 136.643 | 154.062 | 42.79 |
| `photo4k` | 10 | 169.973 | 166.199 | 191.365 | 289.931 | 58.83 |
| `photo4k` | 30 | 574.240 | 555.745 | 687.866 | 696.254 | 52.24 |

![Ktor accepted-route concurrency chart](../../../docs/images/readme-charts/images-benchmark-ktor-concurrency-chart-01.png)

Both fixtures reach their highest derived throughput at concurrency 10. Raising
concurrency from 10 to 30 reduces throughput by about 18.2% for `medium` and
11.2% for `photo4k`, while p95 batch completion grows from 74.2 to 290.8 ms and
191.4 to 687.9 ms respectively. Concurrency 30 is therefore useful as a
saturation probe on this 12-core host, but it is not a sensible default
capacity target.

## Rejected And Mixed Traffic

Expected `400 Bad Request` responses count as successful benchmark operations;
any unexpected status fails the sample.

| Workload | Fixture | Concurrency | Mean ms/batch | p95 | p99 | Derived req/s |
|----------|---------|------------:|--------------:|----:|----:|--------------:|
| rejected only | N/A | 1 | 0.210 | 0.258 | 0.304 | 4,760.78 |
| rejected only | N/A | 10 | 1.209 | 1.780 | 4.260 | 8,272.79 |
| rejected only | N/A | 30 | 7.528 | 29.213 | 59.549 | 3,985.29 |
| 90% accepted / 10% rejected | `medium` | 10 | 57.411 | 64.766 | 74.187 | 174.18 |
| 90% accepted / 10% rejected | `medium` | 30 | 176.290 | 204.787 | 218.104 | 170.17 |
| 90% accepted / 10% rejected | `photo4k` | 10 | 156.511 | 179.988 | 241.435 | 63.89 |
| 90% accepted / 10% rejected | `photo4k` | 30 | 511.312 | 611.372 | 640.680 | 58.67 |

The rejected-only path also peaks at concurrency 10 and develops a long tail at
30 (`p95 29.213 ms` versus `1.780 ms`). Mixed traffic remains dominated by the
accepted image work, but its derived throughput drops 2.3% for `medium` and
8.2% for `photo4k` between concurrency 10 and 30. These results support a
bounded admission/concurrency policy and deployment-specific load testing
before choosing production limits.

## Operational Throttling Recommendation

Accepted thumbnail requests perform CPU- and memory-intensive decode, resize,
and PNG encoding. The concurrency-30 rows add queueing and tail latency without
improving accepted-route throughput, so this route needs bounded admission in a
production service.

Use the following as an initial deployment policy:

| Control | Initial policy | Reasoning from this run |
|---------|----------------|-------------------------|
| Per-instance accepted-route concurrency | Start with a configurable cap of `10` active thumbnail requests. | Both accepted fixtures peak at concurrency 10; concurrency 30 reduces derived throughput and increases p95 batch latency. |
| Waiting requests | Use a bounded queue; do not allow unbounded coroutine or request accumulation. | Saturation at 30 raises the 4K p95 batch completion from 191.4 to 687.9 ms. |
| Capacity overload | Return `503 Service Unavailable` with `Retry-After` when the service-wide queue is full. | Protects CPU and heap from work that cannot be admitted promptly. |
| Client or tenant quota | Apply a separate rate limit and return `429 Too Many Requests` when that quota is exceeded. | Prevents one caller from consuming the shared thumbnail budget. |
| Large inputs | Keep byte/pixel/side guards before decode; consider a lower quota or asynchronous job path for large uploads. | `photo4k` has materially lower throughput and larger tail latency than `medium`. |
| Observability | Record active work, queue depth, admission rejections, p95/p99 completion time, and input dimensions. | Lets the deployment-specific cap be changed from live evidence instead of a fixed local snapshot. |

The cap of `10` is a starting point for one instance, not a universal capacity
number. Before release, repeat the accepted, rejected, and mixed workloads with
the deployment CPU/memory limit, storage and network boundaries, replica count,
and expected arrival pattern. Choose the final cap from the latency SLO and
overload behavior observed there.

The test host excludes sockets, TLS, reverse proxies, client think time, and
open-loop arrival rates. It also runs image processing through the current
production route dispatcher without modeling a multi-instance deployment.

Concurrency raw output:
[`ktor-route-concurrency.json`](raw/issue-205-20260726-macos-java25/ktor-route-concurrency.json)
(SHA-256 `345e9eb08940856daf364970358bcc3681a3136b4c4e5fc62713e3ac56cd04f2`).
