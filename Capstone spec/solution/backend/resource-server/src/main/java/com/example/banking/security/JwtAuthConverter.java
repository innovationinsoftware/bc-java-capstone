package com.example.banking.security;

import com.example.banking.model.BankUserEntity;
import com.example.banking.model.UserRole;
import com.example.banking.repository.BankUserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts a validated JWT into a Spring Security authentication.
 *
 * On first login, creates a BANK_USERS row from the JWT's sub claim.
 * On subsequent logins, reuses the existing row.
 *
 * The role is read from a custom "role" claim in the JWT (set by the
 * mock auth server's token customizer). If absent (e.g., Google tokens),
 * defaults to CUSTOMER.
 *
 * authentication.getName() returns the local userId (not the JWT sub).
 * This is the same value that all service methods use for ownership checks.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final BankUserRepository users;

    public JwtAuthConverter(BankUserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String subject = jwt.getSubject();
        String rawEmail = jwt.getClaimAsString("email");
        String email = rawEmail != null ? rawEmail : subject + "@mock.local";
        String rawName = jwt.getClaimAsString("name");
        String name = rawName != null ? rawName : subject;

        // Determine role from JWT claim; default to CUSTOMER if not present
        String roleClaim = jwt.getClaimAsString("role");
        UserRole role = "ADMIN".equalsIgnoreCase(roleClaim) ? UserRole.ADMIN : UserRole.CUSTOMER;

        // Upsert: create on first login, find on subsequent logins.
        BankUserEntity localUser = users.findBySubject(subject)
                .orElseGet(() -> users.save(
                        BankUserEntity.newUser(subject, email, name, role)));

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + localUser.getRole().name()));

        // Set the principal name to the local userId
        return new JwtAuthenticationToken(jwt, authorities, localUser.getUserId());
    }
}
