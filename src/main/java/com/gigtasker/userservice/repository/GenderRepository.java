package com.gigtasker.userservice.repository;

import com.gigtasker.userservice.entity.Gender;
import com.gigtasker.userservice.enums.GenderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GenderRepository extends JpaRepository<Gender, Long> {
    Optional<Gender> findByDescription(String description);
    Optional<Gender> findByName(GenderType name);
}
