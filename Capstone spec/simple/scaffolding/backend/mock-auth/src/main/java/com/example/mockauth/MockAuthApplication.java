package com.example.mockauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Tiny Spring Authorization Server used during development.
 *
 * It stands in for whatever IdP a real bank would use (Okta, Auth0, Azure AD,
 * Keycloak). The protocol surface is the same — only this one is conveniently
 * runnable on localhost with no setup.
 *
 * Pre-registered users (in-memory):
 *   - alice / alice  (CUSTOMER)
 *   - admin / admin  (ADMIN, but the role is enforced by the Resource Server,
 *                     not by this server — we set it in BANK_USERS)
 *
 * Pre-registered client:
 *   - spa-client / spa-secret
 *     redirect-uri: http://localhost:8080/login/oauth2/code/mock-auth
 *
 * Listens on http://localhost:9000.
 */
@SpringBootApplication
public class MockAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockAuthApplication.class, args);
    }
}
