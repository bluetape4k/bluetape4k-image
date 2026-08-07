---
manualId: "loading-and-writing"
title: "Loading and Writing"
locale: "en"
releaseRef: "0.4.0"
---

# Loading and Writing

Image I/O is where untrusted bytes become large in-memory structures and where partial output can escape. Keep decode and encode policy explicit.

## Loading

<code>immutableImageOf</code> accepts <code>ByteArray</code>, <code>InputStream</code>, Okio <code>BufferedSource</code>/<code>Source</code>, <code>File</code>, and <code>Path</code>. Choose the overload that matches the real boundary instead of reading through several temporary representations.

The helper decodes the image; it does not replace application upload policy. Bound encoded bytes, inspect dimensions when possible, reject unsupported formats, and apply timeouts before full processing. File paths must be resolved under an application-owned root.

## Writing

The module supplies synchronous and coroutine-friendly JPEG, PNG, and WebP writing paths. Pick quality and compression from product requirements, then assert output size and decodability. For files, write to a temporary sibling and move it into place when consumers must not see partial data.

Do not assume that a suspend writer makes CPU encoding non-blocking. Place CPU and file work on the dispatcher selected by the application and bound concurrent encodes.

## Large files and Okio

Okio adapters avoid unnecessary API conversions and make file boundaries explicit. They do not make a decode streaming if the image backend still materializes pixels. Measure peak memory with the largest allowed dimensions.

Run the [basic processing workshop](../modules/basic-processing.md) for a complete load-transform-write path. Compare native I/O only after reading [Vips API](../native/vips-api.md).

## Sources

- [Loading and writer support](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt)
- [Core module guide](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images/README.md)
