# 3-Day Plan

A guideline. Adjust to your team's pace.

## Day 1 — Get all four services running, sign in works

Theme: stand up the stack, verify the BFF flow end-to-end as a starting point. The scaffold has the OAuth wiring done — you mostly verify and explore.

**Morning**
- Read the spec and the rubric. Decide who owns what.
- Run `scripts/verify-prereqs.ps1`. Resolve any `[FAIL]` rows before going further.
- Run `scripts/setup-oracle.sql` once. Bring up four terminals (`.ps1` on Windows, `.sh` on bash/Git Bash):
  - Terminal 1: `start-mock-auth` (port 9000, must start first)
  - Terminal 2: `start-resource-server` (port 8081)
  - Terminal 3: `start-bff` (port 8080)
  - Terminal 4: `start-frontend` (port 5173)
- Open `http://localhost:5173`. Click Sign in. Log in as `alice`. Confirm you land back signed in.
- Open DevTools → Application. Confirm cookies show `JSESSIONID` (HttpOnly) and `XSRF-TOKEN`. Confirm `sessionStorage` and `localStorage` are empty.

**Afternoon**
- Read the existing entity, service, and controller code in `resource-server/`. Trace a request: SPA → BFF proxy controller → WebClient call → Resource Server → DB.
- Verify `GET /api/v1/accounts` and `GET /api/v1/accounts/{id}` work (already implemented).
- Verify `POST /api/v1/transactions` for DEPOSIT and WITHDRAWAL (already implemented).
- Frontend: flesh out `AccountsPage` and `AccountDetailPage` (loading/empty/error states).

**End of Day 1, you should have:**
- [ ] Four services running, sign-in flow works
- [ ] DevTools confirms NO tokens in JS storage
- [ ] DEPOSIT and WITHDRAWAL work end-to-end through the SPA
- [ ] Every team member has at least one merged commit

## Day 2 — Transfers, Kafka, RBAC

Theme: implement the missing transaction logic, prove RBAC + CSRF.

**Morning**
- Resource Server: implement `TransactionService.applyTransferOut(...)` for **internal** transfers (two rows, one DB transaction, shared `transferGroupId`).
- Resource Server: implement external `TRANSFER_OUT` calling the WireMock-stubbed Payment Processor. Failure path → `FAILED`, no debit. Start WireMock with `./scripts/start-wiremock.sh`.
- Verify Kafka events with a console consumer.

**Afternoon**
- Add `/api/v1/admin/users` proxy on the BFF. Promote a user to ADMIN: `UPDATE BANK_USERS SET ROLE='ADMIN' WHERE EMAIL='...';`
- Frontend: `AdminUsersPage`. Hide the nav link via `useMe()` for non-admins (UX). API rejects with 403 (security).
- Confirm CSRF: a POST without `X-XSRF-TOKEN` returns 403; logout works via the form post.
- Hardening sweep: `grep -r "client_secret\|password=\|Bearer eyJ"` returns zero hits.

**End of Day 2, you should have:**
- [ ] Internal + external transfers working
- [ ] Kafka events arriving when transactions complete
- [ ] CSRF enforced on mutations
- [ ] RBAC enforced (CUSTOMER vs ADMIN, both at URL and method levels)
- [ ] No secrets in the repo

## Day 3 — Prove it and demo it

Theme: tests, scans, polish, demo.

**Morning**
- Unit tests for Resource Server services (deposit, withdrawal, transfer, insufficient funds, ownership).
- Integration tests with `@SpringBootTest` + MockMvc + `.with(jwt())` + `@EmbeddedKafka`. Plus one BFF integration test confirming `/api/v1/users/me` is 401 without a session.
- Run **Checkmarx SAST**. Triage findings into `docs/sast-findings.md`. Fix the highs.

**Afternoon**
- Run **DAST** baseline + 3 custom payloads. Document in `docs/dast-payloads.md`.
- Polish: clone-and-run README, one-page architecture doc, sweep TODOs.
- Demo dry-run as a team. Decide who speaks for what.
- **Final demo (15 min).** Centerpiece: open DevTools and show that there are no tokens in JS — only the cookie.

## Common traps

- **Day 1:** Starting BFF before mock-auth is up. The BFF will fail to fetch `/.well-known/openid-configuration` and refuse to boot. Always start mock-auth first.
- **Day 1:** Forgetting the Vite proxy. If `/api/...` calls return 404 from the SPA, the proxy isn't running. The browser must see same-origin.
- **Day 2:** Trying to swap the mock auth server for Google. Don't, on Day 2. Document it as future work and move on.
- **Day 3:** Trying to fix every Checkmarx finding. Triage, fix the highs, rationale for the rest.

## If you're behind

- Drop the **external** transfer (WireMock path). Internal transfer + Kafka still clears "Meets."
- If Day 1 ends without a working sign-in, **stop everything else Day 2 morning** and pair the whole team on it. There's nothing to test if auth doesn't work.
