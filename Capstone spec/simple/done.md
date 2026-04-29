# Definition of Done

Run this checklist before you demo. Tick a box only when you've **observed** it working.

## Repo

- [ ] One GitHub repo with `backend/` (multi-module: mock-auth, bff, resource-server), `frontend/`, `docs/`
- [ ] `README.md` lets a new dev clone-and-run in under 15 minutes
- [ ] `.env.example` documents every env var; no real secrets committed
- [ ] `git log` shows commits from every team member

## Backend works

- [ ] `mvn test` (multi-module) is green
- [ ] All four services boot cleanly: mock-auth (9000), resource-server (8081), bff (8080), frontend (5173)
- [ ] Every endpoint in [requirements.md](./requirements.md) returns the documented status code through the BFF

## Frontend works

- [ ] `npm install` and `npm run dev` work from a clean checkout
- [ ] Vite proxy forwards `/api/**`, `/login/**`, `/logout`, `/oauth2/**` to the BFF on `:8080`
- [ ] `npm run build` produces a static bundle without errors
- [ ] **`grep -r "oidc-client-ts\|react-oidc-context" frontend/src` returns nothing.** No OAuth library in the SPA.

## Security works (the BFF wins)

The headline check:

- [ ] Sign in as `alice`. Open DevTools → Application. **Cookies show only `JSESSIONID` (HttpOnly) and `XSRF-TOKEN`. sessionStorage and localStorage are empty.**

Plus:

- [ ] `curl http://localhost:8081/api/v1/accounts` direct to RS (no Bearer) → 401
- [ ] Customer hits `/api/v1/admin/users` → 403
- [ ] Customer hits another customer's account → 404 (not 403)
- [ ] Sign out → next API call returns 401, browser redirects to login
- [ ] POST without `X-XSRF-TOKEN` header → 403; with the right header → succeeds
- [ ] `grep -r "client_secret\|password=\|Bearer eyJ" backend/*/src frontend/src` → no real values
- [ ] No `console.log(token)` or `log.info("token=...")` anywhere
- [ ] BFF `client_secret` and Payment Processor API key in env vars only

## Functionality works (live, in the SPA)

- [ ] Sign in as `alice`. A row appears in `BANK_USERS` (verify in Oracle)
- [ ] AccountsPage shows your seeded accounts
- [ ] Submit a deposit → balance updates → Kafka consumer prints the event
- [ ] Submit a withdrawal that exceeds balance → 422 `INSUFFICIENT_FUNDS`, balance unchanged
- [ ] Submit an internal transfer → two rows, same `transferGroupId`, both balances correct
- [ ] Submit external transfer with WireMock returning 503 → `FAILED`, no debit
- [ ] Sign out via the form button → cookie cleared, app redirects to login
- [ ] Sign in as `admin` → `/admin/users` works
- [ ] Sign in as `alice` → `/admin/users` blocked (server returns 403; nav link hidden)

## Tests + scans

- [ ] Unit tests cover RS service logic (deposit, withdrawal, transfer, insufficient funds, ownership)
- [ ] Integration tests cover 401, 403, ownership, deposit happy path, Kafka emission
- [ ] At least one BFF integration test (e.g., `/api/v1/users/me` is 401 without session)
- [ ] Checkmarx scan run; `docs/sast-findings.md` has one row per finding; highs remediated
- [ ] DAST baseline + 3 custom payloads documented in `docs/dast-payloads.md`

## Docs

- [ ] `docs/architecture.md` — diagram showing browser ↔ BFF ↔ RS ↔ Auth Server + 1 page of explanation
- [ ] `docs/security-decisions.md` — why BFF, where tokens live, CSRF wiring, etc.
- [ ] `docs/sast-findings.md` — Checkmarx triage table
- [ ] `docs/dast-payloads.md` — baseline + custom payload writeups

## Demo readiness

- [ ] All four services can be brought up from a clean shell in under 5 minutes
- [ ] At least one team member has practiced the demo from start to finish
- [ ] Every team member knows which 2–3 minutes of the demo they own
- [ ] You have the "open DevTools, show no tokens in JS" moment rehearsed — that's the BFF win

If every box is checked, you're ready.
