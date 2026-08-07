---
manualId: "transforms-and-filters"
title: "Transforms and Filters"
locale: "en"
releaseRef: "0.4.0"
---

# Transforms and Filters

The core module provides direct immutable-image functions and an <code>ImageFilterChain</code> DSL. Use direct functions for a small named operation and the DSL when one processing policy composes several steps.

## Geometry

The release includes resize, split, padding, rotation and flip, perspective transform, automatic crop, smart crop, and CLAHE/global histogram equalization. Decide target dimensions and crop policy before cosmetic filters. Geometry changes the pixels that later analysis sees.

## Filter chain

<code>applyFilters</code> evaluates an <code>ImageFilterChain</code> containing blur and sharpen operations, brightness/contrast/gamma and color operations, style effects, pixelation, borders, rounded corners, watermark, caption, and geometry extensions.

Keep reusable policies as named functions:

    fun prepareThumbnail(source: ImmutableImage): ImmutableImage =
        source.applyFilters {
            smartCrop(640, 360)
            sharpen()
        }

Validate every option at the application boundary. Radius, target size, coordinates, opacity, and text can create excessive work or invalid geometry when copied directly from a request.

## Ordering

Normalize orientation and crop first, resize near the final dimensions, then apply filters whose radius or text size depends on pixels. Apply watermark or caption after destructive crop. Encode only after the chain is complete.

Use golden images for intent and numeric assertions for dimensions and invariants. When visual output changes intentionally, review the image before accepting a new fixture.

## Sources

- [Filter DSL](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images/src/main/kotlin/io/bluetape4k/images/filters/dsl)
- [Transform implementations](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images/src/main/kotlin/io/bluetape4k/images/transforms)
