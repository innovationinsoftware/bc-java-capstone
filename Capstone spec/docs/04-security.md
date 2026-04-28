# 04 — Security

This is the section the rubric weights most heavily after backend implementation. Read it twice.

## Identity model

- **Identity provider:** Google. There is no local username/password store, no separate authorization server. You will register an OAuth2 client in Google Cloud Console.
- **The React SPA is a public client.** It cannot keep a secret (Module 9 slides covered why). It uses **Authorization Code + PKCE**.
- **The Spring Boot service is a Resource Server.** It validates Google-issued JWTs but does not mint them. It never sees the user's password and never speaks to Google's token endpoint directly.
- **Roles** (`CUSTOMER`, `ADMIN`) are stored locally in `BANK_USERS.ROLE`. Google does not know about them. Role lookup happens once per request, after JWT validation, in a Spring Security `JwtAuthenticationConverter`.

## Setup: Google Cloud Console

Each team registers its own OAuth client.

1. Go to https://console.cloud.google.com → "APIs & Services" → "Credentials".
2. Create an **OAuth 2.0 Client ID**, type **Web application**.
3. **Authorized JavaScript origins:** `http://localhost:5173`.
4. **Authorized redirect URIs:** `http://localhost:5173/callback`.
5. Capture the **Client ID** (no client secret needed for PKCE).
6. The OAuth consent screen needs to be configured. For the capstone, "External" + "Testing" with your team members added as test users is enough — you do not need to publish the app.

