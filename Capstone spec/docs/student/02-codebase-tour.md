# 02 — Codebase Tour

Read before you write. The scaffolding gives you ~80% of the code. Knowing what
is already there means you stop reinventing pieces and you stop changing things
the tests depend on.

Spend 60 minutes on this chapter as a team. At the end every member should be
able to draw the request flow on a whiteboard from memory.

## Module map

```
scaffolding/
├── backend/
│   ├── pom.xml                 (parent POM — packaging=pom)
│   ├── mock-auth/              port 9000 — Spring Authorization Server
│   ├── bff/                    port 8080 — OAuth2 client + reverse proxy
│   └── resource-server/        port 8081 — JWT-protected banking API
├── frontend/                   port 5173 — React 18 + Vite
├── scripts/                    *.bat / *.sh — start each service
├── wiremock-stubs/             Payment Processor stubs
└── http-tests/                 banking.http — IntelliJ HTTP client
```

The full architecture is in [`../01-architecture.md`](../01-architecture.md). Read
that first if you have not yet.

## What is already done for you

Do not rewrite any of these. They are working code:

| Concern | Where |
|---|---|
| Maven multi-module build | `backend/pom.xml` + per-module POMs |
| JPA entities + repositories | `resource-server/.../model/` and `.../repository/` |
| Database schema | `resource-server/src/main/resources/db/migration/V1__*.sql` |
| DTOs with Bean Validation | `resource-server/.../dto/` |
| Custom domain exceptions | `resource-server/.../exception/` |
| RFC 7807 error responses | `resource-server/.../exception/GlobalExceptionHandler.java` |
| Kafka producer wiring | `resource-server/.../kafka/` and `application.yml` |
| BFF proxy controllers | `bff/.../controller/` (forward `/api/v1/**` to RS) |
| Mock-auth users + client | `mock-auth/.../AuthorizationServerConfig.java` |
| React routing + layout | `frontend/src/App.jsx`, `routes/AppLayout.jsx` |
| API per-resource modules | `frontend/src/api/accounts.js`, `transactions.js`, `users.js` |
| Component shells | `frontend/src/components/AccountCard.jsx` etc. |

## What you must build

Every spot you need to fill in is marked with `// TODO` and a Javadoc comment.

| File | What you implement |
|---|---|
| `resource-server/.../service/TransactionService.java` | `applyDeposit`, `applyWithdrawal`, `applyTransferOut` |
| `resource-server/.../security/JwtAuthConverter.java` | `convert(Jwt)` — JWT → local user mapping |
| `resource-server/.../service/TransactionServiceTest.java` | 5 unit tests |
| `resource-server/.../service/PaymentServiceTest.java` | 2 unit tests |
| `resource-server/.../security/JwtAuthConverterTest.java` | 2 unit tests |
| `resource-server/.../controller/AccountControllerIntegrationTest.java` | 3 integration tests |

The entire frontend, the BFF security chain, the WebClient OIDC filter, and
the Resource Server's SecurityFilterChain are all **pre-built**. You will
read them during this tour but will not modify them.

**You write Java; you do not write any React or TypeScript.** The frontend
exercises the BFF pattern that you secure on the backend; you'll demo and
explain it but not author it.

A grep is your friend:

```bash
grep -rn "TODO\|UnsupportedOperationException" backend/ frontend/src/
```

## Tasks

### Task 2.1 — Trace one request end-to-end

Pick `GET /api/v1/accounts`. Open files in this order and note one sentence
about each:

1. `frontend/src/routes/AccountsPage.jsx` — who calls the API?
2. `frontend/src/api/accounts.js` — what URL is hit?
3. `frontend/src/api/apiClient.js` — how does the request carry the session and CSRF token?
4. `frontend/vite.config.js` — where does `/api` proxy to?
5. `bff/.../controller/AccountsBffController.java` — how does the BFF forward?
6. `bff/.../config/WebClientConfig.java` — how does the bearer token get attached?
7. `resource-server/.../controller/AccountController.java` — who handles it on the RS?
8. `resource-server/.../service/AccountService.java` — what does ownership look like?
9. `resource-server/.../repository/AccountRepository.java` — final stop.

If at any point you don't understand what a class does, read its Javadoc.
Every public class in the scaffold has one.

**Why the frontend half matters even though you didn't write it:** the demo
Q&A may include "walk us through how a transaction is submitted" or "what
happens in the SPA when an API call returns 401?" Whichever team member
demos the SPA must be able to point at the relevant lines. Spend the time
here, not during the demo.

### Task 2.2 — Find the seven security gates

The capstone is graded heavily on security. Locate each of these in the code:

1. JWT issuer + audience validated
2. Stateless session policy on the resource server
3. URL-level `hasRole("ADMIN")` rule
4. `@PreAuthorize` method-level rule
5. Ownership check that returns 404 (not 403)
6. CSRF token cookie configuration on the BFF
7. CSRF eager-load filter on the BFF (already wired — find it; understand why)

You won't pass [`../10-definition-of-done.md`](../10-definition-of-done.md) without
all seven. Knowing where they live is half the battle.

### Task 2.3 — Read the schema

Open `resource-server/src/main/resources/db/migration/V1__initial_schema.sql`
and the matching JPA entities under `model/`. Confirm:

- All money columns are `NUMBER(19, 4)`. The Java side uses `BigDecimal`.
- Primary keys are `VARCHAR2` strings — no numeric IDs in URLs.
- `BANK_USERS.SUBJECT` is unique. That's the link between a JWT `sub` and
  a local row.
- The check constraints (status, type, balance ≥ 0) are a safety net, not the
  primary validation. Validate in the service layer **first**.

You'll be referencing this all week.

## Done when

- [ ] Every team member has walked the request flow at least once.
- [ ] You can name all seven security gates without looking.
- [ ] `grep -rn "TODO" backend/ frontend/src/` shows you the work ahead.

Move on to [03-deposit-and-withdrawal.md](./03-deposit-and-withdrawal.md).
