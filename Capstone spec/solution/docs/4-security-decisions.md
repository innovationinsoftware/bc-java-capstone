# Security Decisions

## Authentication — Backend-for-Frontend (BFF) pattern

We implement the BFF security pattern to keep all OAuth2 tokens server-side.

- **Frontend storage:** The React SPA never sees or stores any JWT.  No tokens
  in `localStorage` or `sessionStorage`.  The browser only holds an `HttpOnly
  JSESSIONID` cookie issued by the BFF.  Because the cookie is `HttpOnly`,
  JavaScript cannot read it, which eliminates the most common XSS token-theft
  vector.
- **Session management:** The BFF (`spring-boot-starter-oauth2-client`) exchanges
  the authorization code for tokens, stores them in the server-side `HttpSession`,
  and forwards a `Bearer` JWT to the Resource Server on every proxied request.
- **CSRF mitigation:** `CookieCsrfTokenRepository` issues an `XSRF-TOKEN` cookie
  (JavaScript-readable).  The React SPA reads it and echoes it back in the
  `X-XSRF-TOKEN` request header on every mutating call (`apiFetch` helper).

## Dual Identity Providers

The system supports two OAuth2 providers simultaneously:

| Provider | Client registration | Used by |
|---|---|---|
| `mock-auth` | PKCE public client `spa-client` | "Sign in (Demo)" button |
| `google` | Confidential client (client secret) | "Sign in with Google" button |

The BFF `application.yml` registers both under `spring.security.oauth2.client`.
The sign-in page at `/login` (custom, served by the BFF) shows both buttons;
clicking one starts the flow for that provider.

## JWT validation — multi-issuer decoder

`JwtDecoderConfig` constructs a `JwtDecoder` that delegates to the correct
issuer-specific decoder based on the token's `iss` claim:

| Issuer | JWKS endpoint |
|---|---|
| `http://localhost:9000` | `http://localhost:9000/oauth2/jwks` |
| `https://accounts.google.com` | Google's published JWKS URI |

Both decoders validate: algorithm (`RS256`), signature, `exp`, and issuer.
Tokens with any other `iss` value are rejected with `401 Unauthorized`.

## JwtAuthConverter — sub → local identity

`JwtAuthConverter` implements `Converter<Jwt, AbstractAuthenticationToken>`.
After the JWT is validated, this converter:

1. Calls `BankUserRepository.findBySubject(sub)`.
2. If no row exists (first login), calls `BankUserEntity.newUser(...)` and persists
   a new row with a generated `usr_` prefixed ID.
3. Maps the `role` JWT claim (`CUSTOMER` / `ADMIN`) to the Spring
   `ROLE_CUSTOMER` / `ROLE_ADMIN` authority.
4. Returns a `JwtAuthenticationToken` whose `getName()` is the *local* `userId`
   (e.g. `usr_abc123`), not the raw JWT `sub`.

This means all downstream code (services, repositories) works with stable local
IDs — it is never coupled to the IdP-specific subject format.

## Authorization — two-layer RBAC

| Endpoint pattern | Required role |
|---|---|
| `POST /api/v1/transactions` | `ROLE_CUSTOMER` |
| `GET /api/v1/accounts/**` | `ROLE_CUSTOMER` |
| `GET /api/v1/users/me` | any authenticated |
| `GET /api/v1/admin/users` | `ROLE_ADMIN` |
| `GET /health` | public |

**Resource ownership** is enforced inside each service method.  For example,
`AccountService.findOwnedAccount` checks `account.ownerId.equals(principal.userId)` and
throws `ResourceNotFoundException` (-> 404) if the account exists but belongs to
another user — deliberately returning 404 rather than 403 to avoid leaking
the existence of other users' accounts.

## Attack mitigations

| Threat | Mitigation |
|---|---|
| **XSS token theft** | `HttpOnly` session cookie — JavaScript cannot read it |
| **CSRF** | `CookieCsrfTokenRepository` + `X-XSRF-TOKEN` header on all mutating calls |
| **CORS** | `SPA_ORIGIN` allowlist only; `allowCredentials(true)` |
| **SQL injection** | Spring Data JPA parameterized queries throughout |
| **Broken Object-Level Authorization** | Ownership check in every service before returning data |
| **Payment processor details leakage** | `GlobalExceptionHandler` catches `PaymentProcessorException` and returns a generic 502 body — upstream error message is never forwarded to the caller |
| **Idempotency** | `idempotencyKey` column with `UNIQUE` constraint prevents duplicate charges if the client retries |