package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.CountryDTO;
import com.gigtasker.userservice.dto.GenderDTO;
import com.gigtasker.userservice.mapper.CountryMapper;
import com.gigtasker.userservice.mapper.GenderMapper;
import com.gigtasker.userservice.repository.CountryRepository;
import com.gigtasker.userservice.repository.GenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferenceDataService  {

    private final CountryRepository countryRepository;
    private final GenderRepository genderRepository;
    private final CountryMapper countryMapper;
    private final GenderMapper genderMapper;

    public List<CountryDTO> getAllCountries() {
        return countryRepository.findAllByOrderByNameAsc().stream()
                .map(countryMapper::toDTO)
                .toList();
    }

    @Cacheable("genders")
    public List<GenderDTO> getAllGenders() {
        return genderRepository.findAll().stream()
                .map(genderMapper::toDTO)
                .toList();
    }
}
