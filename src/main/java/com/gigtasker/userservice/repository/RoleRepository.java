package com.gigtasker.userservice.repository;

import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    @Query("select r from Role r where r.name = :roleName")
    Optional<Role> findByRoleName(RoleType roleName);
}
