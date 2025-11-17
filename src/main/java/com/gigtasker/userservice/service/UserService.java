package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.UserDTO;
import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.entity.User;
import com.gigtasker.userservice.exceptions.KeycloakException;
import com.gigtasker.userservice.exceptions.ResourceNotFoundException;
import com.gigtasker.userservice.interfaces.RoleService;
import com.gigtasker.userservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
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

    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String GIGTASKER = "gigtasker";

    private static final List<String> ALLOWED_ROLES = List.of(ROLE_USER, ROLE_ADMIN);

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
        return userRepository.findById(id)
                // Use the builder in your .map() function
                .map(user -> UserDTO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .build())
                .orElse(null);
    }

    @Transactional
    public UserDTO getMe() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email = jwt.getClaimAsString("email");

        // 1. Get raw strings from token
        List<String> rawRoles = extractRolesFromToken(jwt);

        // 2. Convert strings to Role Entities (Filtering & Creating as needed)
        Set<Role> syncedRoles = processRoles(rawRoles);

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createNewUserFromJwt(jwt, syncedRoles));

        // 3. Sync DB if different
        // (We compare the Sets of names to see if they changed)
        Set<String> currentRoleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        Set<String> newRoleNames = syncedRoles.stream().map(Role::getName).collect(Collectors.toSet());

        if (!currentRoleNames.equals(newRoleNames)) {
            user.setRoles(syncedRoles);
            user = userRepository.save(user);
            log.info("Synced roles for user {}: {}", email, newRoleNames);
        }

        return UserDTO.fromEntity(user);
    }

    private User createNewUserFromJwt(Jwt jwt, Set<Role> roles) {
        User newUser = User.builder()
                .email(jwt.getClaimAsString("email"))
                .username(jwt.getClaimAsString("preferred_username"))
                .firstName(jwt.getClaimAsString("given_name"))
                .lastName(jwt.getClaimAsString("family_name"))
                .roles(roles)
                .build();
        return userRepository.save(newUser);
    }

    private Set<Role> processRoles(List<String> rawRoles) {
        Set<Role> result = new HashSet<>();

        for (String roleName : rawRoles) {

            // --- 2. THE WHITELIST CHECK ---
            // If we don't explicitly know this role, skip it.
            if (!ALLOWED_ROLES.contains(roleName)) {
                continue;
            }

            Role role = roleService.getRoleOrSave(roleName);

            result.add(role);
        }

        return result;
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
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found in local DB"));

        RealmResource realm = keycloakBot.realm(GIGTASKER);
        UsersResource usersResource = realm.users();

        try {
            // A. Find Keycloak ID
            List<UserRepresentation> kcUsers = usersResource.searchByEmail(user.getEmail(), true);
            if (kcUsers.isEmpty()) throw new ResourceNotFoundException("User missing in Keycloak");
            String keycloakUserId = kcUsers.getFirst().getId();

            // B. Add to Group "GIGTASKER_ADMIN_USERS"
            List<GroupRepresentation> groups = realm.groups().groups("GIGTASKER_ADMIN_USERS", 0, 1);
            if (groups.isEmpty()) throw new ResourceNotFoundException("Admin Group missing in Keycloak.");
            usersResource.get(keycloakUserId).joinGroup(groups.getFirst().getId());

            // C. Assign ADMIN Role
            RoleRepresentation adminRole = realm.roles().get(ROLE_ADMIN).toRepresentation();
            usersResource.get(keycloakUserId).roles().realmLevel().add(List.of(adminRole));

        } catch (Exception e) {
            log.error("Keycloak update failed", e);
            throw new KeycloakException("Failed to promote user in Keycloak");
        }

        // D. Update Local DB
        // 1. Check if they already have the role (by comparing Strings)
        boolean alreadyHasRole = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals(ROLE_ADMIN));

        if (!alreadyHasRole) {
            // 2. We must fetch the actual Role ENTITY from the database
            Role adminRoleEntity = roleService.getRoleOrSave(ROLE_ADMIN);

            // 3. Add the ENTITY, not the String
            user.getRoles().add(adminRoleEntity);

            // 4. Save the user
            userRepository.save(user);
            log.info("Local DB: Assigned {} role to {}", ROLE_ADMIN, user.getEmail());
        }
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        try {
            List<UserRepresentation> kcUsers = keycloakBot.realm(GIGTASKER).users().searchByEmail(user.getEmail(), true);
            if (!kcUsers.isEmpty()) {
                keycloakBot.realm(GIGTASKER).users().get(kcUsers.getFirst().getId()).remove();
            }
        } catch (Exception e) {
            log.error("Keycloak delete failed", e);
            throw new KeycloakException("Failed to delete user from Keycloak");
        }
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAllWithRoles().stream().map(UserDTO::fromEntity).toList();
    }
}
