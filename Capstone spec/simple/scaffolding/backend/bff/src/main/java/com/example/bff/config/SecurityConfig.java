package com.example.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * BFF security:
 *   - oauth2Login() — Spring auto-exposes /oauth2/authorization/{regId}
 *     and /login/oauth2/code/{regId} for the Authorization Code + PKCE flow.
 *   - Cookie-based session — JSESSIONID is HttpOnly + SameSite=Lax by default.
 *   - CSRF protection — required because we authenticate via cookies.
 *     The token lives in a JS-readable XSRF-TOKEN cookie; the SPA reads it
 *     and echoes it as the X-XSRF-TOKEN header on mutations.
 *
 * Endpoints exposed by Spring Security (no controller code needed):
 *   GET  /oauth2/authorization/mock-auth   start the OAuth flow
 *   GET  /login/oauth2/code/mock-auth            OAuth callback
 *   POST /logout                                 invalidate session
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Static SPA assets (when serving the built bundle from BFF in prod)
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/vite.svg").permitAll()
                // OAuth/login endpoints
                .requestMatchers("/login/**", "/oauth2/**", "/error").permitAll()
                // Everything else (i.e., /api/**, /logout) requires an authenticated session
                .anyRequest().authenticated()
            )
            .oauth2Login(Customizer.withDefaults())
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            );

        return http.build();
    }
}
