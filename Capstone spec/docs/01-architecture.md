# 01 — Architecture

## System diagram (logical)

The capstone uses the **Backend-for-Frontend (BFF)** pattern. The browser only ever talks to the BFF, over a same-origin HttpOnly session cookie. Tokens never reach JavaScript.

```
                ┌─────────────────────────┐
                │  Authorization Server   │
                │  (mock, port 9000)      │
                │  Spring Authorization   │
                │  Server                 │
                └──────────▲──────────────┘
                           │ (1) OAuth2 Authorization Code + PKCE
                           │     (server-to-server, BFF holds the secret)
                           │
   (browser ↔ BFF, same origin)            (BFF ↔ Resource Server)
   ┌──────────────┐    ┌─────────────────┐    ┌────────────────────┐
   │ React SPA    │    │ Spring Boot BFF │    │ Resource Server    │
   │ (Vite, dev   │◄──►│ port 8080       │◄──►│ port 8081          │
   │  proxy:5173) │    │ OAuth2 client   │    │ Banking API        │
   │              │    │ Session cookie  │    │ JPA, Kafka, etc.   │
   └──────────────┘    └─────┬───────────┘    └────────┬───────────┘
       cookie                │                         │
                             │                         ├──► Oracle 21c
                             │                         ├──► Kafka topic
                             │                         └──► Payment Processor
                             │                              (WireMock)
                             │ WebClient + OAuth2 filter
                             │ attaches Bearer token
                             ▼
                    (calls Resource Server with
                     the user's access token)
```

Numbered flows:

