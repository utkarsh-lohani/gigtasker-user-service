package com.gigtasker.userservice.configuration;

import org.gigtasker.gigtaskercommon.security.GigTaskerSecurity;
import org.gigtasker.gigtaskercommon.security.SecurityCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(GigTaskerSecurity.class)
public class SecurityConfig {

    @Bean
    public SecurityCustomizer userPublicEndpoints() {
        return authorize -> authorize
                // Allow Login & Register
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Allow Country/Gender Dropdowns
                .requestMatchers("/api/v1/references/**").permitAll();
    }
}