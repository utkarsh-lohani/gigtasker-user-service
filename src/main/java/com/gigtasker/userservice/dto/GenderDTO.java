package com.gigtasker.userservice.dto;

import java.io.Serializable;

public record GenderDTO(
        Long id,
        String name,
        String description
) implements Serializable {}