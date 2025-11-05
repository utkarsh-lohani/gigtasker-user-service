package com.gigtasker.userservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults; // Import this

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // We'll still add the basic .cors() to play nice
                .cors(withDefaults())

                .authorizeHttpRequests(authorize -> authorize
                        // We explicitly allow the OPTIONS preflight "ping"
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // We demand a token for EVERYTHING else
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                );

        return http.build();
    }
}