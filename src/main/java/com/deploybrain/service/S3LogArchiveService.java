package com.deploybrain.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Slf4j
@Service
public class S3LogArchiveService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public S3LogArchiveService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @PostConstruct
    public void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("S3 bucket '{}' already exists", bucketName);
        } catch (NoSuchBucketException e) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("Created S3 bucket '{}'", bucketName);
            } catch (Exception createEx) {
                log.warn("Could not create S3 bucket '{}' - log archival will fail silently until this is resolved: {}",
                        bucketName, createEx.getMessage());
            }
        } catch (Exception e) {
            log.warn("Could not verify S3 bucket '{}' existence at startup: {}", bucketName, e.getMessage());
        }
    }

    public void archiveLogZip(String repoName, UUID buildId, byte[] zipBytes) {
        String key = String.format("logs/%s/%s.zip", repoName, buildId);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType("application/zip")
                            .build(),
                    RequestBody.fromBytes(zipBytes)
            );
            log.info("Archived log zip to S3/MinIO: {}", key);
        } catch (Exception e) {
            log.warn("Failed to archive log zip for build {} - continuing without archival: {}",
                    buildId, e.getMessage());
        }
    }
}