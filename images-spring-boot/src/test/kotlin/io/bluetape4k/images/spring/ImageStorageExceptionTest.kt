package io.bluetape4k.images.spring

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.Test

class ImageStorageExceptionTest {

    private val key = ImageObjectKey.of("uploads", "photo.jpg")

    @Test
    fun `NotFoundException has correct key`() {
        val ex = ImageStorageException.NotFoundException(key)
        ex.key shouldBeEqualTo key
    }

    @Test
    fun `NotFoundException default message contains fullKey`() {
        val ex = ImageStorageException.NotFoundException(key)
        ex.message.orEmpty().contains(key.fullKey).shouldBeTrue()
    }

    @Test
    fun `NotFoundException accepts custom message`() {
        val ex = ImageStorageException.NotFoundException(key, message = "Custom not found")
        ex.message shouldBeEqualTo "Custom not found"
    }

    @Test
    fun `NotFoundException is ImageStorageException`() {
        val ex = ImageStorageException.NotFoundException(key)
        ex.shouldBeInstanceOf<ImageStorageException>()
    }

    @Test
    fun `AccessDeniedException has correct key`() {
        val ex = ImageStorageException.AccessDeniedException(key)
        ex.key shouldBeEqualTo key
    }

    @Test
    fun `AccessDeniedException default message contains fullKey`() {
        val ex = ImageStorageException.AccessDeniedException(key)
        ex.message.orEmpty().contains(key.fullKey).shouldBeTrue()
    }

    @Test
    fun `AccessDeniedException is ImageStorageException`() {
        val ex = ImageStorageException.AccessDeniedException(key)
        ex.shouldBeInstanceOf<ImageStorageException>()
    }

    @Test
    fun `ConflictException has correct key`() {
        val ex = ImageStorageException.ConflictException(key)
        ex.key shouldBeEqualTo key
    }

    @Test
    fun `ConflictException default message contains fullKey`() {
        val ex = ImageStorageException.ConflictException(key)
        ex.message.orEmpty().contains(key.fullKey).shouldBeTrue()
    }

    @Test
    fun `ConflictException is ImageStorageException`() {
        val ex = ImageStorageException.ConflictException(key)
        ex.shouldBeInstanceOf<ImageStorageException>()
    }

    @Test
    fun `TransientException with null key works`() {
        val ex = ImageStorageException.TransientException()
        ex.key.shouldBeNull()
        ex.message.shouldNotBeNull()
    }

    @Test
    fun `TransientException with key works`() {
        val ex = ImageStorageException.TransientException(key = key)
        ex.key shouldBeEqualTo key
        ex.message.orEmpty().contains(key.fullKey).shouldBeTrue()
    }

    @Test
    fun `TransientException is ImageStorageException`() {
        val ex = ImageStorageException.TransientException()
        ex.shouldBeInstanceOf<ImageStorageException>()
    }

    @Test
    fun `ValidationException with null key and explicit message works`() {
        val ex = ImageStorageException.ValidationException(key = null, message = "size exceeded")
        ex.key.shouldBeNull()
        ex.message shouldBeEqualTo "size exceeded"
    }

    @Test
    fun `ValidationException with key and message works`() {
        val ex = ImageStorageException.ValidationException(key = key, message = "upload too large")
        ex.key shouldBeEqualTo key
        ex.message shouldBeEqualTo "upload too large"
    }

    @Test
    fun `ValidationException is ImageStorageException`() {
        val ex = ImageStorageException.ValidationException(message = "invalid")
        ex.shouldBeInstanceOf<ImageStorageException>()
    }

    @Test
    fun `when expression covers all sealed subtypes exhaustively`() {
        val exceptions: List<ImageStorageException> = listOf(
            ImageStorageException.NotFoundException(key),
            ImageStorageException.AccessDeniedException(key),
            ImageStorageException.ConflictException(key),
            ImageStorageException.TransientException(key = key),
            ImageStorageException.ValidationException(key = key, message = "invalid"),
        )

        exceptions.forEach { ex ->
            val label = when (ex) {
                is ImageStorageException.NotFoundException     -> "not-found"
                is ImageStorageException.AccessDeniedException -> "access-denied"
                is ImageStorageException.ConflictException     -> "conflict"
                is ImageStorageException.TransientException    -> "transient"
                is ImageStorageException.ValidationException   -> "validation"
            }
            label.shouldNotBeNull()
        }
    }

    @Test
    fun `NotFoundException cause is stored`() {
        val cause = RuntimeException("root cause")
        val ex = ImageStorageException.NotFoundException(key, cause = cause)
        ex.cause shouldBeEqualTo cause
    }

    @Test
    fun `TransientException cause is stored`() {
        val cause = java.io.IOException("disk error")
        val ex = ImageStorageException.TransientException(key = key, cause = cause)
        ex.cause shouldBeEqualTo cause
    }
}
