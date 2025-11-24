package com.gigtasker.userservice.repository;

import com.gigtasker.userservice.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CountryRepository extends JpaRepository<Country, Long> {
    List<Country> findAllByOrderByNameAsc();
    List<Country> findAllByOrderByRegionAsc();
    Optional<Country> findByIsoCode(String isoCode);
}
