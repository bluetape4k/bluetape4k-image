package io.bluetape4k.images.spring.autoconfigure;

import io.bluetape4k.aws.spring.s3.S3ListPage;
import io.bluetape4k.aws.spring.s3.S3Operations;
import io.bluetape4k.aws.spring.s3.S3Resource;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Duration;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 명시적인 headObject 구현 없이 컴파일된 Java consumer fixture입니다.
 * 상속된 Kotlin default method가 STORAGE-1 이전 runtime을 나타냅니다.
 */
public final class LegacyJavaS3Operations implements S3Operations {

    @Override
    public Object existsBucket(String bucket, Continuation<? super Boolean> continuation) {
        return null;
    }

    @Override
    public Object upload(
        String bucket,
        String key,
        byte[] bytes,
        String contentType,
        Continuation<? super PutObjectResponse> continuation
    ) {
        return null;
    }

    @Override
    public Object upload(
        String bucket,
        String key,
        String contents,
        Charset charset,
        String contentType,
        Continuation<? super PutObjectResponse> continuation
    ) {
        return null;
    }

    @Override
    public Object downloadBytes(String bucket, String key, Continuation<? super byte[]> continuation) {
        return null;
    }

    @Override
    public Object downloadText(
        String bucket,
        String key,
        Charset charset,
        Continuation<? super String> continuation
    ) {
        return null;
    }

    @Override
    public Object delete(String bucket, String key, Continuation<? super DeleteObjectResponse> continuation) {
        return null;
    }

    @Override
    public Object listPage(
        String bucket,
        String prefix,
        int maxKeys,
        String continuationToken,
        Continuation<? super S3ListPage> continuation
    ) {
        return null;
    }

    @Override
    public Flow<S3Object> listFlow(String bucket, String prefix, int pageSize) {
        return null;
    }

    @Override
    public S3Resource resource(String bucket, String key) {
        return null;
    }

    @Override
    public URL presignGet(String bucket, String key, Duration duration) {
        return null;
    }

    @Override
    public URL presignPut(String bucket, String key, Duration duration, String contentType) {
        return null;
    }
}
