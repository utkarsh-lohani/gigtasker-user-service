package com.gigtasker.userservice.exceptions;

public class ResourceNotFoundException extends RuntimeException{
    public ResourceNotFoundException(String message) {
        super(message); // Pass the error message up to the parent RuntimeException
    }
}
