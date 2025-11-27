package com.gigtasker.userservice.controller;

import com.gigtasker.userservice.dto.CountryDTO;
import com.gigtasker.userservice.dto.GenderDTO;
import com.gigtasker.userservice.service.ReferenceDataService;
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

    private final ReferenceDataService referenceDataService;

    @GetMapping("/countries")
    public ResponseEntity<List<CountryDTO>> getAllCountries() {
        return ResponseEntity.ok(referenceDataService.getAllCountries());
    }

    @GetMapping("/genders")
    public ResponseEntity<List<GenderDTO>> getAllGenders() {
        return ResponseEntity.ok(referenceDataService.getAllGenders());
    }
}
