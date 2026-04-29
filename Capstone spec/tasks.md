I have enough context now to produce the task list. Below is a bullet-list inventory of what's missing or incomplete in the `solution/` folder, derived from `Capstone spec/docs/00-11.md`, the `simple/scaffolding/` reference layout, and `10-definition-of-done.md`.

## Architectural rework (the biggest gap)

The spec mandates a **multi-module Maven build with three separate Spring Boot apps** plus a real mock auth server. The solution currently ships a **single-module app** that conflates the BFF and Resource Server and uses **Google OAuth** instead of the required mock auth server. Until this is fixed, none of the rest of the rubric's "Security Integration" line is genuinely earnable.

- [ ] Restructure `solution/backend/` as a **multi-module Maven project** with a parent `pom.xml` (`packaging=pom`) and three child modules: `mock-auth/`, `bff/`, `resource-server/`.
- [ ] Add a `solution/backend/mock-auth/` module:
  - [ ] `pom.xml` pulling in `spring-boot-starter-oauth2-authorization-server`.
  - [ ] `MockAuthApplication.java` + `AuthorizationServerConfig.java` (port **9000**).
  - [ ] In-memory users `alice`/`alice` (CUSTOMER) and `admin`/`admin` (ADMIN).
  - [ ] One registered confidential client `spa-client` / `spa-secret` with redirect `http://localhost:8080/login/oauth2/code/mock-auth`.
  - [ ] Expose `.well-known/openid-configuration`, `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/userinfo`, `/connect/logout`, with the `aud` claim set to `spa-client`.
- [ ] Add a `solution/backend/bff/` module:
  - [ ] `pom.xml` with `spring-boot-starter-oauth2-client`, `spring-boot-starter-webflux`, `spring-boot-starter-security`.
  - [ ] `BffApplication.java`, `config/SecurityConfig.java`, `config/WebClientConfig.java` (binds `ServletOAuth2AuthorizedClientExchangeFilterFunction` to a `WebClient` pointed at `bank.resource-server.base-url`).
  - [ ] Proxy controllers: `AccountsBffController`, `TransactionsBffController`, `UsersBffController`, plus a `/api/v1/users/me` proxy.
  - [ ] CSRF enabled (`CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler`); `SameSite=Lax`, `HttpOnly`, `Secure` cookie config.
  - [ ] `application.yml` with `spring.security.oauth2.client.registration.mock-auth` + `provider.mock-auth.issuer-uri=http://localhost:9000`. Listens on **port 8080**.
