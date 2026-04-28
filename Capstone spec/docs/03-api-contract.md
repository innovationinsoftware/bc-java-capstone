# 03 — REST API Contract

This is the contract the React SPA expects, the integration tests assert against, and the rubric grades you on. Every endpoint requires a valid Google-issued JWT unless explicitly marked `permitAll`.

Base path: `/api/v1`. Content type for both request and response: `application/json`.

## Conventions

- **All money values are decimal strings or numbers with up to 4 decimal places** (e.g., `"125.5000"` or `125.5`). Choose one and be consistent. Never serialize as a binary float.
- **All timestamps are ISO-8601 with timezone** (e.g., `"2026-04-26T13:45:30.123Z"`).
- **All IDs are opaque strings**. Never expose database row counts or sequential IDs.
- **`Authorization: Bearer <jwt>`** is required on every endpoint except `GET /health`.
- **CORS** is configured to allow the SPA origin only.
- **Error envelope** uses RFC 7807 Problem Details (Module 2 covered this). See "Error responses" below.

## Endpoints

### `GET /health`
**Auth:** none.
**200** — `{"status":"UP"}`. Used by the SPA to verify the API is reachable before showing the login button.

### `GET /api/v1/users/me`
**Auth:** any authenticated user (`SCOPE_openid`).
Returns the calling user's profile. **Idempotent side effect:** if no `BANK_USERS` row exists for the JWT's `sub` claim, one is created with role `CUSTOMER`. This is how new Google accounts onboard.

**200** —
```json
{
  "userId": "usr_a3f1...",
  "email": "alice@example.com",
  "displayName": "Alice Example",
  "role": "CUSTOMER"
}
```

### `GET /api/v1/accounts`
**Auth:** authenticated.
Returns the calling user's accounts. **`ADMIN` users do not see all accounts here** — admins use `/api/v1/admin/users/{userId}/accounts` for that. Keeping the endpoint scoped per-user prevents data-leak by role confusion.

**200** —
```json
[
  {
    "accountId": "acc_001",
    "accountType": "CHECKING",
    "currency": "USD",
    "balance": 1500.00,
    "createdAt": "2026-04-01T09:30:00Z"
  },
  {
    "accountId": "acc_002",
    "accountType": "SAVINGS",
    "currency": "USD",
    "balance": 8200.50,
    "createdAt": "2026-04-01T09:31:00Z"
  }
]
```

### `GET /api/v1/accounts/{accountId}`
**Auth:** authenticated **and** must own the account.
**Ownership check** — if the account's `OWNER_ID` does not match the calling user's `USER_ID`, return **404 Not Found** (not 403). 404-on-not-yours prevents account-ID enumeration. The rubric explicitly grades this kind of subtle hardening under "endpoint hardening."

**200** — single account object, same shape as the list element above.
**404** — account does not exist **or** is not owned by the caller.

### `GET /api/v1/accounts/{accountId}/transactions`
**Auth:** authenticated, must own the account.
Returns all transactions for the given account, ordered by `createdAt` descending. Pagination is **out of scope** — return the full list.

**200** —
```json
[
  {
    "transactionId": "txn_9c3...",
    "accountId": "acc_001",
    "type": "DEPOSIT",
    "amount": 200.00,
    "status": "COMPLETED",
    "counterparty": null,
    "transferGroupId": null,
    "description": "Paycheque",
    "createdAt": "2026-04-26T08:15:00Z"
  }
]
```
**404** — same rules as the account endpoint.

### `POST /api/v1/transactions`
**Auth:** authenticated, must own the source account.
Submit a new transaction. The request body changes shape based on `type` — the simplest design uses one DTO with optional fields and validates per-type in the service layer.

**Request** —
```json
{
  "accountId": "acc_001",
  "type": "WITHDRAWAL",
  "amount": 50.00,
  "counterparty": null,
  "description": "ATM Main St"
}
```

For a `TRANSFER_OUT`, `counterparty` is the destination account ID (your own account → internal transfer; an external string → external transfer that goes through the Payment Processor).

**Validation rules** (Bean Validation; covered in Lab 5):

- `accountId` — `@NotBlank`, must exist, must be owned by caller.
- `type` — `@NotNull`, must be one of `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_OUT`. (`TRANSFER_IN` is created internally by the system when the counterparty completes a transfer to you; clients never POST `TRANSFER_IN` directly.)
- `amount` — `@NotNull`, `@DecimalMin("0.01")`, `@Digits(integer = 15, fraction = 4)`.
- `counterparty` — required for `TRANSFER_OUT`, forbidden otherwise.
- `description` — `@Size(max = 255)`, optional.

