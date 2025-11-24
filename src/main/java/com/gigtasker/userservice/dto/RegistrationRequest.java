package com.gigtasker.userservice.dto;

import com.gigtasker.userservice.entity.Country;
import com.gigtasker.userservice.entity.Gender;

import java.time.LocalDate;

public record RegistrationRequest(
        String username,
        String email,
        String password,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String ethnicity,
        Gender gender,
        Country country
) {}
