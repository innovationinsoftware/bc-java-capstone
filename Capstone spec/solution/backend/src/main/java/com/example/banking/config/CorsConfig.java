package com.example.banking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS for the React SPA running on a different port (5173) than the
 * backend (8081).
 *
 * In the BFF model, credentials (cookies) are sent cross-origin, so
 * allowCredentials must be true and the origin must be explicit — never "*".
 *
 * X-XSRF-TOKEN is added to allowedHeaders so the CSRF token echoed by
 * the SPA on mutating requests is not blocked by the browser's CORS
 * preflight check.
 */
@Configuration
public class CorsConfig {

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${bank.cors.allowed-origin}") String allowedOrigin) {

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(allowedOrigin));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        cfg.setExposedHeaders(List.of("Location"));
        cfg.setAllowCredentials(true);  // required for cookie-based auth
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        source.registerCorsConfiguration("/health", cfg);
        source.registerCorsConfiguration("/logout", cfg);  // sign-out is POST /logout
        return source;
    }
}
