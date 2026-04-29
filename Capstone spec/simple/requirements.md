# Requirements

## Architecture

Four runtime pieces:

```
Browser ──cookie──► BFF ──Bearer JWT──► Resource Server ─► Oracle, Kafka, Payment Processor
                    │
                    ▼
              Authorization Server
              (mock, port 9000)
```

- **React SPA** (port 5173 in dev) — Vite + react-router-dom. **No OAuth library.** Holds only the BFF's HttpOnly session cookie.
- **BFF** (port 8080) — Spring Boot OAuth2 client. Drives Authorization Code + PKCE login, holds tokens server-side per session. Uses **Spring WebClient** + `ServletOAuth2AuthorizedClientExchangeFilterFunction` to call the Resource Server.
- **Resource Server** (port 8081) — Spring Boot OAuth2 resource server. The banking API. Validates JWTs, persists to Oracle, publishes Kafka events.
- **Mock Authorization Server** (port 9000) — Spring Authorization Server with two pre-registered users (`alice`/`alice` for CUSTOMER, `admin`/`admin` for ADMIN) and one client (`spa-client`).

In dev, the Vite dev server proxies `/api/**`, `/login/**`, `/logout`, and `/oauth2/**` to the BFF on `:8080`, so the browser sees everything as same-origin on `:5173`.

## Domain

Three things exist:

- **User** — created on first sign-in. Has a role: `CUSTOMER` or `ADMIN`.
- **Account** — belongs to one user. Has a type (`CHECKING` or `SAVINGS`), USD balance.
- **Transaction** — one movement of money against an account. Type is `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_OUT`, or `TRANSFER_IN`. Has an amount and a status (`COMPLETED` or `FAILED`).

A transfer between two of the user's own accounts creates **two** transaction rows (one OUT, one IN) inside one DB transaction.

Use `BigDecimal` for money. Never `double`.

## REST API

Base path `/api/v1`. JSON in, JSON out. Both the BFF and the Resource Server expose the same paths — the BFF is a thin proxy.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | public (RS only) | liveness check |
| GET | `/api/v1/users/me` | any user | returns caller's profile; creates user row on first login |
| GET | `/api/v1/accounts` | owner | caller's own accounts |
| GET | `/api/v1/accounts/{id}` | owner | one account (404 if not yours) |
| GET | `/api/v1/accounts/{id}/transactions` | owner | transactions for that account |
| POST | `/api/v1/transactions` | owner | submit a transaction |
| GET | `/api/v1/admin/users` | ADMIN | list all users |

**Ownership rule:** if a customer asks for an account that exists but belongs to someone else, return **404**, not 403.

**Transaction submission body:**

```json
{
  "accountId": "acc_001",
  "type": "WITHDRAWAL",
  "amount": 50.00,
  "counterparty": null,
  "description": "ATM"
}
```

**Validation rules** (Bean Validation):

- `accountId` — required, must exist, must be owned by caller
- `type` — required, one of DEPOSIT / WITHDRAWAL / TRANSFER_OUT
- `amount` — required, > 0
- `counterparty` — required for `TRANSFER_OUT`, forbidden otherwise
- A `WITHDRAWAL` or `TRANSFER_OUT` may not bring balance below 0 → return **422** with code `INSUFFICIENT_FUNDS`

**Error responses** — RFC 7807 envelope. Never echo a stack trace.

## Security (BFF model)

The hard requirements:

1. **No tokens in the browser.** No `oidc-client-ts`, no `react-oidc-context`, no token in `sessionStorage` or `localStorage`. The browser holds only the BFF's HttpOnly session cookie.
2. **BFF is the OAuth2 client.** It owns a `client_secret` (env var, not in repo) and drives Authorization Code + PKCE against the Authorization Server. Spring's `oauth2Login()` does the work; you mostly configure it.
3. **BFF stores tokens server-side** as `OAuth2AuthorizedClient` keyed by session ID. Spring auto-refreshes expired access tokens.
4. **BFF calls the Resource Server with WebClient** + `ServletOAuth2AuthorizedClientExchangeFilterFunction`. The filter attaches `Authorization: Bearer <jwt>` automatically. Your proxy code never touches tokens.
5. **Resource Server validates JWTs**: issuer, signature, expiry, and audience. Maps `sub` to a local `BANK_USERS` row via `JwtAuthConverter`, creating it on first login with role `CUSTOMER`.
6. **RBAC** — `CUSTOMER` and `ADMIN` enforced on the Resource Server at URL filter **and** method security (`@PreAuthorize`). The BFF also surfaces the role to the SPA for UX gating.
7. **CSRF protection** is enabled on the BFF (cookie auth requires it). SPA reads the `XSRF-TOKEN` cookie and sends `X-XSRF-TOKEN` on mutations. `/logout` is a form POST with the CSRF token.
8. **Same-origin in dev** via Vite proxy (CORS does not apply). For prod, the SPA is built and served from the BFF's static folder.
9. **Secrets** (BFF `client_secret`, DB password, Payment Processor key) come from env vars. Nothing secret in the repo.

