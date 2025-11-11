package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.UserDTO;
import com.gigtasker.userservice.entity.User;
import com.gigtasker.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserService {

    // Using Spring Transactional because it's much powerful
    // readonly = true in Transactional indicates JPA/Hibernate that to not bother dirty checking those objects as I'm not saving anything
    // Or in other word telling DB that it's just a select query

    private final UserRepository userRepository;
    private final Keycloak keycloakBot;

    public UserService(UserRepository userRepository, @Qualifier("keycloakBot") Keycloak keycloakBot) {
        this.userRepository = userRepository;
        this.keycloakBot = keycloakBot;
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

    public UserDTO getMe() {
        // 1. Get the authentication object from Spring Security
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        // 2. Get the JWT (the "ID card") from the authentication
        Jwt jwt = (Jwt) authentication.getPrincipal();

        // 3. Get the email from the "ID card"
        String email = jwt.getClaimAsString("email");

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            return UserDTO.fromEntity(userOptional.get());
        } else {
            return createNewUserFromJwt(jwt);
        }
    }

    private UserDTO createNewUserFromJwt(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        // "preferred_username" is the default claim for the username
        String username = jwt.getClaimAsString("preferred_username");

        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        User newUser = User.builder()
                .email(email)
                .username(username)
                .firstName(firstName) // <-- Add this
                .lastName(lastName)   // <-- Add this
                .build();

        User savedUser = userRepository.save(newUser);

        return UserDTO.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> findUsersByIds(List<Long> ids) {
        return userRepository.findByIdIn(ids)
                .stream()
                .map(UserDTO::fromEntity).toList();
    }

    @Transactional
    public void promoteUserToAdmin(Long userId) {
        // 1. Find the user in *our* DB
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceAccessException("User not found in local DB"));

        // 2. Find the "ADMIN" role in Keycloak
        RealmResource gigtaskerRealm = keycloakBot.realm("gigtasker");
        RoleRepresentation adminRole = gigtaskerRealm.roles().get("ADMIN").toRepresentation();

        // 3. Find the user *in Keycloak* using their email
        List<UserRepresentation> keycloakUsers = gigtaskerRealm.users().searchByEmail(user.getEmail(), true);
        if (keycloakUsers.isEmpty()) {
            throw new ResourceAccessException("User not found in Keycloak");
        }
        String keycloakUserId = keycloakUsers.getFirst().getId();

        // 4. Assign the role!
        gigtaskerRealm.users().get(keycloakUserId).roles().realmLevel().add(List.of(adminRole));

        UserService.log.info("User {} promoted to ADMIN", user.getEmail());
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserDTO::fromEntity).toList();
    }
}
