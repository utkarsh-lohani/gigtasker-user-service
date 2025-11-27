package com.gigtasker.userservice.mapper;

import com.gigtasker.userservice.dto.GenderDTO;
import com.gigtasker.userservice.entity.Gender;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface GenderMapper {
    @Mapping(target = "name", expression = "java(gender.getName().name())")
    GenderDTO toDTO(Gender gender);
}
