package com.example.banking.config;

import com.example.banking.security.JwtAuthConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthConverter jwtConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // stateless bearer-token API; CSRF is the BFF's concern
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // TODO: Allow OPTIONS requests to "/**"
                // TODO: Allow GET requests to "/health"
                // TODO: Require ROLE_ADMIN for "/api/v1/admin/**"
                // TODO: Require authentication for all other requests
                .anyRequest().permitAll() // REMOVE this line and replace with the rules above
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                // TODO: Set the JWT Authentication Converter to use the provided jwtConverter
                jwt.jwtAuthenticationConverter(jwtConverter) // Replace as needed
            ));

        return http.build();
    }
}