1. **Login** — User clicks Sign in → SPA navigates to `/oauth2/authorization/mock-auth` (a Spring-exposed endpoint). The **BFF** initiates Authorization Code + PKCE against the Authorization Server. PKCE verifier lives in the BFF's HTTP session, not in the browser. The browser only sees redirects.
2. **Token exchange** — Auth Server redirects back to `/login/oauth2/code/mock-auth` on the BFF. Spring Security exchanges the code for tokens (using the BFF's `client_secret`), stores them server-side as an `OAuth2AuthorizedClient` keyed by session ID, sets the HttpOnly session cookie, and redirects to `/`.
3. **API call** — SPA calls `/api/v1/accounts` (same origin, cookie sent automatically). The BFF's controller uses **`WebClient`** with `ServletOAuth2AuthorizedClientExchangeFilterFunction` to call the Resource Server. The filter looks up the user's `OAuth2AuthorizedClient`, attaches the access token as a Bearer header, and refreshes it transparently if it's near expiry.
4. **Resource Server** — Validates the JWT (signature, issuer, audience, expiry), maps the `sub` claim to a local user with role, and serves the request.
5. **Domain side effects** — Resource Server reads/writes Oracle, calls the Payment Processor, publishes Kafka events. The BFF is **not** in the data path for these.

## Why BFF, not pure SPA

| Concern | Pure SPA | BFF |
|---|---|---|
| Where do tokens live? | Browser JavaScript | Server-side session |
| What can XSS steal? | Tokens (full impersonation) | Nothing usable beyond active session |
| OAuth client type | Public | Confidential (client_secret) |
| Same-origin deployment | Optional | Natural |
| CORS concerns | Yes (cross-origin API) | No (same-origin BFF) |
| Multi-tab logout | Manual sync | Automatic (cookie shared) |

The capstone is a banking app — sensitive data, regulated industry. The current OAuth working group recommendation for sensitive applications is BFF, and that's what students will demonstrate they can build.

## Components

### React SPA (`frontend/`)

- **Tooling.** Vite for bundling and dev server. `npm run dev` for local, `npm run build` for production bundle. The build output is copied into the BFF's `src/main/resources/static/` for prod deployment.
- **Routing.** `react-router-dom` v6. No `RequireAuth`/`RequireRole` components, no `CallbackPage` — Spring Security handles the auth flow at the network layer. If a route's API call returns 401, the SPA redirects to the BFF's login URL.
- **State.** Component-local state with `useState` and `useEffect`. No OIDC library, no `AuthProvider`.
- **Auth integration.** Login is a plain anchor: `<a href="/oauth2/authorization/mock-auth">Sign in</a>`. Logout is a form POST to `/logout` with the CSRF token. There is no OAuth library in the frontend.
- **Same-origin in dev.** Vite is configured to proxy `/api/**`, `/login/**`, `/logout`, and `/oauth2/**` to the BFF on `localhost:8080`, so from the browser's point of view everything is on `localhost:5173`. CORS does not apply.

### Spring Boot BFF (`backend/bff/`)

- **Role.** OAuth2 client (confidential), session-based authentication, serves the SPA in production, proxies API calls to the Resource Server.
- **Starters.** `spring-boot-starter-oauth2-client`, `spring-boot-starter-webflux` (for `WebClient`), `spring-boot-starter-security`.
- **Auth setup.** `application.yml` configures `spring.security.oauth2.client.registration.mock-auth` and `provider.mock-auth.issuer-uri`. Spring auto-discovers endpoints from `/.well-known/openid-configuration`. Spring auto-exposes `/oauth2/authorization/mock-auth`, `/login/oauth2/code/mock-auth`, and `/logout` — no custom controllers needed.
- **Outbound calls.** A single `WebClient` bean is configured with `ServletOAuth2AuthorizedClientExchangeFilterFunction`. Proxy controllers under `/api/v1/**` use this `WebClient` to call the Resource Server. The filter attaches the user's bearer token automatically; nothing in the proxy code touches tokens.
- **CSRF.** Enabled. The BFF issues an `XSRF-TOKEN` cookie; the SPA reads it and sends `X-XSRF-TOKEN` on mutations.
- **Session.** Standard Spring `SESSION` (in-memory dev, Redis or DB-backed in prod). The `OAuth2AuthorizedClient` (which holds the user's tokens) is keyed by session ID.

### Spring Boot Resource Server (`backend/resource-server/`)

- **Role.** The banking API. Same code that existed pre-BFF — JPA against Oracle, Kafka producer, Payment Processor integration, business rules.
- **Starters.** `spring-boot-starter-oauth2-resource-server`, JPA, validation, `spring-kafka`.
- **Auth setup.** Validates JWTs against the same Authorization Server. Same `JwtAuthConverter` pattern: maps the `sub` claim to a local `BANK_USERS` row, creates the row on first login, attaches `ROLE_CUSTOMER` or `ROLE_ADMIN`.
- **Network.** Listens on `localhost:8081`. Should not be reachable from the public internet — only the BFF talks to it. (For local dev, students don't bother with network isolation; for an "Exceeds" answer, students can document how they'd lock it down behind a private network in production.)

### Mock Authorization Server (`backend/mock-auth/`)

- **Role.** Issues tokens. Stand-in for whatever IdP a real bank would integrate with (Okta, Auth0, Azure AD, Keycloak).
- **Starter.** `spring-boot-starter-oauth2-authorization-server`.
- **Implementation.** A single `@Configuration` class registering one or two clients (`spa-client` for the BFF, optionally a service client) and an in-memory user store. About 60 lines of code.
- **Listens on** `localhost:9000`. Exposes `/.well-known/openid-configuration`, `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/userinfo`, `/connect/logout`.

This is the same Authorization Server pattern students built in Module 3 / Lab 5, with two pre-registered clients: `spa-client` for the BFF (confidential, with client_secret) and an in-memory test user (`alice`/`alice`, role `CUSTOMER`).

## Repository layout

One Git repo per team. Multi-module Maven backend:

```
<team-name>-banking/
├── README.md
├── .gitignore
├── .env.example
├── backend/
│   ├── pom.xml                      # parent, packaging=pom
│   ├── mock-auth/
│   │   ├── pom.xml
│   │   └── src/main/java/com/example/mockauth/
│   │       └── MockAuthApplication.java + AuthorizationServerConfig.java
│   ├── bff/
│   │   ├── pom.xml
│   │   └── src/main/java/com/example/bff/
│   │       ├── BffApplication.java
│   │       ├── config/              SecurityConfig, WebClientConfig
│   │       └── controller/          AccountsController, TransactionsController, UsersController, MeController
│   └── resource-server/
│       ├── pom.xml
│       └── src/main/java/com/example/banking/
│           ├── BankingApplication.java
│           ├── config/              SecurityConfig, JwtDecoderConfig, RestClientConfig (for Payment Processor)
│           ├── controller/          AccountController, TransactionController, UserController, HealthController
│           ├── dto/, exception/, kafka/, model/, repository/, security/, service/
│           └── resources/db/migration/
├── frontend/
│   ├── package.json
│   ├── vite.config.js               # proxy /api,/login,/logout,/oauth2 → :8080
│   ├── index.html
│   └── src/
│       ├── main.jsx, App.jsx
│       ├── api/                     apiClient (cookie-based, CSRF-aware)
│       ├── components/
│       └── routes/                  AccountsPage, AccountDetailPage, etc. — no auth/ folder
├── scripts/
│   ├── setup-oracle.sql
│   ├── start-mock-auth.sh           BFF needs this up first
│   ├── start-bff.sh
│   ├── start-resource-server.sh
│   ├── start-frontend.sh
│   └── start-wiremock.sh
├── wiremock-stubs/
└── docs/
    ├── architecture.md, security-decisions.md, sast-findings.md, dast-payloads.md, demo-script.md
```

## Ports and URLs (dev)

| Service | URL | Notes |
|---|---|---|
| React SPA (dev) | http://localhost:5173 | Vite, proxies `/api`, `/login`, `/logout`, `/oauth2` to 8080 |
| BFF | http://localhost:8080 | OAuth2 client, session cookie, WebClient |
| Resource Server | http://localhost:8081 | OAuth2 resource server, banking API |
| Mock Auth Server | http://localhost:9000 | Spring Authorization Server |
| Oracle XE | jdbc:oracle:thin:@//localhost:1521/XEPDB1 | Resource Server connects |
| Kafka broker | provided by instructor | Resource Server connects |
| Payment Processor stub | http://localhost:8089 | WireMock; Resource Server connects |

The browser never directly contacts the Resource Server, the Auth Server, or any other downstream — only the BFF.

## Configuration strategy

- `application.yml` per module holds non-secret defaults.
- Secrets come from env vars: BFF's `OAUTH_CLIENT_SECRET`, Resource Server's `ORACLE_PASSWORD`, etc.
- **Don't commit secrets.** The rubric specifically grades credential handling.

The BFF's `application.yml` will look something like:

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

bank:
  resource-server:
    base-url: ${RESOURCE_SERVER_URL:http://localhost:8081}

server:
  port: 8080
```

## CORS

CORS is **not** required in the BFF pattern. The browser talks to the BFF only, on the same origin. CORS exists to allow cross-origin requests; if you find yourself configuring `Access-Control-Allow-Origin`, you're not running the BFF correctly. (The Resource Server doesn't need CORS either — only the BFF talks to it, server-to-server.)

The one case where CORS matters in dev: the Vite dev server runs on `:5173` and the BFF on `:8080`. They are technically different origins. The fix is **not** CORS — the fix is the Vite proxy in `vite.config.js`, which makes the browser see everything as `:5173`. From the browser's perspective, the BFF and the SPA are the same origin.

## What the scaffold gives you

The scaffold ships with all three backend modules building successfully and the SPA shell rendering. You will:

- Extend the Resource Server to implement the missing transaction logic (TODO markers in `TransactionService.applyTransferOut`).
- Add proxy endpoints to the BFF for any new resource the SPA needs (the scaffold gives you accounts and transactions as examples).
- Implement the SPA pages (the scaffold gives shells with loading/empty/error patterns).
- Write tests, run scans, write docs.

You should **not** rip out the BFF and reintroduce a token-in-localStorage SPA. The rubric grades the BFF pattern specifically.

Next: [Domain Model & Database](./02-domain-model.md).