Business rules:

- A `WITHDRAWAL` or `TRANSFER_OUT` may not bring `BALANCE` below zero. Return **422 Unprocessable Entity** with code `INSUFFICIENT_FUNDS` if it would.
- A `TRANSFER_OUT` to one of the caller's own accounts creates two rows in one transaction (debit source, credit destination), shares a `transferGroupId`, both rows status `COMPLETED`.
- A `TRANSFER_OUT` to an external `counterparty` calls the Payment Processor before committing. If the processor returns 2xx, status is `COMPLETED`; if it returns a 4xx/5xx within timeout, the transaction row is `FAILED` and the balance is **not** debited. Tests cover both paths.
- After commit, publish a `TransactionEvent` to Kafka (see [Kafka Events](./06-kafka-events.md)). Do this *after* the DB commit; if the publish fails, log it — do not roll back the DB transaction.

**201 Created** — returns the created transaction (or both rows for an internal transfer, as a list). Include a `Location` header pointing at `/api/v1/transactions/{transactionId}` of the source row.

**400 Bad Request** — validation error.
**404 Not Found** — `accountId` not owned.
**422 Unprocessable Entity** — `INSUFFICIENT_FUNDS` or other business-rule failure.
**502 Bad Gateway** — Payment Processor returned an error and the transfer is `FAILED`. Body explains.

### `GET /api/v1/transactions/{transactionId}`
**Auth:** authenticated, must own the transaction's account.
Returns a single transaction.
**200** — same shape as the elements of `/api/v1/accounts/{accountId}/transactions`.
**404** — does not exist or not owned.

### `GET /api/v1/admin/users` *(ADMIN only)*
**Auth:** authenticated **and** role `ADMIN`. Use `@PreAuthorize("hasRole('ADMIN')")` on the controller method (covered in Module 3 and Lab 5).

**200** — list of user summaries:
```json
[
  {"userId":"usr_001","email":"alice@example.com","displayName":"Alice","role":"CUSTOMER"}
]
```
**403** — caller is `CUSTOMER`.

### `GET /api/v1/admin/users/{userId}/accounts` *(ADMIN only)*
**Auth:** ADMIN. Returns any user's account list. Same response shape as `GET /api/v1/accounts`.

These two admin endpoints are the **only** places a non-owner can see another user's data. Everything else is scoped to the caller.

## Error responses (RFC 7807)

All error responses use this envelope:

```json
{
  "type": "about:blank",
  "title": "Insufficient funds",
  "status": 422,
  "code": "INSUFFICIENT_FUNDS",
  "detail": "Account acc_001 has balance 30.00, transaction would make it -20.00.",
  "instance": "/api/v1/transactions",
  "timestamp": "2026-04-26T13:45:30.123Z",
  "errors": [
    {"field": "amount", "message": "must be ≤ available balance"}
  ]
}
```

Implement this in `exception/GlobalExceptionHandler.java` with `@ControllerAdvice` — Module 2 covered the pattern. Map exceptions to status codes:

| Exception | HTTP | `code` |
|---|---|---|
| `MethodArgumentNotValidException` (Bean Validation) | 400 | `VALIDATION_FAILED` |
| `HttpMessageNotReadableException` (bad JSON) | 400 | `MALFORMED_JSON` |
| `AccessDeniedException` (Spring Security) | 403 | `FORBIDDEN` |
| `AuthenticationException` | 401 | `UNAUTHORIZED` |
| `ResourceNotFoundException` (yours) | 404 | `NOT_FOUND` |
| `InsufficientFundsException` (yours) | 422 | `INSUFFICIENT_FUNDS` |
| `BusinessRuleException` (yours, generic) | 422 | `BUSINESS_RULE_VIOLATION` |
| `PaymentProcessorException` (yours) | 502 | `PAYMENT_PROCESSOR_ERROR` |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` — message is generic, **never** echo exception text |

The "never echo exception text" rule is from the SAST playbook — leaking stack traces or raw exception messages is an information-disclosure finding.

## OpenAPI

Add `springdoc-openapi-starter-webmvc-ui` and serve `/v3/api-docs` and `/swagger-ui.html`. Hitting "Exceeds" on the rubric requires documented endpoints; springdoc reads your annotations for free.

`/v3/api-docs` and `/swagger-ui.html` should be reachable **without auth** in dev, and **disabled or behind ADMIN** in prod. Use a profile guard:

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

## What about WebSockets / push for live transactions?

Out of scope. The capstone is request/response REST. Kafka events imply a downstream consumer would push notifications, but you do not build that consumer or the SPA push channel.

Next: [Security](./04-security.md).
