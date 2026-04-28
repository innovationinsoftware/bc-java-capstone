package com.example.banking.config;

import com.example.banking.security.CustomOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Spring Security configured as a BFF (Backend-for-Frontend).
 *
 * The backend is the OAuth2 client — it performs the Google auth code
 * exchange server-side and creates an HttpOnly JSESSIONID session cookie.
 * The React SPA never sees a token. This eliminates the risk of token
 * theft via XSS.
 *
 * CSRF protection is re-enabled because the browser automatically sends
 * session cookies with every request (unlike Bearer tokens). Spring sets
 * an XSRF-TOKEN cookie that JS can read; the SPA echoes it back as the
 * X-XSRF-TOKEN header on every mutating request.
 *
 * Sessions are stateful — the session store is in-memory (sufficient for
 * the capstone). For production, use Spring Session with JDBC or Redis.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CustomOidcUserService oidcUserService) throws Exception {

        // Use the deferred CSRF token approach introduced in Spring Security 6.
        // CookieCsrfTokenRepository.withHttpOnlyFalse() makes the XSRF-TOKEN
        // cookie readable by JavaScript so the React SPA can echo it back.
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null); // deferred token loading

        http
            .cors(cors -> cors.configure(http))  // picks up CorsConfig bean
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                // OAuth2 login endpoints must be open
                .requestMatchers("/login", "/login/**", "/oauth2/**", "/logout").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // Return 401 JSON (not a redirect to /login) for unauthenticated API calls.
            // The SPA handles the 401 by redirecting the user to /login itself.
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getRequestURI().startsWith("/api/")
                )
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(u -> u.oidcUserService(oidcUserService))
                // After successful login, redirect to the SPA root.
                // The SPA reads the session cookie on its next /api call.
                .defaultSuccessUrl("http://localhost:5173/", true)
                .failureUrl("http://localhost:5173/login?error=true")
            )
            // POST /logout invalidates the session and clears the cookie.
            .logout(logout -> logout
                .logoutSuccessUrl("http://localhost:5173/login")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }
}
