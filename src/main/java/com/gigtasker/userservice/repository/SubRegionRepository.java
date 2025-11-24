package com.gigtasker.userservice.repository;

import com.gigtasker.userservice.entity.SubRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubRegionRepository extends JpaRepository<SubRegion, Long> {
}
