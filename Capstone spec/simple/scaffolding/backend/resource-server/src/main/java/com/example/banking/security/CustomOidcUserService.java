package com.example.banking.security;

import com.example.banking.model.BankUserEntity;
import com.example.banking.repository.BankUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Custom OIDC user service — handles Google login.
 *
 * Google is an OpenID Connect (OIDC) provider, so Spring uses OidcUserService
 * (not the plain OAuth2UserService). This class extends OidcUserService to
 * hook into that flow.
 *
 * On first login, a CUSTOMER row is created in BANK_USERS. On subsequent
 * logins, the existing row is returned. This is how new Google accounts
 * onboard — there is no separate signup endpoint.
 *
 * The returned OidcUser's getName() returns the local userId (not the Google
 * subject) so that authentication.getName() gives the same value that
 * account/transaction queries expect.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private final BankUserRepository users;

    public CustomOidcUserService(BankUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // Let Spring fetch and parse the Google OIDC user-info response first.
        OidcUser oidcUser = super.loadUser(userRequest);

        String subject = oidcUser.getSubject();
        String email   = oidcUser.getEmail();
        String name    = oidcUser.getFullName();

        // Upsert: create BANK_USERS row on first login, load on subsequent logins.
        BankUserEntity localUser = users.findBySubject(subject)
                .orElseGet(() -> users.save(BankUserEntity.newCustomer(subject, email, name)));

        // Build authorities from the local role stored in the database.
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + localUser.getRole().name()));

        // Wrap the Google OidcUser with a delegate that overrides getName()
        // to return the local userId. This way authentication.getName()
        // returns the userId everywhere in the application.
        return new LocalOidcUser(oidcUser, authorities, localUser.getUserId());
    }

    /**
     * Wraps an OidcUser and overrides getName() to return the local userId
     * instead of the Google subject. All other methods delegate to the
     * original OidcUser.
     */
    private static class LocalOidcUser implements OidcUser {

        private final OidcUser delegate;
        private final Collection<? extends GrantedAuthority> authorities;
        private final String localUserId;

        LocalOidcUser(OidcUser delegate,
                      Collection<? extends GrantedAuthority> authorities,
                      String localUserId) {
            this.delegate = delegate;
            this.authorities = authorities;
            this.localUserId = localUserId;
        }

        /** Returns the local userId — this is what authentication.getName() returns. */
        @Override
        public String getName() { return localUserId; }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

        // ---- delegate everything else to the original Google OidcUser ----

        @Override
        public Map<String, Object> getAttributes() { return delegate.getAttributes(); }

        @Override
        public Map<String, Object> getClaims() { return delegate.getClaims(); }

        @Override
        public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }

        @Override
        public OidcIdToken getIdToken() { return delegate.getIdToken(); }
    }
}
