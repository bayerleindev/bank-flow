package br.com.bankflow.onboarding.api.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class RustFsPresignedUploadService {

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration uploadTtl;

    public RustFsPresignedUploadService(
            S3Presigner s3Presigner,
            @Value("${app.rustfs.bucket}") String bucket,
            @Value("${app.rustfs.presigned-upload-ttl}") Duration uploadTtl) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.uploadTtl = uploadTtl;
    }

    public PresignedUpload presign(String storageKey, String contentType) {
        PutObjectRequest objectRequest =
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .contentType(contentType)
                        .build();
        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(uploadTtl)
                        .putObjectRequest(objectRequest)
                        .build();
        PresignedPutObjectRequest request = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUpload(request.url().toString(), Instant.now().plus(uploadTtl));
    }

    public record PresignedUpload(String uploadUrl, Instant expiresAt) {}
}
