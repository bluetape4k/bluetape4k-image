---
manualId: "immutable-image-model"
title: "Immutable Image Model"
locale: "en"
releaseRef: "0.3.0"
---

# Immutable Image Model

<code>bluetape4k-images</code> treats Scrimage <code>ImmutableImage</code> as the portable value passed between loading, transformation, analysis, and writing steps. Operations return a new image instead of mutating the source.

## Why the model matters

An immutable value makes a processing chain easier to reason about and reuse, but each result can hold a full pixel buffer. Immutability is not free memory. Keep operation scopes short, avoid retaining intermediate images, and measure long chains with representative dimensions.

Use <code>immutableImageOf</code> overloads for byte arrays, streams, Okio sources, files, and paths. These helpers centralize decoding and keep callers on the same image type. [Loading and writing](loading-and-writing.md) explains ownership at I/O boundaries.

## Drawing and mutable interop

Use <code>withGraphics</code> when drawing onto an <code>ImmutableImage</code>; the helper manages the Java2D graphics context and returns an immutable result. Mutable <code>BufferedImage</code> interop remains available for APIs that require it, but keep the mutable object inside a narrow adapter boundary.

## Design a pipeline

Build the pipeline in this order:

1. validate encoded input and dimensions;
2. decode once;
3. normalize orientation, color, and target size;
4. apply transforms or filters;
5. analyze the normalized image when comparisons require a common scale;
6. encode once at the output boundary.

CAPTCHA and OCR consume the same model, while libvips uses a separate closeable <code>VipsImage</code> contract. Do not confuse value-style <code>ImmutableImage</code> with native-resource ownership.

## Sources

- [ImmutableImage factories](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt)
- [Module reference](../modules/bluetape4k-images.md)
