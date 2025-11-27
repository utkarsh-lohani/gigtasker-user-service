package com.gigtasker.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${cloud.aws.s3.endpoint}") // e.g. http://localhost:9000
    private String endpoint;

    public String uploadProfileImage(UUID userId, MultipartFile file) {
        String extension = getExtension(file.getOriginalFilename());
        String key = "avatars/" + userId.toString() + "." + extension;

        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putOb, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return key;

        } catch (IOException e) {
            log.error("Failed to upload file", e);
            throw new RuntimeException("File upload failed");
        }
    }

    private String getExtension(String filename) {
        return filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf(".") + 1)
                : "jpg";
    }
}
