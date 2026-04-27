# 04 — Security

The capstone uses the **Backend-for-Frontend (BFF)** pattern. This is the section the rubric weights most heavily after backend implementation. Read it twice.

## Why BFF, not pure-SPA tokens

Pure-SPA architectures put OAuth tokens in browser JavaScript (`sessionStorage` or `localStorage`). That works, but a single XSS vulnerability anywhere in the bundle (your code, a third-party dep, a CDN-loaded script) lets an attacker exfiltrate the token and impersonate the user permanently — until the token expires.

The BFF pattern moves tokens server-side. The browser only sees an HttpOnly session cookie. JavaScript cannot read the cookie (`document.cookie` doesn't show it). XSS becomes session-bounded — the attacker can act as the user *while the page is open*, but cannot copy the cookie to another machine and cannot persist after the tab closes.

For a banking app — sensitive data, regulated industry — BFF is the current OAuth working group recommendation. That's what you build.

## The three-tier identity model

```
Browser ── HttpOnly cookie ──► BFF ── Bearer JWT ──► Resource Server
                               │
                               ▼
                          Authorization Server
                          (issues tokens to BFF)
```

- **Browser** holds only a session cookie. No tokens, no JavaScript-readable secrets.
- **BFF** is a confidential OAuth2 client. It owns a `client_secret`, drives the Authorization Code + PKCE flow, and stores the resulting tokens server-side per session.
- **Resource Server** is the banking API. It validates JWTs but doesn't know about cookies or sessions — every call carries a Bearer header, full stop.
- **Authorization Server** issues tokens. For dev, the scaffold ships a tiny mock auth server (`backend/mock-auth/`, port 9000) — the same Spring Authorization Server pattern students built in Module 3 / Lab 5.

## Login flow (end-to-end)

1. User visits `http://localhost:5173/`. The SPA detects (via a `/api/v1/users/me` call returning 401) that no session exists, and shows a Sign in button.
2. The Sign in button is a plain anchor: `<a href="/oauth2/authorization/mock-auth">Sign in</a>`. Clicking it navigates the browser to that URL on the BFF.
3. The BFF (Spring Security) generates a PKCE `code_verifier`, stores it in the HTTP session, and 302s the browser to the Auth Server's `/oauth2/authorize` with the `code_challenge`.
4. User authenticates on the Auth Server. (For mock-auth, that's `alice`/`alice` for a `CUSTOMER` user, or `admin`/`admin` for an `ADMIN`.)
5. Auth Server 302s back to `/login/oauth2/code/mock-auth` on the BFF, with `?code=...&state=...`.
6. BFF (Spring Security) verifies `state`, POSTs to the Auth Server's `/oauth2/token` with the code, the PKCE verifier, and the `client_secret`, and receives `access_token`, `id_token`, and (typically) `refresh_token`.
7. BFF stores the tokens server-side as an `OAuth2AuthorizedClient` keyed by session ID. Sets `JSESSIONID` as `HttpOnly; Secure; SameSite=Lax`. 302s to `/`.
8. Subsequent SPA → BFF requests carry the cookie automatically. The BFF looks up the user's tokens and forwards them to the Resource Server via WebClient.

The SPA's only roles: kick off step 2 (a link click) and recover from 401s by re-doing step 2.

## What the BFF needs to do

### Spring dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>   <!-- for WebClient -->
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### Configuration

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          mock-auth:
            client-id: spa-client
            client-secret: ${OAUTH_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: openid, profile, email
        provider:
          mock-auth:
            issuer-uri: ${AUTH_SERVER_URL:http://localhost:9000}
```

`{baseUrl}` and `{registrationId}` are resolved by Spring at runtime.

### Security config

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                .requestMatchers("/login/**", "/oauth2/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(Customizer.withDefaults())
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            );
        return http.build();
    }
}
```

Notes:
- `oauth2Login(Customizer.withDefaults())` is the entire login wiring. Spring auto-exposes `/oauth2/authorization/{registrationId}` and `/login/oauth2/code/{registrationId}`.
- CSRF is **enabled** (BFF authenticates with cookies, so CSRF protection is required). The `CookieCsrfTokenRepository.withHttpOnlyFalse()` puts the token in a JS-readable `XSRF-TOKEN` cookie. The SPA reads it and sends `X-XSRF-TOKEN` on mutations.
- Logout invalidates the session, which removes the `OAuth2AuthorizedClient`, which means the tokens are gone.

### WebClient with OAuth2 filter

The proxy controllers call the Resource Server with `WebClient`. Spring Security's `ServletOAuth2AuthorizedClientExchangeFilterFunction` automatically attaches the bearer token from the user's stored `OAuth2AuthorizedClient`:

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient resourceServerWebClient(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${bank.resource-server.base-url}") String baseUrl) {

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2.setDefaultClientRegistrationId("mock-auth");

        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter(oauth2)
                .build();
    }
}
```

Now any controller can do:

```java
@RestController
@RequestMapping("/api/v1")
public class AccountsBffController {
    private final WebClient resourceServerWebClient;

    public AccountsBffController(WebClient resourceServerWebClient) {
        this.resourceServerWebClient = resourceServerWebClient;
    }

    @GetMapping("/accounts")
    public Mono<List<AccountDto>> listAccounts() {
        return resourceServerWebClient.get()
                .uri("/api/v1/accounts")
                .retrieve()
                .bodyToFlux(AccountDto.class)
                .collectList();
    }
}
```

The call to `/api/v1/accounts` on the Resource Server gets `Authorization: Bearer <jwt>` automatically. Token refresh near expiry is also automatic.

### What about CSRF on `/logout`?

`/logout` is a POST. It needs a CSRF token. The SPA reads the `XSRF-TOKEN` cookie and posts it back as a hidden form field or `X-XSRF-TOKEN` header.

```jsx
<form method="POST" action="/logout">
  <input type="hidden" name="_csrf" value={getCsrfTokenFromCookie()} />
  <button type="submit">Sign out</button>
</form>
```

## What the Resource Server needs to do

The Resource Server is **unchanged** from a pure-token model — it still validates JWTs against the Authorization Server, still maps `sub` to a local user, still applies role-based authorization. The fact that the JWT came via the BFF (instead of the browser) is invisible to it.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH_SERVER_URL:http://localhost:9000}
```

Audience validation is added via a `JwtDecoder` bean if your Auth Server sets the `aud` claim (the mock auth server does — to `spa-client`).

The `JwtAuthConverter` looks up the `BANK_USERS` row by `sub`, creates it on first login, and injects the role as `ROLE_CUSTOMER` / `ROLE_ADMIN`. Same pattern as before.

## Authorization layers (still required)

Even with BFF, the Resource Server enforces auth itself. **Two layers:**

1. **URL filter** — `/api/v1/admin/**` requires `ROLE_ADMIN` in `SecurityFilterChain`.
2. **Method security** — `@PreAuthorize("hasRole('ADMIN')")` on admin controller methods *and* ownership checks in services (return 404 for non-owned resources).

Don't rely on the BFF gatekeeping. A bug in the BFF should not become a security bug in the Resource Server.

## Same-origin in dev

The browser must see the SPA and the BFF as the same origin or the cookie won't be sent. In dev, Vite handles this:

```js
// frontend/vite.config.js
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api':     { target: 'http://localhost:8080', changeOrigin: true },
      '/login':   { target: 'http://localhost:8080', changeOrigin: true },
      '/logout':  { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2':  { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
```

The browser thinks everything is on `:5173`. Cookies set by the BFF are scoped to `localhost:5173` (because Vite proxies the response unmodified). Same-origin, no CORS.

For prod, build the SPA (`npm run build`) and copy `dist/` into `backend/bff/src/main/resources/static/`. Spring serves it on `:8080` alongside `/api/**` and `/login/**`.

## Mock Authorization Server

`backend/mock-auth/` runs Spring Authorization Server on port 9000 with two pre-registered users and one client:

| User | Password | Role |
|---|---|---|
| alice | alice | CUSTOMER |
| admin | admin | ADMIN |

| Client ID | Client secret | Type | Redirect URIs |
|---|---|---|---|
| spa-client | `spa-secret` (dev only) | Confidential | `http://localhost:8080/login/oauth2/code/mock-auth` |

The Auth Server includes:
- `/.well-known/openid-configuration` (auto-discovery)
- `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/userinfo`, `/connect/logout`

For Day 2 demo, students log in as `alice` then re-test as `admin` to verify the role gate.

> **Real IdP path.** If you want to demonstrate an external IdP (Google, Okta, Auth0), register an additional client registration alongside `mock-auth` in the BFF's `application.yml`. The architecture doesn't change. Document the choice in `docs/security-decisions.md`. Reaching this is "Exceeds" territory for the security rubric line — don't do it on Day 1.

## Hardening checklist (use as your day-2 self-review)

| Check | Where | Pass criteria |
|---|---|---|
| Issuer validated | RS `application.yml` | `issuer-uri` set; tokens from other issuers rejected |
| Audience validated | RS `JwtDecoder` bean | Tokens for other clients rejected |
| Signature + expiry | Spring default | Tampered/expired tokens → 401 |
| BFF client_secret | env var | Not in repo, not in logs |
| Session cookie | BFF `SecurityConfig` | HttpOnly, Secure, SameSite=Lax |
| CSRF protection | BFF `SecurityConfig` | Enabled with cookie repository; mutations require `X-XSRF-TOKEN` |
| Same-origin | dev: Vite proxy; prod: BFF serves SPA | Browser only sees one origin |
| No tokens in JS | SPA code | `grep -r "access_token\|sessionStorage\|localStorage" frontend/src` finds nothing token-related |
| Ownership 404s | RS service layer | Non-owner gets 404, not 403 |
| Admin gate two-layer | RS `SecurityConfig` + `@PreAuthorize` | Both layers enforce role |
| Logout works end-to-end | manual | Click sign out → cookie cleared, `/api/v1/users/me` returns 401 |
| Multi-tab logout | manual | Sign out in tab A → tab B's next API call returns 401 |

If any row fails, fix it before Day 3.

## What to write in `docs/security-decisions.md`

Short paragraphs covering:
- Why BFF over pure SPA (one or two sentences)
- Where tokens live (server-side `OAuth2AuthorizedClient`, never in browser)
- How CSRF is handled (cookie repository, SPA reads/echoes `XSRF-TOKEN`)
- How the BFF authenticates to the Resource Server (WebClient + `ServletOAuth2AuthorizedClientExchangeFilterFunction`)
- How the Resource Server enforces RBAC (URL filter + method security + service-layer ownership)
- How the Payment Processor API key is stored (env var, never logged)
- What's still on your hardening to-do list (production-grade session store, CSRF on `/logout`, etc.)

Two pages, max. The rubric grades that you understood the model — not that you wrote a thesis.

Next: [React Frontend](./05-frontend.md).
