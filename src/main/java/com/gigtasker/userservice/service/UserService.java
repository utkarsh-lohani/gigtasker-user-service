package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.UserDTO;
import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.entity.User;
import com.gigtasker.userservice.enums.RoleType;
import com.gigtasker.userservice.exceptions.KeycloakException;
import com.gigtasker.userservice.exceptions.ResourceNotFoundException;
import com.gigtasker.userservice.repository.UserRepository;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    // Using Spring Transactional because it's much powerful
    // readonly = true in Transactional indicates JPA/Hibernate that to not bother dirty checking those objects as I'm not saving anything
    // Or in other word telling DB that it's just a select query

    private final UserRepository userRepository;
    private final Keycloak keycloakBot;
    private final RoleService roleService;

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String GIGTASKER = "gigtasker";

    public UserService(UserRepository userRepository,
                       @Qualifier("keycloakBot") Keycloak keycloakBot, RoleService roleService) {
        this.userRepository = userRepository;
        this.keycloakBot = keycloakBot;
        this.roleService = roleService;
    }

    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        // Logic - We get data in the form of UserDTO
        // And we save it in the form of User since User Entity exists in our DB
        // And we have returned the user back in UserDTO Format
        // DTO's are used to explicitly hide the actual Entity from being exposed
        User user = User.builder().username(userDTO.getUsername()).email(userDTO.getEmail()).build();
        User savedUser = userRepository.save(user);
        return UserDTO.builder().username(savedUser.getUsername()).email(savedUser.getEmail()).build();
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        return userRepository.findByIdWithRoles(id).map(UserDTO::fromEntity).orElse(null);
    }

    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        return userRepository.findByEmail(email).map(UserDTO::fromEntity).orElse(null);
    }

    @Transactional
    public UserDTO getMe() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email = jwt.getClaimAsString("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found. Please register via the app."));

        List<String> rolesFromToken = extractRolesFromToken(jwt);
        Set<Role> syncedRoles = roleService.processRoles(rolesFromToken);

        Set<RoleType> currentRoleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        Set<RoleType> newRoleNames = syncedRoles.stream().map(Role::getName).collect(Collectors.toSet());

        if (!currentRoleNames.equals(newRoleNames)) {
            log.info("Syncing roles for user {}", email);
            user.setRoles(syncedRoles);
            user = userRepository.save(user);
        }

        return UserDTO.fromEntity(user);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromToken(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return new ArrayList<>();
        return (List<String>) realmAccess.getOrDefault("roles", new ArrayList<>());
    }

    @Transactional(readOnly = true)
    public List<UserDTO> findUsersByIds(List<Long> ids) {
        return userRepository.findByIdIn(ids)
                .stream()
                .map(UserDTO::fromEntity).toList();
    }

    @Transactional
    public void promoteUserToAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found in local DB"));

        RealmResource realm = keycloakBot.realm(GIGTASKER);
        UsersResource usersResource = realm.users();

        try {
            // 1. Find Keycloak User (Use UUID from DB is safer if you have it populated)
            List<UserRepresentation> kcUsers = usersResource.searchByEmail(user.getEmail(), true);
            if (kcUsers.isEmpty()) throw new ResourceNotFoundException("User missing in Keycloak");
            String keycloakUserId = kcUsers.getFirst().getId();

            // 2. Assign ADMIN Role in Keycloak
            RoleRepresentation adminRole = realm.roles().get(ROLE_ADMIN).toRepresentation();
            usersResource.get(keycloakUserId).roles().realmLevel().add(List.of(adminRole));

            // 3. Add to Admin Group (Optional, only if you use groups)
            List<GroupRepresentation> groups = realm.groups().groups("GIGTASKER_ADMIN_USERS", 0, 1);
            if (!groups.isEmpty()) {
                usersResource.get(keycloakUserId).joinGroup(groups.getFirst().getId());
            }

        } catch (Exception e) {
            log.error("Keycloak promotion failed", e);
            throw new KeycloakException("Failed to promote user in Keycloak");
        }

        // 4. Update Local DB
        Role adminRoleEntity = roleService.findRoleByName(RoleType.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("ROLE_ADMIN not found in DB"));

        user.getRoles().add(adminRoleEntity);
        userRepository.save(user);
        log.info("Promoted {} to ADMIN", user.getEmail());
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        updateKeycloakStatus(user.getKeycloakId(), false);

        try {
            userRepository.delete(user);
        } catch (Exception e) {
            log.error("DB delete failed, re-enabling Keycloak user to maintain consistency");
            updateKeycloakStatus(user.getKeycloakId(), true);
            throw e;
        }

        log.info("User {} soft-deleted (Disabled in Keycloak, IsDeleted=true in DB)", user.getEmail());
    }

    private void updateKeycloakStatus(UUID keycloakId, boolean enabled) {
        try {
            UserResource userResource = keycloakBot.realm(GIGTASKER).users().get(keycloakId.toString());

            UserRepresentation representation = userResource.toRepresentation();
            representation.setEnabled(enabled);

            userResource.update(representation);
        } catch (Exception e) {
            log.error("Failed to update Keycloak status", e);
            throw new KeycloakException("External Identity Error");
        }
    }

    @Transactional
    public void purgeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        try {
            keycloakBot.realm(GIGTASKER).users().get(user.getKeycloakId().toString()).remove();
        } catch (NotFoundException e) {
            log.warn("User already gone from Keycloak {}", e.getMessage());
        }

        userRepository.removeAllRoles(userId);
        userRepository.hardDeleteById(userId);

        log.info("User {} PERMANENTLY deleted.", user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAllWithRoles().stream().map(UserDTO::fromEntity).toList();
    }
}
