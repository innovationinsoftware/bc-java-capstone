# Definition of Done

Run this checklist before you demo. Tick a box only when you've **observed** it working.

## Repo

- [ ] One GitHub repo with `backend/`, `frontend/`, `docs/`
- [ ] `README.md` lets a new dev clone-and-run in under 15 minutes
- [ ] `.env.example` documents every env var; no real secrets committed
- [ ] `git log` shows commits from every team member

## Backend works

- [ ] `mvn test` is green
- [ ] Backend boots against Oracle without errors
- [ ] Every endpoint in [requirements.md](./requirements.md) returns the documented status code

## Frontend works

- [ ] `npm install` and `npm run dev` work from a clean checkout
- [ ] `npm run build` produces a static bundle without errors

## Security works

Run these manually and confirm:

- [ ] `curl /api/v1/accounts` (no token) → **401**
- [ ] `curl` with mutated token signature → **401**
- [ ] Customer hits `/api/v1/admin/users` → **403**
- [ ] Customer hits another customer's account → **404** (not 403)
- [ ] CORS allows your SPA origin only — not `*`
- [ ] `grep -r "client_secret\|password=\|Bearer eyJ" backend/src frontend/src` → no real values
- [ ] No `console.log(token)` or `log.info("token=...")` anywhere

## Functionality works (live, in the SPA)

- [ ] Sign in with a real Google account
- [ ] On first login, a row appears in `BANK_USERS` (verify in Oracle)
- [ ] AccountsPage shows your seeded accounts
- [ ] Submit a deposit → balance updates → Kafka consumer prints the event
- [ ] Submit a withdrawal that exceeds balance → 422 `INSUFFICIENT_FUNDS`, balance unchanged
- [ ] Submit an internal transfer → two rows, same `transferGroupId`, both balances correct
- [ ] Submit external transfer with WireMock returning 503 → `FAILED`, no debit
- [ ] Sign out → sessionStorage cleared, app redirects to `/login`
- [ ] Log in as admin → `/admin/users` works
- [ ] Log in as customer → `/admin/users` blocked

## Tests + scans

- [ ] Unit tests cover service logic (deposit, withdrawal, transfer, insufficient funds, ownership)
- [ ] Integration tests cover 401, 403, ownership, deposit happy path, Kafka emission
- [ ] Checkmarx scan run; `docs/sast-findings.md` has one row per finding; highs remediated
- [ ] DAST baseline + 3 custom payloads documented in `docs/dast-payloads.md`

## Docs

- [ ] `docs/architecture.md` — one diagram + one page of explanation
- [ ] `docs/sast-findings.md` — Checkmarx triage table
- [ ] `docs/dast-payloads.md` — baseline + custom payload writeups

## Demo readiness

- [ ] Backend, frontend, and Kafka consumer can be brought up from a clean shell in under 5 minutes
- [ ] You have a known-good Google account ready to sign in with
- [ ] Every team member knows which 2–3 minutes of the demo they own
- [ ] At least one full dry-run of the demo has happened

If every box is checked, you're ready.
