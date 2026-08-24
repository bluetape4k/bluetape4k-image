package io.bluetape4k.images.spring.storage.s3

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.aws.spring.s3.S3TransferOperations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import java.nio.file.Path

class S3TransferOperationsAdapterTest {

    @Test
    fun `adapter forwards path upload request and returns ETag`() = runTest {
        val delegate = mockk<S3TransferOperations>()
        val completedUpload = mockk<CompletedFileUpload>()
        val response = mockk<PutObjectResponse>()
        val configure = slot<UploadFileRequest.Builder.() -> Unit>()
        val source = Path.of("/tmp/image.jpg")
        every { completedUpload.response() } returns response
        every { response.eTag() } returns "etag"
        coEvery {
            delegate.uploadFile("images", "uploads/photo.jpg", source, capture(configure))
        } returns completedUpload

        val etag = S3TransferOperationsAdapter(delegate).uploadFile(
            bucket = "images",
            key = "uploads/photo.jpg",
            source = source,
            contentType = "image/jpeg",
        )

        etag shouldBeEqualTo "etag"
        val request = UploadFileRequest.builder()
            .source(source)
            .apply(configure.captured)
            .build()
        request.putObjectRequest().bucket() shouldBeEqualTo "images"
        request.putObjectRequest().key() shouldBeEqualTo "uploads/photo.jpg"
        request.putObjectRequest().contentType() shouldBeEqualTo "image/jpeg"
        coVerify(exactly = 1) {
            delegate.uploadFile("images", "uploads/photo.jpg", source, configure.captured)
        }
    }
}