- [ ] Re-package the existing single-module backend as `solution/backend/resource-server/` (port **8081**):
  - [ ] Replace the OAuth2 **client** stack with `spring-boot-starter-oauth2-resource-server`.
  - [ ] Drop `CustomOidcUserService`, `CorsConfig` (CORS isn't needed in BFF), and the Google `oauth2Login` block from `SecurityConfig`.
  - [ ] Add a `JwtAuthConverter` that maps the JWT `sub` → `BANK_USERS` row (creates on first login) and assigns `ROLE_CUSTOMER` / `ROLE_ADMIN`.
  - [ ] Add a `JwtDecoderConfig` that validates `issuer`, `audience` (`spa-client`), signature, expiry.
  - [ ] Replace `oauth2Login(...)` in `SecurityConfig` with `oauth2ResourceServer(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))`. Disable session creation; statelessness on the RS.
  - [ ] Update `application.yml` to use `spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9000` (drop the `oauth2.client.registration.google` block).
  - [ ] Remove the cross-origin `defaultSuccessUrl("http://localhost:5173/", true)` + Google logout redirects — they belong on the BFF.

## Missing backend code & config

- [ ] `JwtAuthConverter` class under `resource-server/security/` (currently absent — solution uses `CustomOidcUserService` for Google instead).
- [ ] `JwtDecoderConfig` enforcing audience validation per `04-security.md`.
- [ ] `GET /api/v1/users/me` should idempotently create a `BANK_USERS` row on first login from the JWT's `sub` claim (currently `UserController` is wired to OIDC user, not JWT).
- [ ] Controller methods enforce `@PreAuthorize("hasRole('ADMIN')")` on `/api/v1/admin/**` (verify both URL filter **and** method security per spec — two-layer RBAC).
- [ ] `springdoc-openapi-starter-webmvc-ui` for `/v3/api-docs` + `/swagger-ui.html` (Exceeds — currently absent).
- [ ] `V2__seed_admin.sql` references a Google `sub` placeholder; replace with the mock-auth admin's `sub` so `admin/admin` lands as `ADMIN`.
- [ ] Confirm `TransactionService.applyTransferOut` covers both **internal transfer (two rows, shared `transferGroupId`, balances debited/credited atomically)** and **external transfer with Payment Processor success / 4xx / 5xx → FAILED, no debit** paths. (The spec marks this as the single biggest "you must build" item; make sure it's done in the solution.)
- [ ] Kafka publish is `AFTER_COMMIT` (either via `@TransactionalEventListener` or controller-after-service). Verify the Kafka publisher is **not** inside the DB transaction.

## Missing tests

The spec's Definition of Done requires the unit-test rows in `07-testing.md` and the integration scenarios in the integration-test list to all be green. The solution currently has only `HealthControllerTest`, `AccountControllerIntegrationTest`, and a partial `TransactionServiceTest`.

- [ ] `TransactionServiceTest`: deposit happy path, withdrawal happy / insufficient funds, **internal transfer (two rows + shared transferGroupId)**, **external transfer success**, **external transfer processor 503 → FAILED + no debit**.
- [ ] `AccountServiceTest`: returns owner's accounts, non-owner returns 404, empty list.
- [ ] `PaymentServiceTest`: API key in header, timeout → `PaymentProcessorException`, 5xx → same.
- [ ] `JwtAuthConverterTest`: first-login row creation, subsequent-login reuse, admin role mapping.
- [ ] `GlobalExceptionHandlerTest`: every mapped exception → expected status + RFC 7807 envelope.
- [ ] `TransactionEventPublisherTest`: correct topic, key=accountId, payload.
- [ ] Integration test class with `@SpringBootTest` + MockMvc + `.with(jwt())` + `@EmbeddedKafka` covering: 401, 403, ownership-404, deposit happy path with Kafka emission, internal transfer creates two rows, external transfer 503 → FAILED, withdrawal-422 path.
- [ ] BFF smoke test (`BffSmokeTest`) verifying `/health` and that `/api/v1/**` requires a session.

## Frontend rework to match BFF spec

The frontend currently uses a cross-origin `VITE_API_BASE_URL`, `RequireAuth`/`RequireRole`, and an `AuthContext` — all of which the spec (`05-frontend.md`) says **must not exist** in the BFF model. `10-definition-of-done.md` literally checks `grep -r "oidc-client-ts\|react-oidc-context" frontend/src` returns nothing.

- [ ] `frontend/vite.config.js` must proxy `/api`, `/login`, `/logout`, `/oauth2` to `http://localhost:8080` (currently no proxy is configured).
- [ ] Delete `auth/AuthContext.jsx`, `auth/RequireAuth.jsx`, `auth/RequireRole.jsx`, and `routes/LoginPage.jsx`. The spec is explicit about this.
- [ ] Remove `RequireAuth`/`RequireRole` wrappers from `App.jsx`; routes are flat and rely on the BFF / 401-bounce.
- [ ] Rewrite `api/apiClient.js`:
  - [ ] Use **same-origin** paths (no `VITE_API_BASE_URL`); change `credentials: "include"` → `credentials: "same-origin"`.
  - [ ] On 401, `window.location.assign("/oauth2/authorization/mock-auth")`.
- [ ] Add `hooks/useMe.js` returning `{ user, loading }`.
- [ ] Add `components/` per spec: `AccountCard.jsx`, `TransactionList.jsx`, `TransactionForm.jsx`, `ErrorBanner.jsx`.
- [ ] Replace login button with plain `<a href="/oauth2/authorization/mock-auth">Sign in</a>`; logout becomes a `<form method="POST" action="/logout">` carrying `_csrf` from the `XSRF-TOKEN` cookie.
- [ ] Hide `/admin/users` nav link when `useMe().user.role !== 'ADMIN'` (UX gating only — server enforces).
- [ ] Add `npm test` script + at least one Vitest/Jest test (DoD requires `npm test` green).

## Scripts, env, ops

- [ ] Add `scripts/start-mock-auth.sh` and `scripts/start-resource-server.sh`; rename / repurpose `start-backend.sh` to `start-bff.sh`.
- [ ] Update `.env.example` to drop `GOOGLE_CLIENT_ID` and add `OAUTH_CLIENT_SECRET=spa-secret`, `AUTH_SERVER_URL=http://localhost:9000`, `RESOURCE_SERVER_URL=http://localhost:8081`. (Also add `SPRING_PROFILES_ACTIVE` if used.)
- [ ] `solution/README.md` currently documents the Google-OAuth flow and tells students to register a Google client; rewrite for the mock-auth flow (the root `README.md` even calls out "to be updated to use standalone installations").
- [ ] Decide what to do with `docker-compose.yml`: the root README says infra has been **moved to standalone installations**, so either delete it or document that it's optional.
- [ ] Confirm `scripts/*.sh` have execute bits set (DoD common-gotcha item).

## Documentation gaps

- [ ] `docs/architecture.md` is currently a 7-line stub with a malformed mermaid block (uses backticks instead of triple backticks). Spec wants **one diagram + one page of design decisions**, including the BFF rationale and trade-offs (in-memory session, no Redis, etc.).
- [ ] `docs/security-decisions.md` should be aligned with mock-auth (currently references Google) and cover: token storage, CSRF, BFF→RS auth via WebClient filter, RBAC two-layer, payment-processor key handling, hardening to-do.
- [ ] `docs/sast-findings.md` should cover **every** Checkmarx finding with rescan results — the file currently shows three handcrafted entries, not an actual scan triage.
- [ ] `docs/dast-payloads.md` needs a baseline-scan section in addition to the three custom payloads, and the rubric's "Exceeds" expects a written paragraph **per payload class** (bulk-transfer, malformed input, authorization probes, JWT replay, JWT signature mutation).
- [ ] `docs/demo-script.md`, `docs/team-plan.md`: review for accuracy after the architecture is corrected (steps still reference Google flow + single backend).
- [ ] No `.github/workflows/ci.yml` (optional per `09-deliverables-and-rubric.md`, but called out as appreciated — Exceeds material).

## Verification before declaring "done"

Run the DoD checklist in `Capstone spec/docs/10-definition-of-done.md` end-to-end against the rebuilt solution; the most likely current failures are:
- `mvn -f backend/pom.xml test` (no parent POM yet → build fails).
- All four services starting cleanly (mock-auth doesn't exist yet).
- `grep -r "oidc-client-ts\|react-oidc-context" frontend/src` returning nothing (currently the frontend has its own auth context — different package, but the *intent* is "no client-side auth library/state"; remove it).
- DevTools showing only `JSESSIONID` + `XSRF-TOKEN` (currently login goes through Google, so the cookie story is half right but the auth server is wrong).
- Customer→another customer's account returns **404, not 403** (verify in `AccountService` ownership check).
