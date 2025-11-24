package com.gigtasker.userservice.controller;

import com.gigtasker.userservice.entity.Country;
import com.gigtasker.userservice.entity.Gender;
import com.gigtasker.userservice.repository.CountryRepository;
import com.gigtasker.userservice.repository.GenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/references")
@RequiredArgsConstructor
public class ReferenceController {

    private final CountryRepository countryRepository;
    private final GenderRepository genderRepository;

    @GetMapping("/countries")
    public ResponseEntity<List<Country>> getAllCountries() {
        return ResponseEntity.ok(countryRepository.findAllByOrderByNameAsc());
    }

    @GetMapping("/genders")
    public ResponseEntity<List<Gender>> getAllGenders() {
        return ResponseEntity.ok(genderRepository.findAll());
    }
}
