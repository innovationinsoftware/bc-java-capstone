package com.example.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The single WebClient that talks to the Resource Server.
 *
 * The ServletOAuth2AuthorizedClientExchangeFilterFunction looks up the
 * current user's OAuth2AuthorizedClient (stored server-side, keyed by
 * session) and attaches the access token as a Bearer header on every
 * outbound request. It also refreshes the token if it's near expiry.
 *
 * Proxy controllers just call webClient.get().uri(...) — they never see
 * tokens directly.
 */
@Configuration
public class WebClientConfig {

    public static final String AUTH_CLIENT_REGISTRATION_ID = "mock-auth";

    @Bean
    public WebClient resourceServerWebClient(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${bank.resource-server.base-url}") String baseUrl) {

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId(AUTH_CLIENT_REGISTRATION_ID);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter(oauth2Filter)
                .build();
    }
}
