package io.bluetape4k.images.spring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageStorageExceptionTest {

    private val key = ImageObjectKey.of("uploads", "photo.jpg")

    @Test
    fun `NotFoundException has correct key`() {
        val ex = ImageStorageException.NotFoundException(key)
        assertEquals(key, ex.key)
    }

    @Test
    fun `NotFoundException default message contains fullKey`() {
        val ex = ImageStorageException.NotFoundException(key)
        assertTrue(ex.message?.contains(key.fullKey) == true)
    }

    @Test
    fun `NotFoundException accepts custom message`() {
        val ex = ImageStorageException.NotFoundException(key, message = "Custom not found")
        assertEquals("Custom not found", ex.message)
    }

    @Test
    fun `NotFoundException is ImageStorageException`() {
        val ex = ImageStorageException.NotFoundException(key)
        assertTrue(ex is ImageStorageException)
    }

    @Test
    fun `AccessDeniedException has correct key`() {
        val ex = ImageStorageException.AccessDeniedException(key)
        assertEquals(key, ex.key)
    }

    @Test
    fun `AccessDeniedException default message contains fullKey`() {
        val ex = ImageStorageException.AccessDeniedException(key)
        assertTrue(ex.message?.contains(key.fullKey) == true)
    }

    @Test
    fun `AccessDeniedException is ImageStorageException`() {
        val ex = ImageStorageException.AccessDeniedException(key)
        assertTrue(ex is ImageStorageException)
    }

    @Test
    fun `ConflictException has correct key`() {
        val ex = ImageStorageException.ConflictException(key)
        assertEquals(key, ex.key)
    }

    @Test
    fun `ConflictException default message contains fullKey`() {
        val ex = ImageStorageException.ConflictException(key)
        assertTrue(ex.message?.contains(key.fullKey) == true)
    }

    @Test
    fun `ConflictException is ImageStorageException`() {
        val ex = ImageStorageException.ConflictException(key)
        assertTrue(ex is ImageStorageException)
    }

    @Test
    fun `TransientException with null key works`() {
        val ex = ImageStorageException.TransientException()
        assertNull(ex.key)
        assertNotNull(ex.message)
    }

    @Test
    fun `TransientException with key works`() {
        val ex = ImageStorageException.TransientException(key = key)
        assertEquals(key, ex.key)
        assertTrue(ex.message?.contains(key.fullKey) == true)
    }

    @Test
    fun `TransientException is ImageStorageException`() {
        val ex = ImageStorageException.TransientException()
        assertTrue(ex is ImageStorageException)
    }

    @Test
    fun `ValidationException with null key and explicit message works`() {
        val ex = ImageStorageException.ValidationException(key = null, message = "size exceeded")
        assertNull(ex.key)
        assertEquals("size exceeded", ex.message)
    }

    @Test
    fun `ValidationException with key and message works`() {
        val ex = ImageStorageException.ValidationException(key = key, message = "upload too large")
        assertEquals(key, ex.key)
        assertEquals("upload too large", ex.message)
    }

    @Test
    fun `ValidationException is ImageStorageException`() {
        val ex = ImageStorageException.ValidationException(message = "invalid")
        assertTrue(ex is ImageStorageException)
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
                is ImageStorageException.NotFoundException -> "not-found"
                is ImageStorageException.AccessDeniedException -> "access-denied"
                is ImageStorageException.ConflictException -> "conflict"
                is ImageStorageException.TransientException -> "transient"
                is ImageStorageException.ValidationException -> "validation"
            }
            assertNotNull(label)
        }
    }

    @Test
    fun `NotFoundException cause is stored`() {
        val cause = RuntimeException("root cause")
        val ex = ImageStorageException.NotFoundException(key, cause = cause)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun `TransientException cause is stored`() {
        val cause = java.io.IOException("disk error")
        val ex = ImageStorageException.TransientException(key = key, cause = cause)
        assertEquals(cause, ex.cause)
    }
}
