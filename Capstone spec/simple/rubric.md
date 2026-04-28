# How You're Graded

The full rubric is in `Capstone Project/Capstone Rubric v2.xlsx`. This is a one-page summary so you know where the points live.

## Weights

| Section | Weight |
|---|---|
| Backend Implementation | 25% |
| Security Integration | 20% |
| Frontend Implementation | 15% |
| Testing & Security Validation | 15% |
| AI-Assisted Development | 10% |
| Code Quality | 10% |
| Collaboration & Presentation | 5% |

## What "Meets Expectations" looks like

**Backend (25%)** — Resource Server's `AccountService` and `TransactionService` work. Endpoints follow the contract with correct status codes and RFC 7807 errors. JPA against Oracle works. Kafka events publish on every completed transaction with the right key, payload, and producer config (`acks=all`, idempotent).

**Security (20%) — BFF model** — BFF runs as an OAuth2 client; tokens are stored server-side; the browser holds only an HttpOnly session cookie. **Spring WebClient with `ServletOAuth2AuthorizedClientExchangeFilterFunction` calls the Resource Server.** Resource Server validates JWTs (issuer + signature + expiry + audience). RBAC (`CUSTOMER`, `ADMIN`) enforced at URL filter and method security. CSRF protection on the BFF.

**Frontend (15%)** — React components call the API via reusable modules; loading/error/empty handled. **No OIDC library in the SPA.** Login is a plain anchor; logout is a form post with the CSRF token. Same-origin via Vite proxy.

**Testing (15%)** — Unit tests cover service logic. Integration tests exercise the full Resource Server stack with `@SpringBootTest` + `@EmbeddedKafka`. At least one integration test on the BFF. Checkmarx SAST run, findings triaged, highs remediated. DAST scan with custom banking payloads.

**AI usage (10%)** — Effective Copilot use across the codebase. You can explain what each piece of generated code does and why you accepted or changed it.

**Code quality (10%)** — Sensible multi-module Maven layout. Clear naming. Foreseeable errors handled. Comments explain *why*, not *what*.

**Collaboration (5%)** — Every team member contributed and can speak to their work in the demo.

## What loses you points fast

- **Tokens in JavaScript** — `sessionStorage`, `localStorage`, or a JS variable. The BFF rubric line specifically grades this.
- **`oidc-client-ts` or `react-oidc-context` in frontend dependencies** — that's a pure-SPA pattern, not BFF.
- A team member with no commits — fails Collaboration (5%) entirely.
- A team member who can't explain their own code in Q&A — fails AI Usage (10%).
- 403 instead of 404 for non-owned resources — leaks data, fails hardening.
- Returning raw exception messages or stack traces in error responses.
- Hardcoded secrets in the repo (Checkmarx will find them in 30 seconds).
- "Tests" that mock the system under test and assert nothing meaningful.
- Forgetting CSRF — mutations succeeding without a CSRF token.

## What gets you to "Exceeds"

- OpenAPI docs (`/swagger-ui.html`) on the Resource Server.
- Integration tests with `@EmbeddedKafka` covering event emission.
- Each Checkmarx finding has a written rationale and a rescan confirming the fix.
- DAST custom payloads probe specific banking attack patterns and document how the API behaved.
- Switched the BFF from mock-auth to a real IdP (Google, Okta) with documentation.
- Session storage moved to Redis or JDBC, with reasoning written up.
- Demonstrated that XSS in the SPA cannot exfiltrate any token (because there are none).
