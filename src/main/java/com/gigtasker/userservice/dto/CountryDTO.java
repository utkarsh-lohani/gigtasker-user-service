package com.gigtasker.userservice.dto;

import java.io.Serializable;

public record CountryDTO(
        Long id,
        String name,
        String isoCode,
        String phoneCode,
        String currencyCode,
        String regionName
) implements Serializable {}