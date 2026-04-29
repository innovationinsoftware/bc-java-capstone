# 09 — Deliverables & Rubric Mapping

## What you submit

A single GitHub repo URL containing **everything**:

```
<team-name>-banking/
├── README.md                          # clone-and-run, < 15 min
├── backend/
│   ├── pom.xml                        # parent
│   ├── mock-auth/                     # Spring Authorization Server, port 9000
│   ├── bff/                           # OAuth2 client + WebClient proxy, port 8080
│   └── resource-server/               # banking API, port 8081 (mvn test green)
├── frontend/                          # React + Vite, no OIDC library, npm test green
├── scripts/                           # setup-oracle.sql, start-*.sh helpers
├── wiremock-stubs/                    # Payment Processor stubs
├── docs/
│   ├── architecture.md                # 1-page diagram + design decisions
│   ├── security-decisions.md          # BFF rationale, CSRF, session storage
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
| React components and API integration | Components call API via `api/` modules, not raw `fetch`; loading/error/empty handled. **Same-origin via Vite proxy.** | `routes/`, `components/`, `api/apiClient.js` |
| Account listing and transaction submission views | `AccountsPage`, `AccountDetailPage`, `NewTransactionPage` work; client-side validation matches backend. | `routes/AccountsPage.jsx`, etc. |
| Authenticated UI interactions | Calls go same-origin with cookie; CSRF token attached on mutations; 401 redirects to BFF login URL; **no tokens in JavaScript**. | `api/apiClient.js`, `useMe` hook |

**To exceed:** Accessible form labels; disabled buttons during submit; CSRF cookie/header wired correctly without help; the SPA gracefully shows "Session expired — please sign in again" before redirecting.

### 3. Security Integration (20%)

| Rubric criterion | What it means in the BFF model | Where |
|---|---|---|
| OAuth2 resource server | Resource Server validates JWTs from the Authorization Server; rejects bad ones cleanly. | `resource-server/config/SecurityConfig.java`, `JwtDecoderConfig.java` |
| OAuth login flow via BFF | End-to-end: SPA `<a>` link → BFF `/oauth2/authorization/...` → Auth Server → BFF callback → session cookie → SPA. **No tokens in browser.** | `bff/config/SecurityConfig.java`, `bff/application.yml` |
| RBAC (customer, admin) | `CUSTOMER` and `ADMIN` roles enforced on the Resource Server at URL filter **and** method security. The BFF's session also includes the role for client-side UX gating. | `resource-server/SecurityConfig`, `@PreAuthorize` annotations |
| Endpoint hardening | Issuer + audience + signature + expiry validated by RS; BFF's `client_secret` and Payment Processor API key in env vars only; CSRF protection enabled on the BFF. | `application.yml`, `PaymentService.java`, `docs/security-decisions.md` |

**To exceed:** Switching the BFF from mock-auth to a real IdP (Google, Okta) with documentation; session storage moved to Redis or JDBC; production-grade CSRF / SameSite considerations explicitly discussed; demonstration that XSS in the SPA cannot exfiltrate tokens.

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

1. **(2 min) Architecture intro.** One person draws the four-tier diagram on a whiteboard or screen-shares the doc. Browser ↔ BFF ↔ Resource Server, with the Auth Server off to the side. Where the cookie lives, where the bearer JWT lives, where Kafka fits.
2. **(3 min) Login flow.** Sign in as `alice`. Open DevTools → Application → Cookies. **Show that the only cookies are `JSESSIONID` (HttpOnly) and `XSRF-TOKEN`. Show that sessionStorage and localStorage are empty.** This is the BFF's headline win — make it visible.
3. **(5 min) Customer flow.** List accounts, drill into one, submit a deposit, submit a transfer (internal), watch balances update. Tail the Kafka console consumer to show events arriving keyed by accountId.
4. **(2 min) Admin flow.** Sign out, sign back in as `admin`. Show `/admin/users` works. Sign back in as `alice` and demonstrate the 403.
5. **(2 min) SAST highlight.** Open `docs/sast-findings.md`. Walk through one finding's fix and the rescan.
6. **(2 min) DAST highlight.** Walk through one custom payload — what was sent, what came back, what it told you about the API.
7. **(2 min) Q&A.** Be ready for "Why X?" questions on design decisions — especially "why BFF over pure-SPA".

Demos that try to cover *all* the features in 20 minutes go shallow. Pick your strongest five minutes and rehearse them.

Next: [Definition of Done](./10-definition-of-done.md).
