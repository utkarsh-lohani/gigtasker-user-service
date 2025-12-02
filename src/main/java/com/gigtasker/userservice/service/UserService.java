package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.UserDTO;
import com.gigtasker.userservice.dto.UserUpdateDTO;
import com.gigtasker.userservice.entity.Country;
import com.gigtasker.userservice.entity.Gender;
import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.entity.User;
import com.gigtasker.userservice.enums.RoleType;
import com.gigtasker.userservice.exceptions.KeycloakException;
import com.gigtasker.userservice.exceptions.ResourceNotFoundException;
import com.gigtasker.userservice.repository.CountryRepository;
import com.gigtasker.userservice.repository.GenderRepository;
import com.gigtasker.userservice.repository.UserRepository;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final Keycloak keycloakBot;
    private final RoleService roleService;
    private final StorageService storageService;
    private final CountryRepository countryRepository;
    private final GenderRepository genderRepository;
    private final KeycloakService keycloakService;

    private static final String GIGTASKER = "gigtasker";

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    public UserService(UserRepository userRepository, StorageService storageService, KeycloakService keycloakService,
                       @Qualifier("keycloakBot") Keycloak keycloakBot, RoleService roleService,
                       CountryRepository countryRepository, GenderRepository genderRepository) {
        this.userRepository = userRepository;
        this.keycloakBot = keycloakBot;
        this.roleService = roleService;
        this.storageService = storageService;
        this.countryRepository = countryRepository;
        this.genderRepository = genderRepository;
        this.keycloakService = keycloakService;
    }

    private static final String USER_NOT_FOUND = "User not found";

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
        Jwt jwt = (Jwt) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        assert jwt != null;
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
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));

        String keycloakUserId = keycloakService.findUserIdByEmail(user.getEmail());
        if (keycloakUserId == null) {
            throw new ResourceNotFoundException("User missing in Keycloak");
        }

        // assign group
        keycloakService.addUserToGroup(UUID.fromString(keycloakUserId), "GIGTASKER_ADMIN_USERS");

        // add ROLE_ADMIN locally
        Role adminRole = roleService.findRoleByName(RoleType.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("ROLE_ADMIN missing"));

        user.getRoles().add(adminRole);
        userRepository.save(user);

        log.info("Promoted {} to ADMIN", user.getEmail());
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));

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
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));

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

    @Transactional
    public UserDTO updateUser(UUID keycloakId, UserUpdateDTO updates) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));

        return performUpdate(user, updates);
    }

    private UserDTO performUpdate(User user, UserUpdateDTO updates) {
        // Apply updates (Partial update logic)
        if (updates.firstName() != null) user.setFirstName(updates.firstName());
        if (updates.lastName() != null) user.setLastName(updates.lastName());
        if (updates.dateOfBirth() != null) user.setDateOfBirth(updates.dateOfBirth());

        // Handle Foreign Keys
        if (updates.genderId() != null) {
            Gender gender = genderRepository.findById(updates.genderId())
                    .orElseThrow(() -> new RuntimeException("Invalid Gender ID"));
            user.setGender(gender);
        }

        if (updates.countryId() != null) {
            Country country = countryRepository.findById(updates.countryId())
                    .orElseThrow(() -> new RuntimeException("Invalid Country ID"));
            user.setCountry(country);
        }

        User savedUser = userRepository.save(user);
        log.info("Updated profile for user: {}", user.getEmail());
        return UserDTO.fromEntity(savedUser);
    }

    @Transactional
    public UserDTO updateProfileImage(UUID keycloakId, MultipartFile file) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));

        // Upload to MinIO -> Returns key (e.g., "avatars/uuid.jpg")
        String imageKey = storageService.uploadProfileImage(keycloakId, file);

        // Update DB
        user.setProfileImageUrl(imageKey);

        return UserDTO.fromEntity(userRepository.save(user));
    }
}
