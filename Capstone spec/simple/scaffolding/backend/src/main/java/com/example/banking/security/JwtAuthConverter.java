package com.example.banking.security;

import com.example.banking.model.BankUserEntity;
import com.example.banking.repository.BankUserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Translates a validated Google JWT into a Spring authentication carrying
 * the local user's role.
 *
 * On first login (no BANK_USERS row for this 'sub'), a CUSTOMER row is
 * created. This is how new Google accounts onboard — there is no separate
 * signup endpoint.
 *
 * The `name` of the resulting authentication is the local userId, NOT
 * the Google subject. Controllers/services should use
 * authentication.getName() to identify the caller.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final BankUserRepository users;

    public JwtAuthConverter(BankUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // TODO: Implement JWT conversion
        // 1. Extract subject, email, and name claims from the JWT
        // 2. Look up the user in the BankUserRepository
        // 3. If the user doesn't exist, create a new user (with CUSTOMER role)
        // 4. Return a JwtAuthenticationToken with the correct authorities ("ROLE_" + role)
        // Note: The resulting authentication "name" must be the local userId, not the Google subject.
        
        throw new UnsupportedOperationException("TODO: Implement JWT conversion logic");
    }
}
