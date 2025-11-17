package com.gigtasker.userservice.exceptions;

public class KeycloakException extends RuntimeException{
    public KeycloakException(String message) {
        super(message);
    }
}
