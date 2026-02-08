package com.gigtasker.userservice.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RestCountry {
    private Name name;
    private String cca2;
    private Idd idd;
    private Map<String, Currency> currencies;

    private String region;        // "Americas"
    private String subregion;     // "Caribbean"
    private List<String> continents; // ["North America"]

    @Data
    public static class Name {
        private String common;
    }

    @Data
    public static class Idd {
        private String root;
        private List<String> suffixes;
    }

    @Data
    public static class Currency {
        private String name;
        private String symbol;
    }
}
