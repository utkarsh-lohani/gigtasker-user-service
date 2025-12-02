package com.gigtasker.userservice.repository;

import com.gigtasker.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    List<User> findByIdIn(List<Long> ids);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdWithRoles(Long id);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles")
    List<User> findAllWithRoles();

    @Modifying
    @Query(value = "DELETE FROM users WHERE id = :id", nativeQuery = true)
    void hardDeleteById(Long id);

    @Modifying
    @Query(value = "DELETE FROM users_roles WHERE user_id = :id", nativeQuery = true)
    void removeAllRoles(Long id);

    @Query("SELECT u.keycloakId FROM User u")
    Set<UUID> findAllKeycloakIds();

    Optional<User> findByKeycloakId(UUID keycloakId);
}
