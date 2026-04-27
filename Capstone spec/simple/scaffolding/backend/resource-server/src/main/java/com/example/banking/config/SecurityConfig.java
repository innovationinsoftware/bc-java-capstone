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

/**
 * Spring Security configured as an OAuth2 Resource Server.
 *
 * The Resource Server only ever sees server-to-server traffic from the BFF.
 * It validates JWTs (issuer, signature, expiry, audience — see JwtDecoderConfig)
 * and enforces role-based authorization. It does NOT need CORS — only the BFF
 * talks to it, and that's a server-to-server call.
 *
 * Two-layer authorization:
 *   - URL filter: /api/v1/admin/** requires ROLE_ADMIN
 *   - Method security: @PreAuthorize on admin controller methods (see UserController)
 *   - Service-layer ownership: non-owned accounts return 404 (see AccountService)
 */
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
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtConverter)
            ));

        return http.build();
    }
}
