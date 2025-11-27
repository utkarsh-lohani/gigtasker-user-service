package com.gigtasker.userservice.dto;

import java.time.LocalDate;

public record UserUpdateDTO(
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String ethnicity,
        Long genderId,
        Long countryId
) {}
