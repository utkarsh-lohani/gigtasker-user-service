package com.gigtasker.userservice.service;

import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.entity.User;
import com.gigtasker.userservice.enums.RoleType;
import com.gigtasker.userservice.repository.RoleRepository;
import com.gigtasker.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// Dormant Code Previously Used When We Created Users Directly on Keycloak and Synced to our DB's

@Service
@Slf4j
@RequiredArgsConstructor
public class UserSyncService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Qualifier("keycloakBot")
    private final Keycloak keycloakBot;

    // Run every 5 minutes (300,000 ms)
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void syncUsersFromKeycloak() {
        log.info("🔄 Starting Scheduled User Sync...");

        Set<UUID> existingIds = userRepository.findAllKeycloakIds();

        String realm = "gigtasker";
        List<UserRepresentation> kcUsers = keycloakBot.realm(realm).users().list();

        int newCount = 0;

        for (UserRepresentation kcUser : kcUsers) {
            UUID kcUuid = UUID.fromString(kcUser.getId());

            if (!existingIds.contains(kcUuid)) {
                createUserFromKeycloak(kcUser);
                newCount++;
            }
        }

        if (newCount > 0) {
            log.info("✅ Synced {} new users from Keycloak.", newCount);
        } else {
            log.info("⚡ Local DB is already up to date.");
        }
    }

    private void createUserFromKeycloak(UserRepresentation kcUser) {
        // Default role
        Role userRole = roleRepository.findByRoleName(RoleType.ROLE_USER).orElseThrow(() -> new NullPointerException("Role Not Found"));

        User newUser = User.builder()
                .keycloakId(UUID.fromString(kcUser.getId()))
                .username(kcUser.getUsername())
                .email(kcUser.getEmail())
                .firstName(kcUser.getFirstName())
                .lastName(kcUser.getLastName())
                .isDeleted(!kcUser.isEnabled()) // Sync disabled status
                .roles(Set.of(userRole))
                .build();

        userRepository.save(newUser);
        log.debug("Imported user: {}", kcUser.getEmail());
    }
}
