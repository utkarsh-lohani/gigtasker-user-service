package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.LoginRequest;
import com.gigtasker.userservice.dto.RefreshRequest;
import com.gigtasker.userservice.dto.RegistrationRequest;
import com.gigtasker.userservice.dto.UserDTO;
import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.entity.User;
import com.gigtasker.userservice.enums.RoleType;
import com.gigtasker.userservice.exceptions.KeycloakException;
import com.gigtasker.userservice.repository.UserRepository;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.security.auth.login.LoginException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final Keycloak keycloakBot;

    @Value("${keycloak.bot.server-url}")
    private String keycloakUrl;

    @Value("${keycloak.bot.realm}")
    private String realm;

    private final RestClient restClient = RestClient.builder().build();

    @Transactional
    public UserDTO register(RegistrationRequest registrationRequest) {

        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setUsername(registrationRequest.username());
        kcUser.setEmail(registrationRequest.email());
        kcUser.setFirstName(registrationRequest.firstName());
        kcUser.setLastName(registrationRequest.lastName());
        kcUser.setEnabled(true);

        UsersResource usersResource = keycloakBot.realm(realm).users();
        try (Response response = usersResource.create(kcUser)) {
            if (response.getStatus() != 201) {
                throw new KeycloakException("Keycloak error: " + response.getStatusInfo());
            }
            String userId = CreatedResponseUtil.getCreatedId(response);
            UUID keycloakId = UUID.fromString(userId);

            // 3. Set Password
            CredentialRepresentation cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setValue(registrationRequest.password());
            cred.setTemporary(false);
            usersResource.get(userId).resetPassword(cred);

            // 4. Save to Postgres
            Role userRole = roleService.findRoleByName(RoleType.ROLE_USER).orElseThrow(() -> new NoSuchElementException("Role Not Found"));

            User newUser = User.builder()
                    .keycloakId(keycloakId)
                    .username(registrationRequest.username())
                    .email(registrationRequest.email())
                    .firstName(registrationRequest.firstName())
                    .lastName(registrationRequest.lastName())
                    .dateOfBirth(registrationRequest.dateOfBirth())
                    .gender(registrationRequest.gender())
                    .country(registrationRequest.country())
                    .roles(new HashSet<>(Set.of(userRole)))
                    .build();

            return UserDTO.fromEntity(userRepository.save(newUser));
        }
    }

    public Map<String, Object> login(LoginRequest req) throws LoginException {

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", "gigtasker-angular");
        body.add("username", req.username());
        body.add("password", req.password());
        body.add("grant_type", "password");
        // scope=offline_access is required to get a refresh token
        body.add("scope", "openid profile email offline_access");

        return callKeycloakTokenEndpoint(body);
    }

    public Map<String, Object> refresh(RefreshRequest req) throws LoginException {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", "gigtasker-angular");
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", req.refreshToken());

        return callKeycloakTokenEndpoint(body);
    }

    private Map<String, Object> callKeycloakTokenEndpoint(MultiValueMap<String, String> formData) throws LoginException {
        String tokenEndpoint = String.format(
                "%s/realms/%s/protocol/openid-connect/token",
                keycloakUrl, realm
        );

        try {
            return restClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception ex) {
            throw new LoginException("Invalid Login Credentials " + ex.getMessage());
        }
    }
}
