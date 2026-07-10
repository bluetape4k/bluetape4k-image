package io.bluetape4k.images.vips

/**
 * Marks a Vips codec capability API that is still incubating.
 *
 * ## Contract
 * - Marked declarations may change without binary-compatibility guarantees.
 * - Stable Vips report containers remain available without this opt-in.
 *
 * ```kotlin
 * @OptIn(VipsIncubatingApi::class)
 * val format = VipsImageFormat.AVIF
 * ```
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This Vips codec capability API is incubating and may change without binary compatibility guarantees. Use @OptIn(VipsIncubatingApi::class) to acknowledge it.",
)
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
annotation class VipsIncubatingApi
