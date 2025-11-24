package com.gigtasker.userservice.service;

import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.enums.RoleType;
import com.gigtasker.userservice.exceptions.KeycloakException;
import com.gigtasker.userservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public Optional<Role> findRoleByName(RoleType roleType) {
        return roleRepository.findByRoleName(roleType);
    }

    public Set<Role> processRoles(List<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String roleName : roleNames) {
            try {
                // 1. Try to match Keycloak string to our Enum
                RoleType type = RoleType.valueOf(roleName);

                // 2. If match found, get the Entity from DB
                roleRepository.findByRoleName(type).ifPresent(roles::add);
            } catch (IllegalArgumentException e) {
                // 3. If Keycloak sends a role we don't know (e.g. "offline_access"), IGNORE it.
                // This prevents the app from crashing on unknown roles.
                log.trace("Ignoring unknown Keycloak role: {}", roleName);
                throw new KeycloakException(e.getMessage());
            }
        }

        // Ensure every user has at least ROLE_USER
        if (roles.isEmpty()) {
            roleRepository.findByRoleName(RoleType.ROLE_USER).ifPresent(roles::add);
        }
        return roles;
    }
}
