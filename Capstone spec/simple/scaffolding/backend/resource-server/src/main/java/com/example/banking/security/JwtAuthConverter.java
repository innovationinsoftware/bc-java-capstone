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
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        BankUserEntity user = users.findBySubject(subject)
                .orElseGet(() -> users.save(BankUserEntity.newCustomer(subject, email, name)));

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, user.getUserId());
    }
}
