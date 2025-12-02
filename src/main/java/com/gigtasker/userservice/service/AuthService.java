package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.LoginRequest;
import com.gigtasker.userservice.dto.RefreshRequest;
import com.gigtasker.userservice.dto.RegistrationRequest;
import com.gigtasker.userservice.dto.UserDTO;
import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.entity.User;
import com.gigtasker.userservice.enums.RoleType;
import com.gigtasker.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final Keycloak keycloakBot;
    private final KeycloakService keycloakService;

    @Value("${keycloak.bot.server-url}")
    private String keycloakUrl;

    @Value("${keycloak.bot.realm}")
    private String realm;

    private final RestClient restClient = RestClient.builder().build();

    @Transactional
    public UserDTO register(RegistrationRequest req) {
        // 1. Create Keycloak user representation
        UserRepresentation kcUser = keycloakService.getKeyCloakUserRepresentationObject(req);

        // 2. Create in Keycloak
        UUID keycloakId = keycloakService.createUser(kcUser);

        // 3. Set password
        keycloakService.setPassword(keycloakId, req.password());

        // 4. Assign group
        keycloakService.addUserToGroup(keycloakId, "GIGTASKER_USERS");

        // 5. Save to PostgresSQL
        Role userRole = roleService.findRoleByName(RoleType.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("ROLE_USER not found"));

        User newUser = User.builder()
                .keycloakId(keycloakId)
                .username(req.username())
                .email(req.email())
                .firstName(req.firstName())
                .lastName(req.lastName())
                .dateOfBirth(req.dateOfBirth())
                .gender(req.gender())
                .country(req.country())
                .roles(Set.of(userRole))
                .build();

        return UserDTO.fromEntity(userRepository.save(newUser));
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
