package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.RestCountry;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class RestCountriesService {

    private final RestTemplate restTemplate =  new RestTemplate();

    public List<RestCountry> fetchCountries() {
        String url = "https://restcountries.com/v3.1/independent?status=true";

        ResponseEntity<RestCountry[]> response =
                restTemplate.getForEntity(url, RestCountry[].class);

        assert response.getBody() != null;
        return Arrays.asList(response.getBody());
    }
}
