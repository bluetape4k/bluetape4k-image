package io.bluetape4k.images.vips

/**
 * 아직 incubating 상태인 Vips codec capability API를 표시합니다.
 *
 * ## 계약
 * - 표시된 declaration은 binary-compatibility 보장 없이 변경될 수 있습니다.
 * - stable Vips report container는 이 opt-in 없이도 계속 사용할 수 있습니다.
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