## Kafka

Broker is already running. Topic name: `transactions.completed`. The **Resource Server** is the producer (the BFF is not in the data path).

After every committed transaction, publish a JSON event keyed by `accountId`:

```json
{
  "eventId": "evt_uuid",
  "transactionId": "txn_id",
  "accountId": "acc_001",
  "ownerId": "usr_id",
  "type": "WITHDRAWAL",
  "amount": 50.00,
  "currency": "USD",
  "status": "COMPLETED",
  "occurredAt": "2026-04-27T13:45:30Z"
}
```

Producer config: `acks: all`, `enable.idempotence: true`, JsonSerializer.

**Publish *after* the DB commits.** If the publish fails, log it — don't roll back the DB transaction.

## External payment API

For an external `TRANSFER_OUT` (counterparty isn't one of the caller's own accounts), the **Resource Server** calls the downstream Payment Processor over HTTPS before committing.

- `RestTemplate` or `WebClient` (Module 4 pattern). Note: the Resource Server's outbound HTTP is independent of the BFF's WebClient.
- API key in a header, read from env var.
- On success → mark the transaction `COMPLETED`.
- On 5xx or timeout → mark `FAILED`, do **not** debit the balance, return 502.
- For local dev, use **WireMock** standalone (started via `scripts/start-wiremock.sh`) on port 8089.

## React frontend

Routes (no `/login`, no `/callback` — Spring handles those):

- `/` — list of caller's accounts
- `/accounts/:id` — account detail with transaction list
- `/transactions/new` — submit a transaction
- `/admin/users` — admin-only user list (gated by `useMe()` for UX, by API for security)
- `*` — 404 page

The header has a sign-in `<a href="/oauth2/authorization/mock-auth">` link (when signed out) and a logout `<form method="POST" action="/logout">` with the CSRF token (when signed in).

For each fetch, show **loading**, **empty**, and **error** states. Disable submit buttons while a request is in flight.

## Tests

| Type | Tool | What to cover |
|---|---|---|
| Unit | JUnit 5, Mockito | Resource Server services (deposit, withdrawal, transfer, ownership, insufficient funds) |
| Integration (RS) | `@SpringBootTest`, MockMvc, `.with(jwt())`, `@EmbeddedKafka` | Auth (401/403), ownership, happy path, Kafka emission |
| Integration (BFF) | `@SpringBootTest` | Unauthenticated `/api/v1/users/me` returns 401; OAuth login URL is exposed |

## Security validation

Run both, document both:

- **SAST** — Checkmarx. Triage every finding in `docs/sast-findings.md` as fix / accept / defer with one-line rationale. Fix the highs.
- **DAST** — OWASP ZAP or instructor tool. Baseline scan + at least 3 custom payloads in `docs/dast-payloads.md`. Examples:
  - Bulk withdrawals to drain an account (does the balance ever go negative?)
  - Malformed amounts (`-1`, `Infinity`, 5+ decimal places)
  - Authorization probes (other user's account → 404? expired session → 401? mutated CSRF token → 403?)

## What's out of scope (don't build these)

- Account opening / KYC
- Email or SMS notifications
- A real external IdP (Google, Okta) — mock auth server is the path of least resistance
- Refresh-token logic — Spring handles it inside the BFF
- A real payment processor integration
- Production-grade session storage (Redis/JDBC) — in-memory sessions are fine for the capstone; document the trade-off
- Mobile-responsive UI beyond "doesn't break at 1024px"
- Pagination

If you find yourself writing one of these, stop and ask.
