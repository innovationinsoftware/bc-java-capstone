# 09 — Deliverables & Rubric Mapping

## What you submit

A single GitHub repo URL containing **everything**:

```
<team-name>-banking/
├── README.md                          # clone-and-run, < 15 min
├── backend/                           # Spring Boot project, mvn test green
├── frontend/                          # React app, npm test green
├── docs/
│   ├── architecture.md                # 1-page diagram + design decisions
│   ├── security-decisions.md          # how you handled token lifecycle, CORS, RBAC
│   ├── sast-findings.md               # Checkmarx triage table
│   ├── dast-payloads.md               # baseline + custom payload writeups
│   ├── team-plan.md                   # who owns what
│   └── demo-script.md                 # ordered demo steps
├── .env.example                       # all required env vars, with placeholders
└── .github/workflows/ci.yml           # optional but appreciated
```

## How the rubric maps to your deliverables

The rubric has 7 sections weighted as below. Total = 100%.

| Section | Weight | Where it's graded |
|---|---|---|
| Backend Implementation | 25% | `backend/` code; running demo |
| Frontend Implementation | 15% | `frontend/` code; running demo |
| Security Integration | 20% | `backend/security/`, `backend/config/`, frontend `auth/`, `docs/security-decisions.md` |
| Testing & Security Validation | 15% | `backend/src/test/`, `frontend/src/__tests__/`, `docs/sast-findings.md`, `docs/dast-payloads.md` |
| AI-Assisted Development | 10% | Code reviewed during demo Q&A; commit messages; what Copilot output you kept vs rejected |
| Code Quality | 10% | Whole repo |
| Collaboration & Presentation | 5% | Git history (commits per team member, PR comments); the demo itself |

### 1. Backend Implementation (25%)

| Rubric criterion | What it means in this capstone | Where to demonstrate |
|---|---|---|
| Account service logic (Copilot-assisted) | `AccountService` returns owned accounts; ownership-violation path returns 404; tests pass. | `service/AccountService.java`, related unit tests |
| Transaction REST endpoints | `POST /api/v1/transactions` and `GET /api/v1/.../transactions` follow [API contract](./03-api-contract.md): correct status codes, RFC 7807 errors, validation. | `controller/TransactionController.java`, `dto/`, `exception/GlobalExceptionHandler.java` |
| Oracle persistence | JPA entities map to schema cleanly; CRUD works end-to-end; `@Transactional` boundaries are correct. | `model/`, `repository/`, service `@Transactional` annotations |
| Kafka transaction events | Each completed transaction emits one event with right key, payload, and producer config. | `kafka/`, `application.yml` producer block, integration test with `@EmbeddedKafka` |

**To exceed:** OpenAPI docs (`/swagger-ui.html`); idempotent POST handling; explicit JOIN FETCH against N+1; producer failure handling with structured logging.

### 2. Frontend Implementation (15%)

| Rubric criterion | What it means | Where |
|---|---|---|
| React components and API integration | Components call API via `api/` modules, not raw `fetch` in components; loading/error/empty handled. | `routes/`, `components/`, `api/apiClient.ts` |
| Account listing and transaction submission views | `AccountsPage`, `AccountDetailPage`, `NewTransactionPage` work; client-side validation matches backend. | `routes/AccountsPage.tsx`, etc. |
| Authenticated UI interactions | Bearer token attached to every protected request; 401 redirects to login; protected routes guarded with `RequireAuth`/`RequireRole`. | `auth/`, `apiClient`, `RequireAuth` |

**To exceed:** Disabled buttons during submit; accessible form labels; token expiry handling smoother than full re-login; no token in any log statement.

### 3. Security Integration (20%)

