# Requirements

## Domain

Three things exist:

- **User** — created on first Google login. Has a role: `CUSTOMER` or `ADMIN`.
- **Account** — belongs to one user. Has a type (`CHECKING` or `SAVINGS`), USD balance.
- **Transaction** — one movement of money against an account. Type is `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_OUT`, or `TRANSFER_IN`. Has an amount and a status (`COMPLETED` or `FAILED`).

A transfer between two of the user's own accounts creates **two** transaction rows (one OUT, one IN) inside one DB transaction.

Use `BigDecimal` for money. Never `double`.

## REST API

Base path `/api/v1`. JSON in, JSON out. `Authorization: Bearer <google-jwt>` required on every endpoint except `/health`.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | public | liveness check |
| GET | `/api/v1/users/me` | any user | returns caller's profile; creates user row on first login |
| GET | `/api/v1/accounts` | owner | caller's own accounts |
| GET | `/api/v1/accounts/{id}` | owner | one account (404 if not yours) |
| GET | `/api/v1/accounts/{id}/transactions` | owner | transactions for that account |
| POST | `/api/v1/transactions` | owner | submit a transaction |
| GET | `/api/v1/admin/users` | ADMIN | list all users |

**Ownership rule:** if a customer asks for an account that exists but belongs to someone else, return **404**, not 403. (Don't leak the existence of the resource.)

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

**Validation rules** (use Bean Validation):

- `accountId` — required, must exist, must be owned by caller
- `type` — required, one of DEPOSIT / WITHDRAWAL / TRANSFER_OUT
- `amount` — required, > 0
- `counterparty` — required for `TRANSFER_OUT`, forbidden otherwise
- A `WITHDRAWAL` or `TRANSFER_OUT` may not bring balance below 0 → return **422** with code `INSUFFICIENT_FUNDS`

**Error responses** — use a consistent JSON envelope (RFC 7807 style is fine — Module 2 covered it). Never echo a raw stack trace or exception message.

## Security

The hard requirements, in order:

1. **Google is your identity provider.** Register an OAuth client in Google Cloud Console with redirect URI `http://localhost:5173/callback`.
2. **The React SPA uses Authorization Code + PKCE.** Use `oidc-client-ts` or `react-oidc-context` — don't roll your own.
3. **The Spring Boot backend is a Resource Server.** Validate Google's JWTs against Google's JWKS endpoint. Validate `iss` (Google), `aud` (your client ID), `exp`, and signature.
4. **Two roles: CUSTOMER and ADMIN.** Stored in your `BANK_USERS` table (Google doesn't know about them). Map them to `ROLE_CUSTOMER` / `ROLE_ADMIN` via a `JwtAuthenticationConverter`.
5. **Admin endpoints (`/api/v1/admin/**`) require `ROLE_ADMIN`.** Enforce at both URL filter and method security (`@PreAuthorize`).
6. **Stateless sessions, CSRF disabled, CORS allows your SPA origin only** (not `*`).
7. **Tokens** live in `sessionStorage` on the SPA. Never `localStorage`, never URLs, never logs.
8. **Secrets** (Google client ID, DB password, payment-processor key) come from env vars. Nothing secret in the repo.

## Kafka

The broker is already running. Topic name: `transactions.completed`.

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
  "occurredAt": "2026-04-26T13:45:30Z"
}
```

Producer config (from Module 8): `acks: all`, `enable.idempotence: true`, JsonSerializer.

**Publish *after* the DB commits.** If the publish fails, log it — don't roll back the DB transaction.

## External payment API

For an external `TRANSFER_OUT` (counterparty is not one of the caller's own accounts), call a downstream "Payment Processor" over HTTPS before committing.

- Use `RestTemplate` or `WebClient` (Module 4 pattern).
- Carry an API key in a header, read from an env var.
- On success → mark the transaction `COMPLETED`.
- On 5xx or timeout → mark `FAILED`, do **not** debit the balance, return 502.
- For local dev, use **WireMock** on port 8089 to stub the processor.

## React frontend

Routes:

- `/login` — "Sign in with Google" button
- `/callback` — handles the OAuth redirect
- `/` — list of caller's accounts
- `/accounts/:id` — account detail with transaction list
- `/transactions/new` — submit a transaction
- `/admin/users` — admin-only user list
- `*` — 404 page

For each fetch, show **loading**, **empty**, and **error** states. Disable submit buttons while a request is in flight (or you'll get duplicate transactions).

## Tests

| Type | Tool | What to cover |
|---|---|---|
| Unit | JUnit 5, Mockito | Service methods (deposit, withdrawal, transfer, ownership, insufficient funds) |
| Integration | `@SpringBootTest`, MockMvc, `.with(jwt())`, `@EmbeddedKafka` | Auth (401/403), ownership, happy path, Kafka emission |

## Security validation

Run both, document both:

- **SAST** — Checkmarx (instructor will provide access). Triage every finding in `docs/sast-findings.md` as fix / accept / defer with one-line rationale. Fix the highs.
- **DAST** — OWASP ZAP or instructor tool. Run a baseline scan plus at least 3 custom payloads in `docs/dast-payloads.md`. Examples:
  - Bulk withdrawals to drain an account (does the balance ever go negative?)
  - Malformed amounts (`-1`, `Infinity`, 5+ decimal places)
  - Authorization probes (other user's account → 404? expired token → 401? mutated signature → 401? customer hitting `/admin` → 403?)

## What's out of scope (don't build these)

- Account opening / KYC
- Email or SMS notifications
- Refresh token rotation
- Mobile-responsive UI beyond "doesn't break at 1024px"
- A real payment processor integration
- A separate auth server (Google **is** your auth server)
- Pagination

If you find yourself writing one of these, stop and ask.
