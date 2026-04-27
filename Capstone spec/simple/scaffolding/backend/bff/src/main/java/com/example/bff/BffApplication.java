package com.example.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Backend-for-Frontend.
 *
 * Two responsibilities:
 *   1. Drive the OAuth2 Authorization Code + PKCE flow against the Authorization
 *      Server, and hold the resulting tokens in the user's HTTP session.
 *   2. Proxy /api/v1/** calls to the Resource Server, attaching the user's
 *      bearer token via Spring's WebClient OAuth2 filter.
 *
 * The browser only ever sees an HttpOnly session cookie. Tokens never reach
 * JavaScript.
 */
@SpringBootApplication
public class BffApplication {
    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