| Rubric criterion | What it means | Where |
|---|---|---|
| OAuth2 resource server | Spring service validates Google JWTs; rejects bad ones cleanly. | `config/SecurityConfig.java`, `application.yml` |
| Google OAuth identity provider and login flow | Real end-to-end: `/login` → Google consent → `/callback` → calling protected API. | `auth/AuthProvider.tsx`, `routes/CallbackPage.tsx`, `JwtAuthConverter` |
| RBAC (customer, admin) | `CUSTOMER` and `ADMIN` roles, with admin endpoints denied to customers at filter and method-security layers. | `SecurityConfig`, `@PreAuthorize` on admin controllers |
| Endpoint hardening and token validation | Issuer + audience + signature + expiry all validated; payment processor API key in env, never logged. | `application.yml`, `PaymentService.java`, `docs/security-decisions.md` |

**To exceed:** Scope-based gates layered on top of role-based; token expiry handled gracefully; specific resistance to replay/substitution attacks demonstrated in tests.

### 4. Testing & Security Validation (15%)

| Rubric criterion | What it means | Where |
|---|---|---|
| Unit tests | Reasonable coverage of service + controller logic, deterministic, independent. | `backend/src/test/java/.../service/`, `.../controller/` |
| Integration tests | API → service → DB end-to-end. Includes auth scenarios and Kafka emission. | `backend/src/test/java/.../IntegrationTest.java` |
| SAST scan | Checkmarx ran; findings triaged; highs remediated. | `docs/sast-findings.md` |
| DAST scan & custom payloads | DAST ran; custom banking payloads designed; behaviour documented. | `docs/dast-payloads.md` |

### 5. AI-Assisted Development (10%)

This one is **graded during demo Q&A**, not via static reading. Be ready to answer:

- Show me one piece of code Copilot generated and one piece it generated badly that you fixed.
- Why is this method written this way?
- What did Copilot suggest here, and why did you change it?

If a team member can't answer those for code with their name on the commit, they fail this section.

### 6. Code Quality (10%)

| Rubric criterion | What it means | Where |
|---|---|---|
| Project organization and naming | Layout matches [01-architecture.md](./01-architecture.md); names reflect purpose. | Whole repo |
| Exception handling and resilience | Foreseeable failures handled; logs include context; no leaking stack traces. | `GlobalExceptionHandler`, service exception types |
| Readability and idiomatic style | Clean Java + React; comments explain *why*, not *what*; no committed dead Copilot code. | Whole repo |

### 7. Collaboration & Presentation (5%)

| Rubric criterion | What it means | Where |
|---|---|---|
| Collaboration | Every team member has commits and reviewed at least one PR. | `git shortlog`, PR history |
| Final system demo | Logical flow, smooth recovery from glitches, can answer design-decision questions. | The live demo |

A team where one person did 90% of commits **fails** this slice — even if everything else is perfect.

## Demo expectations (15–20 min)

A working agenda template, in `docs/demo-script.md`. Allocate roughly:

1. **(2 min) Architecture intro.** One person explains the diagram. Who calls who, where the JWT goes, where Kafka fits.
2. **(3 min) Login flow.** Sign in with a real Google account. Show the JWT in browser DevTools (sessionStorage). Hit a protected endpoint with the token, then without the token.
3. **(5 min) Customer flow.** List accounts, drill into one, submit a deposit, submit a transfer (internal), watch the balances update. Show the Kafka console consumer printing the events in real time.
4. **(2 min) Admin flow.** Log in as admin (use a separate Google account if you can; otherwise grant admin in DB and re-login). Show `/admin/users`. Log back in as customer and demonstrate the 403.
5. **(2 min) SAST highlight.** Open `docs/sast-findings.md`. Walk through one finding's fix and the rescan.
6. **(2 min) DAST highlight.** Walk through one custom payload — what was sent, what came back, what it told you about the API.
7. **(2 min) Q&A.** Be ready for "Why X?" questions on design decisions.

Demos that try to cover *all* the features in 20 minutes go shallow. Pick your strongest five minutes and rehearse them.

Next: [Definition of Done](./10-definition-of-done.md).
