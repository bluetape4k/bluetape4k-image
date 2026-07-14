---
manualId: "interpreting-benchmark-results"
title: "Interpreting Benchmark Results"
locale: "en"
releaseRef: "0.3.0"
---

# Interpreting Benchmark Results

The benchmark project is executable evidence, not a published dependency. Its reports compare Scrimage and libvips processing plus I/O and allocation boundaries available in 0.3.0.

## Know the measured operation

Resize, encode, filter, chained pipeline, file I/O, large streaming, and memory profiles answer different questions. Read the benchmark class and fixture before using a chart. An encode row includes codec work; a resize row may not. Throughput and average-time metrics also point in opposite directions.

## Preserve context

Record JDK, operating system, CPU, libvips version, selected <code>vips.impl</code>, image dimensions and content, codec quality, warmups, iterations, forks, and metric unit. The repository reports are local measurements from their recorded environment. They do not prove cloud-instance, container, or concurrent-request behavior.

## Turn a report into a decision

1. Find the report closest to the target operation.
2. Reproduce its task on the intended JDK and native backend.
3. Replace or add representative application fixtures.
4. Add storage and framework boundaries.
5. Measure latency percentiles, throughput, JVM heap, native memory, and output size.
6. Choose the simplest backend that meets the budget.

Large streaming and memory reports deserve special care: managed-heap allocation does not account for all native memory, and an Okio API does not guarantee that decoded pixels remain streaming.

## Reproduction

The benchmark module selects Java 21 or Java 25 from <code>-Pvips.impl</code>. Run focused tasks from the release guide and keep raw JSON with the environment note. Do not mix results from changed source into a frozen 0.3 manual claim.

## Sources

- [Benchmark module guide](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/benchmark/images-benchmark/README.md)
- [0.3.0 reports and raw evidence](https://github.com/bluetape4k/bluetape4k-image/tree/a571c30004f571fe8cfcddc29670c1404d212ec6/benchmark/images-benchmark/docs)
