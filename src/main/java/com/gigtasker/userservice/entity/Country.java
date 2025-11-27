package com.gigtasker.userservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "countries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Country {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name; // "India"

    @Column(unique = true, nullable = false, length = 2)
    private String isoCode; // "IN"

    private String phoneCode; // "+91"

    private String currencyCode; // "INR"

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "region_id")
    private Region region; // "ASIA"
}