The Client ID is **not** a secret in the cryptographic sense (it's in the SPA bundle), but treat it as configuration: pass it via environment variable, not hard-coded.

## Login flow (end-to-end)

1. User visits `http://localhost:5173/`. App detects no token in session storage, redirects to `/login`.
2. `/login` shows a "Sign in with Google" button. Clicking it triggers the OIDC client library (`oidc-client-ts` or similar) to:
   - Generate a random `code_verifier`, derive `code_challenge = SHA256(verifier)` (BASE64URL-encoded).
   - Stash `code_verifier` in `sessionStorage`.
   - Redirect the browser to Google's authorization endpoint with `response_type=code`, `client_id`, `redirect_uri=http://localhost:5173/callback`, `code_challenge`, `code_challenge_method=S256`, `scope=openid email profile`, and a random `state`.
3. User authenticates on Google's page and consents.
4. Google redirects back to `/callback?code=...&state=...`.
5. The `/callback` route handler:
   - Verifies `state`.
   - POSTs to Google's token endpoint with `grant_type=authorization_code`, the `code`, the `code_verifier`, `client_id`, and `redirect_uri`.
   - Receives an ID token (JWT) and an access token.
   - Stores the access token in `sessionStorage` (or in-memory; **never** in `localStorage` — see "Token storage" below).
   - Redirects to `/`.
6. From now on, every API call attaches `Authorization: Bearer <access_token>`.
7. On token expiry (1 hour for Google), API calls return 401. The SPA handles 401 by redirecting to `/login` and starting fresh. Refresh-token rotation is **out of scope** for the capstone.

The OIDC client library does steps 2, 4, and 5 for you. Use it. Do not roll your own PKCE.

## Spring Boot resource server

This is the Module 3 / Lab 5 pattern, adapted for Google.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwks-uri: https://www.googleapis.com/oauth2/v3/certs
          issuer-uri: https://accounts.google.com
          audiences: ${GOOGLE_CLIENT_ID}
```

`SecurityConfig.java`:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtConverter) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtConverter)
            ));
        return http.build();
    }
}
```

### Validating Google's tokens

Spring Security validates `iss`, `exp`, and signature automatically once `issuer-uri` is set. The **`audiences`** check is what proves the token was minted for *your* client and not some other Google relying party — without it any Google ID token would pass. Configure it via `audiences` (Spring Boot 3.4+) or by registering a custom `OAuth2TokenValidator<Jwt>` bean.

### Mapping Google `sub` to a local user with a role

Google's JWT does not carry your `CUSTOMER`/`ADMIN` role. A `JwtAuthenticationConverter` looks up the local user on every request and injects `ROLE_CUSTOMER` or `ROLE_ADMIN` as a `GrantedAuthority`. Cache the lookup if it shows up in profiling — but for class scale, a per-request DB hit is fine.

```java
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final BankUserRepository users;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String subject = jwt.getSubject();
        BankUserEntity user = users.findBySubject(subject)
            .orElseGet(() -> users.save(BankUserEntity.firstLoginFrom(jwt)));
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        return new JwtAuthenticationToken(jwt, authorities, user.getUserId());
    }
}
```

This is also where the **first-login user creation** happens — it makes `GET /api/v1/users/me` self-onboarding without a separate signup endpoint.

## Authorization layers

You need authorisation enforced at **two** layers (rubric: "Exceeds — enforced at multiple layers"):

1. **URL filter** — `SecurityFilterChain` rules above (e.g., `/api/v1/admin/**` requires `ROLE_ADMIN`).
2. **Method security** — `@PreAuthorize("hasRole('ADMIN')")` on the controller method *and* ownership checks in service methods.

For ownership, do **not** check in the controller — do it in the service. Pattern:

```java
@Service
public class AccountService {
    public AccountDto findOwnedAccount(String accountId, String callerUserId) {
        AccountEntity acct = accounts.findById(accountId)
            .filter(a -> a.getOwnerId().equals(callerUserId))
            .orElseThrow(() -> new ResourceNotFoundException("account", accountId));
        return AccountDto.from(acct);
    }
}
```

The "filter then orElseThrow ResourceNotFoundException" idiom is what gives you 404-not-403 for non-owned accounts (see [API Contract](./03-api-contract.md)).

## External payment API call

Module 4 covered this. The Payment Processor is a downstream HTTPS service.

- Authenticate to the processor with a long-lived API key carried in `X-Processor-Key`. Read it from `bank.payment-processor.api-key` (env var).
- Set explicit timeouts (connect 2s, read 10s) — never use the default infinite timeout.
- On timeout or 5xx, propagate as `PaymentProcessorException` → 502 to the client.
- **Never** include the API key in your logs or in the response. The rubric specifically calls out "external payment API call exposes credentials" as a failing criterion.

For local development, run WireMock on port 8089 and stub `POST /payments`. The scaffold provides `wiremock/payment-processor.json` with one happy-path mapping and one 503 mapping.

## Token storage in the SPA

- **Use `sessionStorage`, not `localStorage`.** sessionStorage is cleared when the tab closes, which limits exposure if a user walks away from a shared machine.
- **Never** put the token in a cookie unless you also set `HttpOnly; Secure; SameSite=Lax` and adopt the BFF pattern. The capstone uses pure-SPA, not BFF.
- **Do not log the token.** No `console.log(token)` in committed code.
- **Do not put the token in a URL query parameter.** Always send it in the `Authorization` header.

## Hardening checklist (use as your day-2 self-review)

| Check | Where | Pass criteria |
|---|---|---|
| Issuer validated | `application.yml` | `issuer-uri` set to `https://accounts.google.com` |
| Audience validated | `application.yml` or custom validator | Tokens for other Google clients are rejected |
| Signature validated | Spring default | Tampered tokens return 401 |
| Expiry validated | Spring default | Expired tokens return 401 |
| CSRF | `SecurityConfig` | Disabled (we use Bearer tokens) |
| Sessions | `SecurityConfig` | `STATELESS` |
| CORS | `CorsConfig` | Specific origin, not `*` |
| Ownership | service layer | Non-owner gets 404, not 403 |
| Admin gate | URL filter + `@PreAuthorize` | Both layers |
| External API key | env var | Never in repo, never in logs |
| Token in SPA | OIDC client | sessionStorage, not localStorage; never logged |
| Error responses | `GlobalExceptionHandler` | Never echo stack trace or exception class name |
| `/health` | `SecurityConfig` | `permitAll`, no PII in body |
| Swagger/OpenAPI | `application-prod.yml` | Disabled or admin-gated in prod |

If any row in this table fails when you do your day-2 self-review, fix it before Day 3.

Next: [React Frontend](./05-frontend.md).
