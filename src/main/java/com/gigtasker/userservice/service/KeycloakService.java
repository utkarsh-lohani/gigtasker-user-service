package com.gigtasker.userservice.service;

import com.gigtasker.userservice.dto.RegistrationRequest;
import com.gigtasker.userservice.exceptions.KeycloakException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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

    // ---------------------------
    //  GET EXISTING REALM ROLES
    // ---------------------------
    public Set<String> getRealmRolesFromKeycloak() {
        return keycloakBot.realm(realm).roles().list().stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toSet());
    }

    // ---------------------------
    //  CREATE GROUPS IN KEYCLOAK
    // ---------------------------
    public void createGroupsInKeyCloak(Map<String, String> map) {

        map.forEach((groupName, groupRole) -> {
            try {
                List<GroupRepresentation> existing = keycloakBot.realm(realm).groups().groups(groupName, 0, 1);

                if (existing.isEmpty()) {
                    log.info("Creating Group: {}", groupName);

                    GroupRepresentation group = new GroupRepresentation();
                    group.setName(groupName);
                    // Note: Setting setRealmRoles here is ignored by Keycloak API during creation

                    try (Response response = keycloakBot.realm(realm).groups().add(group)) {
                        if (response.getStatus() == 201) {
                            // 1. Get the ID of the new group
                            String groupId = CreatedResponseUtil.getCreatedId(response);

                            // 2. Find the Role we want to assign
                            RoleRepresentation role = keycloakBot.realm(realm)
                                    .roles()
                                    .get(groupRole)
                                    .toRepresentation();

                            // 3. Explicitly assign role to the group
                            keycloakBot.realm(realm)
                                    .groups()
                                    .group(groupId)
                                    .roles()
                                    .realmLevel()
                                    .add(Collections.singletonList(role));

                            log.info("✅ Created Group {} and assigned role {}", groupName, groupRole);
                        } else {
                            log.warn("Keycloak returned error {} while creating group {}", response.getStatus(), groupName);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to seed group {} with Role {} - {}", groupName, groupRole, e.getMessage());
            }
        });
    }

    // ---------------------------
    //  CREATE ROLES IN KEYCLOAK
    // ---------------------------
    public void createRolesInKeyCloak(Map<String, String> rolesToCreate) {

        RolesResource rolesResource = keycloakBot.realm(realm).roles();

        Set<String> existingRoles;
        try {
            existingRoles = getRealmRolesFromKeycloak();
        } catch (Exception e) {
            log.error("❌ Failed to fetch existing roles. Check 'user-service-bot' permissions!", e);
            return;
        }

        rolesToCreate.forEach((roleName, roleDescription) -> {
            if (existingRoles.contains(roleName)) {
                log.info("Role {} already exists", roleName);
                return;
            }

            log.info("Creating Role: {}", roleName);
            RoleRepresentation role = new RoleRepresentation();
            role.setName(roleName);
            role.setDescription(roleDescription);

            try {
                rolesResource.create(role);
                log.info("✅ Created realm role: {}", roleName);
            } catch (Exception ex) {
                log.error("❌ Failed to create realm role {}: {}", roleName, ex.getMessage());
            }
        });
    }

    // --- Sync Postgres User ID to Keycloak ---
    public void updateUserAttribute(UUID keycloakId, String attributeName, String value) {
        try {
            UserResource userResource = users().get(keycloakId.toString());
            UserRepresentation userRep = userResource.toRepresentation();

            // Initialize attributes map if null
            if (userRep.getAttributes() == null) {
                userRep.setAttributes(new HashMap<>());
            }

            // Set the single attribute (e.g. internal_id = 501)
            userRep.singleAttribute(attributeName, value);

            userResource.update(userRep);
            log.info("Synced attribute '{}'='{}' for user {}", attributeName, value, keycloakId);
        } catch (Exception e) {
            log.error("Failed to update user attribute", e);
            throw new KeycloakException("Attribute sync failed");
        }
    }

}
