package com.example.banking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Adds the audience (aud) check to the default decoder. Spring's default
 * already validates issuer, signature, and expiry.
 *
 * The JWT comes from the Authorization Server via the BFF. Its 'aud' claim
 * is the BFF's client_id ('spa-client' in the mock auth server). Without
 * this check, ANY token issued by our auth server for any client would be
 * accepted here.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${bank.expected-audience}") String expectedAudience) {

        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audience = token ->
                token.getAudience() != null && token.getAudience().contains(expectedAudience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                              new OAuth2Error("invalid_audience",
                                  "JWT audience does not match expected", null));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audience));
        return decoder;
    }
}
