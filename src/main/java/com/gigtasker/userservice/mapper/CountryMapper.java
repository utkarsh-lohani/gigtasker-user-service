package com.gigtasker.userservice.mapper;

import com.gigtasker.userservice.dto.CountryDTO;
import com.gigtasker.userservice.entity.Country;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CountryMapper {

    @Mapping(source = "region.name", target = "regionName", defaultValue = "Unknown")
    CountryDTO toDTO(Country country);
}