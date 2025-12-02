package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.RegistrationRequest;
import com.gigtasker.userservice.exceptions.KeycloakException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakService {
    private final Keycloak keycloakBot;

    @Value("${keycloak.bot.realm}")
    private String realm;

    private RealmResource realm() {
        return keycloakBot.realm(realm);
    }

    private UsersResource users() {
        return realm().users();
    }

    public UserRepresentation getKeyCloakUserRepresentationObject(RegistrationRequest req) {
        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setUsername(req.username());
        kcUser.setEmail(req.email());
        kcUser.setFirstName(req.firstName());
        kcUser.setLastName(req.lastName());
        kcUser.setEnabled(true);

        return kcUser;
    }

    // ---------------------------
    //   CREATE USER IN KEYCLOAK
    // ---------------------------
    public UUID createUser(UserRepresentation userRep) {

        try (Response response = users().create(userRep)) {
            if (response.getStatus() != 201) {
                throw new KeycloakException("Keycloak error: " + response.getStatusInfo());
            }

            String userId = CreatedResponseUtil.getCreatedId(response);
            return UUID.fromString(userId);
        }
    }

    // ---------------------------
    //      SET USER PASSWORD
    // ---------------------------
    public void setPassword(UUID keycloakId, String rawPassword) {

        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(rawPassword);
        cred.setTemporary(false);

        users().get(keycloakId.toString()).resetPassword(cred);
    }

    // ---------------------------
    //      ASSIGN USER GROUP
    // ---------------------------
    public void addUserToGroup(UUID keycloakId, String groupName) {
        try {

            List<GroupRepresentation> groups = realm()
                    .groups()
                    .groups(groupName, 0, 1);

            if (groups.isEmpty()) {
                log.warn("Group {} not found in Keycloak", groupName);
                return;
            }

            String groupId = groups.getFirst().getId();
            users().get(keycloakId.toString()).joinGroup(groupId);
            log.info("User {} added to group {}", keycloakId, groupName);

        } catch (Exception e) {
            log.error("Failed to assign group {}", groupName, e);
            throw new KeycloakException("Group assignment failed");
        }
    }

    // ---------------------------
    //  FIND USER BY EMAIL
    // ---------------------------
    public String findUserIdByEmail(String email) {
        List<UserRepresentation> result = users().searchByEmail(email, true);
        if (result.isEmpty()) return null;
        return result.getFirst().getId();
    }

}
