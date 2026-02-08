package com.gigtasker.userservice.exceptions;

import java.util.NoSuchElementException;

public class GenderNotFoundException extends NoSuchElementException {
    public GenderNotFoundException(String genderType) {
        super("GenderType '" + genderType + "' not found!");
    }
}
