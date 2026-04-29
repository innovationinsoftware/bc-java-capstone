package com.example.bff.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * BFF Security — OAuth2 client, session-based, CSRF enabled.
 *
 * The BFF is the only service the browser talks to. It authenticates
 * users via oauth2Login (redirecting to the auth server), stores the
 * resulting tokens in the HTTP session, and gates all /api/** requests
 * behind an authenticated session.
 *
 * For unauthenticated /api/** requests, we return 401 instead of 302.
 * This prevents CORS errors on cross-origin redirects and lets the SPA
 * handle the redirect to the login page manually.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        /*
         * TODO (Day 2 — Step 5): Configure the BFF (Backend-for-Frontend) security chain.
         *
         * The BFF is session-based and browser-facing. Unlike the Resource Server,
         * it DOES use sessions and DOES enforce CSRF protection.
         *
         * 1. Authorize HTTP requests:
         *      a) Permit: "/", "/index.html", "/assets/**", "/favicon.ico"  (SPA static files)
         *      b) Permit: "/login/**", "/oauth2/**"  (Spring's OAuth2 login endpoints)
         *      c) Permit: "/health", "/error"  (avoid redirect loops on error page)
         *      d) All other requests must be authenticated.
         *
         * 2. Exception handling — for /api/** requests return 401 instead of redirecting
         *    to the login page (the SPA handles redirects itself):
         *      http.exceptionHandling(ex -> ex
         *          .defaultAuthenticationEntryPointFor(
         *              new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
         *              new AntPathRequestMatcher("/api/**")
         *          )
         *      )
         *
         * 3. OAuth2 Login:
         *      http.oauth2Login(oauth2 -> oauth2
         *          .defaultSuccessUrl(frontendBaseUrl + "/", true)
         *          .authorizationEndpoint(authz -> authz
         *              .authorizationRequestRepository(new CookieOAuth2AuthorizationRequestRepository())
         *          )
         *      )
         *    The CookieOAuth2AuthorizationRequestRepository stores PKCE state in a
         *    cookie instead of the server session, preventing state-mismatch errors.
         *
         * 4. Logout:
         *      http.logout(logout -> logout
         *          .logoutSuccessUrl(frontendBaseUrl + "/")
         *          .invalidateHttpSession(true)
         *          .deleteCookies("JSESSIONID")
         *      )
         *
         * 5. CSRF — use cookie-based token so the SPA can read it:
         *      http.csrf(csrf -> csrf
         *          .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
         *          .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
         *      )
         *
         * 6. Add the CSRF eager-load filter below (already provided — do not modify it).
         *    Spring Security 6 defers CSRF token loading; without this filter the
         *    XSRF-TOKEN cookie never appears on GET requests and logout always returns 403.
         */
        throw new UnsupportedOperationException("SecurityConfig.securityFilterChain(): not yet implemented");

        // --- Provided helper: add this AFTER you implement steps 1-5 above ---
        // .addFilterAfter(new OncePerRequestFilter() {
        //     @Override
        //     protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
        //                                     FilterChain chain) throws ServletException, IOException {
        //         CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
        //         if (token != null) token.getToken(); // forces cookie write
        //         chain.doFilter(req, res);
        //     }
        // }, CsrfFilter.class);
        //
        // return http.build();
    }
}
