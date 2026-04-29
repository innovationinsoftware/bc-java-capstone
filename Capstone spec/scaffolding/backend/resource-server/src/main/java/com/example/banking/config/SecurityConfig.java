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
 * Resource Server security — stateless JWT validation.
 *
 * No sessions, no CSRF, no CORS. The Resource Server only receives
 * requests from the BFF (server-to-server), with a Bearer JWT attached
 * by the BFF's WebClient OAuth2 filter.
 *
 * Two-layer RBAC:
 *   1. URL filter: /api/v1/admin/** requires ROLE_ADMIN
 *   2. Method security: @PreAuthorize on controller methods (enabled)
 *
 * JwtAuthConverter maps the JWT sub claim to a local BANK_USERS row
 * and assigns the appropriate role from the token's custom "role" claim.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthConverter jwtAuthConverter) throws Exception {
        /*
         * TODO (Day 2 — Step 1): Configure the Resource Server security filter chain.
         *
         * The Resource Server is stateless: no sessions, no CSRF, no browser-facing login.
         * The BFF talks to it server-to-server, attaching a Bearer JWT on every request.
         *
         * Configure the following (in order):
         *
         * 1. CORS — disable entirely. Only the BFF (server-to-server) calls this service.
         *      http.cors(cors -> cors.disable())
         *
         * 2. CSRF — disable entirely. Stateless JWT — no cookies, no CSRF attack surface.
         *      http.csrf(csrf -> csrf.disable())
         *
         * 3. Session management — stateless. Never create an HttpSession.
         *      http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
         *
         * 4. Authorize HTTP requests:
         *      a) Allow HTTP OPTIONS to any path (required for CORS pre-flight, even if CORS is
         *         disabled; some clients still send OPTIONS).
         *      b) Allow GET /health without authentication.
         *      c) Require ROLE_ADMIN for /api/v1/admin/**
         *      d) All other requests require any authenticated user.
         *
         * 5. OAuth2 Resource Server — validate Bearer JWTs using our custom converter:
         *      http.oauth2ResourceServer(oauth2 -> oauth2
         *          .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
         *
         * Finally return http.build().
         *
         * Note: @EnableMethodSecurity on this class enables @PreAuthorize on controller methods
         * as a SECOND layer of RBAC (defence in depth).
         */
        throw new UnsupportedOperationException("SecurityConfig.securityFilterChain(): not yet implemented");
    }
}
