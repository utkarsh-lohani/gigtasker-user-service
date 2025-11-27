package com.gigtasker.userservice.exceptions;

import software.amazon.awssdk.services.s3.model.S3Exception;

public class AWSStorageException extends RuntimeException {
    private final String awsErrorCode;
    private final String requestId;

    public AWSStorageException(String message, S3Exception cause) {
        super(message, cause);
        this.awsErrorCode = cause.awsErrorDetails().errorCode();
        this.requestId = cause.requestId();
    }

    public String getAwsErrorCode() { return awsErrorCode; }
    public String getRequestId() { return requestId; }
}

