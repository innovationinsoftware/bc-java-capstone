# 10 — Definition of Done

This is the single checklist your team runs before declaring "we're ready to demo." If anything here is unchecked, you are not done — even if you've run out of time.

Tick each box only when you have **observed** it working, not when you believe the code is right.

## Repository

- [ ] Single GitHub repo, public or instructor-accessible.
- [ ] `README.md` at root with: prerequisites, env vars, run-the-backend, run-the-frontend, run-the-tests. A new dev can clone and run in under 15 minutes.
- [ ] `.env.example` documents every env var; no real secrets committed.
- [ ] `git log --pretty=format:"%an"` shows commits from **every** team member. Run it.
- [ ] No leftover `// TODO`s in code that would block grading.

## Backend

- [ ] `mvn -f backend/pom.xml test` (multi-module build) runs to completion and is green for all three modules.
- [ ] All four services start cleanly: mock-auth (9000), resource-server (8081), bff (8080), frontend (5173).
- [ ] All endpoints in [API Contract](./03-api-contract.md) return the documented shape and status codes when called via the BFF.
- [ ] No `application.yml` in any module has a hard-coded `client_secret`, password, or API key.
- [ ] `BANK_USERS`, `ACCOUNTS`, `TRANSACTIONS` tables exist with constraints from [Domain Model](./02-domain-model.md).
- [ ] `BigDecimal` is used everywhere money is stored or computed. No `double`.

## Frontend

- [ ] `npm install` from a clean checkout works.
- [ ] `npm run dev` boots and serves on http://localhost:5173.
- [ ] Vite proxy forwards `/api/**`, `/login/**`, `/logout`, `/oauth2/**` to the BFF on `:8080`.
- [ ] `npm run build` produces a static bundle without errors.
- [ ] **`grep -r "oidc-client-ts\|react-oidc-context" frontend/src` returns nothing.** No OAuth library in the SPA.

## Security

- [ ] Sign in works end-to-end (browser → BFF → mock-auth → BFF → SPA).
- [ ] DevTools shows **only** `JSESSIONID` (HttpOnly) and `XSRF-TOKEN` cookies. sessionStorage and localStorage are empty.
- [ ] `curl http://localhost:8081/api/v1/accounts` direct to Resource Server (no Bearer) → 401.
- [ ] `curl http://localhost:8080/api/v1/accounts` direct to BFF (no cookie) → 401 (or 302 to login, depending on config).
- [ ] Customer hitting `/api/v1/admin/users` → 403.
- [ ] Customer hitting another customer's `/api/v1/accounts/{id}` → 404 (not 403).
- [ ] Sign out → `/api/v1/users/me` returns 401.
- [ ] `grep -r "client_secret\|password=\|Bearer eyJ" backend/*/src frontend/src` returns no real values.
- [ ] No `console.log(token)`, `log.info("token=...")`, or any other token logging.
- [ ] Payment processor API key is read from env var, not hard-coded.
- [ ] CSRF: a `POST` with no `X-XSRF-TOKEN` header → 403. With the right header → 200.

## Functionality (live)

Walk through these in the SPA, end to end. Tick when you've personally seen it work.

- [ ] Sign in as `alice`. A `BANK_USERS` row appears (verify in Oracle).
- [ ] `/api/v1/users/me` (via the SPA) returns the user with role `CUSTOMER`.
- [ ] AccountsPage shows your accounts.
- [ ] AccountDetailPage shows transactions ordered desc.
- [ ] Submit a deposit — balance updates, transaction appears, Kafka consumer prints the event.
- [ ] Submit a withdrawal that exceeds the balance — get 422 with `INSUFFICIENT_FUNDS`, balance unchanged.
- [ ] Submit an internal transfer — both rows appear with the same `transferGroupId`, both balances correct.
- [ ] Submit an external transfer to a fake counterparty — WireMock 200 path → completes; WireMock 503 path → fails, no debit.
- [ ] Sign out → cookie cleared, the next API call from the SPA returns 401, browser redirects to login.
- [ ] Sign in as `admin` — `/admin/users` works.
- [ ] Sign in as `alice` — direct API call to `/api/v1/admin/users` returns 403, and the nav link is hidden.

## Testing & security validation

- [ ] Every row of the unit-test table in [07-testing.md](./07-testing.md) has at least one passing test.
- [ ] Integration tests: 401, 403, ownership, deposit happy path, transfer happy path, insufficient funds, Kafka emission — all green.
- [ ] Checkmarx scan run; `docs/sast-findings.md` populated; high-severity remediated; rescan results recorded.
- [ ] DAST baseline scan run; `docs/dast-payloads.md` baseline section populated.
- [ ] At least three custom DAST payloads run and documented (bulk transfer, malformed input, authorization probe).

## Documentation

- [ ] `docs/architecture.md` — one diagram, one page of explanation.
- [ ] `docs/security-decisions.md` — short paragraphs on token storage, RBAC enforcement, CORS, payment processor credentials, error response policy.
- [ ] `docs/sast-findings.md` — table with one row per finding.
- [ ] `docs/dast-payloads.md` — baseline + custom payloads with expected vs actual.
- [ ] `docs/team-plan.md` — who owned what.
- [ ] `docs/demo-script.md` — agenda your team will follow during the demo.

## Demo readiness

- [ ] Backend, frontend, and Kafka console consumer can all be brought up from a clean shell in under 5 minutes.
- [ ] You have a known-good test Google account ready to sign in with.
- [ ] At least one team member has practiced the demo from start to finish.
- [ ] You have a fallback plan if Wi-Fi flakes — local screenshots or a recorded walkthrough.
- [ ] Every team member knows which 2–3 minutes of the demo they are presenting.

When every box is checked, you're ready. If you're not, work the unchecked items in order — most of them block the others.

Next: [Appendix](./11-appendix.md).
