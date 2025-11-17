package com.gigtasker.userservice.configuration;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

    @Bean
    public Keycloak keycloakAdmin(
            @Value("${keycloak.admin.server-url}") String serverUrl,
            @Value("${keycloak.admin.realm}") String realm,
            @Value("${keycloak.admin.username}") String username,
            @Value("${keycloak.admin.password}") String password,
            @Value("${keycloak.admin.client-id}") String clientId
    ) {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl).realm(realm)
                .grantType(OAuth2Constants.PASSWORD)
                .clientId(clientId).username(username).password(password)
                .build();
    }

    @Bean
    public Keycloak keycloakBot(
            @Value("${keycloak.bot.server-url}") String serverUrl,
            @Value("${keycloak.bot.realm}") String realm,
            @Value("${keycloak.bot.client-id}") String clientId,
            @Value("${keycloak.bot.client-secret}") String clientSecret
    ) {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl).realm(realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(clientId).clientSecret(clientSecret)
                .build();
    }
}
