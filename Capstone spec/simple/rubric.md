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

**Backend (25%)** — Account service returns owned accounts. Transaction endpoints follow the API contract with correct status codes. Oracle persistence works end-to-end via Spring Data JPA. Kafka events publish on every completed transaction with the right key and payload.

**Security (20%)** — Spring Boot is configured as an OAuth2 Resource Server validating Google's JWTs (issuer + audience + signature + expiry). Real Google login works end-to-end. RBAC with CUSTOMER and ADMIN roles enforced. External payment-processor credentials handled securely (env var, not in repo, not in logs).

**Frontend (15%)** — React components call the API and handle loading/error/empty states. AccountsPage and transaction submission work. Bearer token attached to protected requests; 401 redirects to login.

**Testing (15%)** — Unit tests cover main service/controller logic. Integration tests exercise the full stack against real persistence. Checkmarx SAST run, findings triaged in writing, important ones remediated. DAST scan run with custom banking payloads.

**AI usage (10%)** — You used Copilot effectively across backend, frontend, and tests. You can explain what the generated code does and why you accepted or changed it.

**Code quality (10%)** — Sensible package/folder structure. Clear naming. Foreseeable errors handled with appropriate exceptions. Comments explain *why*, not *what*.

**Collaboration (5%)** — Every team member contributed and can speak to their work in the demo.

## What loses you points fast

- A team member with no commits — fails Collaboration (5%) entirely
- A team member who can't explain their own code in Q&A — fails AI Usage (10%)
- Token in `localStorage`, in a URL, or in logs — fails endpoint hardening
- 403 instead of 404 for non-owned resources — leaks data, fails hardening
- Returning raw exception messages or stack traces in error responses — info disclosure
- Hardcoded secrets in the repo (Checkmarx will find them in 30 seconds)
- "Tests" that mock the system under test and assert nothing meaningful

## What gets you to "Exceeds"

- OpenAPI docs (`/swagger-ui.html`) for the API
- Integration tests with `@EmbeddedKafka` covering event emission
- Each Checkmarx finding has a written rationale and a rescan confirming the fix
- DAST custom payloads probe specific banking attack patterns and document how the API behaved
- Refresh-token handling smoother than full re-login
- Producer failure handling with structured logging and ideas for a recovery path
