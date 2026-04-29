package com.example.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient that forwards the OIDC id_token as a Bearer header to the Resource Server.
 *
 * Both mock-auth (Spring Authorization Server) and Google issue OIDC id_tokens that
 * are signed JWTs. The Resource Server validates JWTs from both issuers using a
 * multi-issuer JwtDecoder (see JwtDecoderConfig in the resource-server module).
 *
 * Why id_token instead of access_token?
 *   - Google's access_token is an opaque token (e.g. ya29.xxx), NOT a JWT.
 *     The Resource Server cannot validate it via its JwtDecoder.
 *   - Both Google and mock-auth's id_tokens ARE verifiable JWTs.
 *   - mock-auth's TokenCustomizer adds the "role" claim to all JWTs (including
 *     id_token), so role-based access control works correctly for mock-auth users.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient resourceServerWebClient(
            @Value("${bank.resource-server.base-url}") String baseUrl) {

        /*
         * TODO (Day 2 — Step 6): Build a WebClient that forwards the user's OIDC
         * id_token as a Bearer Authorization header to the Resource Server.
         *
         * Why id_token instead of access_token?
         *   - Google's access_token is an OPAQUE token (ya29.xxx), not a JWT.
         *     The Resource Server cannot validate it via its JwtDecoder.
         *   - Both Google and mock-auth issue id_tokens as verifiable JWTs.
         *   - mock-auth's TokenCustomizer adds the "role" claim to the id_token.
         *
         * Steps:
         * 1. Create an ExchangeFilterFunction named oidcBearerFilter:
         *      (request, next) -> {
         *          // a) Get the current Security context:
         *          Authentication auth = SecurityContextHolder.getContext().getAuthentication();
         *          // b) Check if auth is an OAuth2AuthenticationToken
         *          //    AND its principal is an OidcUser:
         *          if (auth instanceof OAuth2AuthenticationToken oauthToken
         *                  && oauthToken.getPrincipal() instanceof OidcUser oidcUser) {
         *              // c) Extract the raw JWT string:
         *              String idToken = oidcUser.getIdToken().getTokenValue();
         *              // d) Rebuild the request with the Authorization header:
         *              ClientRequest withBearer = ClientRequest.from(request)
         *                  .headers(h -> h.setBearerAuth(idToken))
         *                  .build();
         *              return next.exchange(withBearer);
         *          }
         *          // e) No OIDC session — pass through unchanged:
         *          return next.exchange(request);
         *      }
         *
         * 2. Build and return the WebClient:
         *      WebClient.builder()
         *          .baseUrl(baseUrl)
         *          .filter(oidcBearerFilter)
         *          .build()
         */
        throw new UnsupportedOperationException("WebClientConfig.resourceServerWebClient(): not yet implemented");
    }
}

