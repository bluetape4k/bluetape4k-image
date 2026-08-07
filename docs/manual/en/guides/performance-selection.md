---
manualId: "performance-selection"
title: "Performance Selection"
locale: "en"
releaseRef: "0.4.0"
---

# Performance Selection

The repository benchmark compares Scrimage and libvips operations, I/O boundaries, streaming, concurrency, and memory. Use it to form a hypothesis, not as a universal ranking.

## Match the measurement to the task

Separate decode, transform, encode, file read/write, network transfer, and queue time. A faster resize kernel may not improve a request dominated by object storage or encoding. Measure representative dimensions, codecs, quality settings, operation chains, and concurrency.

## Read benchmark rows carefully

Check metric direction: lower average latency is better, while higher throughput is better. Confirm JDK, backend, fixture, warmup, iterations, and whether GC or native memory is included. The release reports include focused evidence for resize, encode, filters, pipeline allocation, I/O boundary, file throughput, large streaming, and memory.

## Choose an operational budget

Record:

- maximum input bytes and decoded pixels;
- latency percentile and throughput target;
- JVM heap and native-memory limit;
- maximum concurrent OCR or native operations;
- output quality and size constraints.

Then run a service-level test that includes storage and framework overhead. If Scrimage meets the budget, its simpler deployment can outweigh a lower native kernel time. If it does not, test one libvips backend on the real host.

Do not extrapolate Java 25 FFM results to Java 21 JNI, or a natural-photo fixture to documents and alpha-heavy graphics. See [interpreting benchmark results](../benchmarks/interpreting-results.md).

## Sources

- [Release benchmark guide](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/benchmark/images-benchmark/README.md)
- [Release benchmark reports](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/benchmark/images-benchmark/docs)
