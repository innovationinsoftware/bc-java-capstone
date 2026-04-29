# 08 — Day-by-Day Plan

Three days. Each day has a morning and an afternoon block. The deliverables column lists what should be **demonstrably working** at the end of that block — not "code written." If it doesn't compile, doesn't pass tests, or you can't show it, it doesn't count.

This plan is a guideline, not a contract. Adjust to your team's pace, but don't push security work to Day 3 — there's never time.

## Day 1 — Resource Server + SPA shell

Theme: get the Resource Server reading/writing Oracle, the SPA rendering accounts. The BFF is already wired in the scaffold — you don't need to log in yet.

### Morning (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 30 min | Whole-team kickoff: read the spec, read the rubric, decide who owns what. | A 1-page `docs/team-plan.md` listing roles and which spec section each owns. |
| 60 min | Clone scaffold. Bring up Oracle. Run `scripts/setup-oracle.sql`. Start mock-auth, resource-server, bff, and frontend (4 terminals). Verify `mvn test` green and the SPA shell renders. | Four services boot. SPA loads at `:5173`. |
| 60 min | Read the existing entity, repository, and service code. Understand `JwtAuthConverter`'s first-login path. | Whole team can describe how a request flows browser → BFF → resource server → DB. |
| 60 min | Verify `GET /api/v1/accounts` and `GET /api/v1/accounts/{id}` work end-to-end with a hand-crafted JWT (the scaffold's `http-tests/banking.http` file). Ownership 404s already wired in `AccountService`. | curl returns seeded account JSON; non-owned account → 404. |

### Afternoon (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 90 min | Verify `POST /api/v1/transactions` for `DEPOSIT` and `WITHDRAWAL` (already implemented). Confirm balance updates and `INSUFFICIENT_FUNDS` returns 422. | Test via `http-tests/banking.http` — both paths green. |
| 60 min | Frontend: flesh out `AccountsPage` and `AccountDetailPage` (loading, empty, error states). Already wired to `/api/v1/accounts` — no token plumbing needed. | SPA at `/` lists accounts after sign-in. |
| 60 min | Day 2 prep: read [04-security.md](./04-security.md) end-to-end. Identify which BFF proxy controllers exist (accounts, transactions, users) and which ones you'll add. | Team alignment on the BFF surface. |
| 30 min | Verify `useMe()` hook + header sign-in/out UX. | Sign-in link visible when signed out; user email visible when signed in. |

End-of-day-1 self-check:

- [ ] Four services run: mock-auth (9000), resource-server (8081), bff (8080), frontend (5173).
- [ ] Sign-in flow works end-to-end: click Sign in → mock-auth login → back to SPA, signed in.
- [ ] DevTools shows a `JSESSIONID` cookie. **No tokens visible in JavaScript.**
- [ ] Three Oracle tables exist with seed data.
- [ ] AccountsPage shows your accounts.
- [ ] DEPOSIT and WITHDRAWAL work end-to-end.
- [ ] `mvn test` is green.
- [ ] Everyone has at least one merged commit.

If you don't hit this list, **don't start the harder security work tomorrow** — finish today's work first thing.

## Day 2 — Transfers, Kafka, role gates

Theme: implement the missing transaction logic, wire Kafka, prove the BFF's session + RBAC story end-to-end.

### Morning (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 90 min | Resource Server: implement `TransactionService.applyTransferOut(...)` for the **internal** path (two rows, same `transferGroupId`, one DB transaction). | SPA can transfer between two of caller's own accounts; both rows visible; balances correct. |
| 60 min | Resource Server: implement `applyTransferOut(...)` **external** path. Calls `PaymentService` (WireMock-stubbed). Failure path marks `FAILED`, no debit. Start WireMock with `scripts/start-wiremock.sh`. | Manual test with WireMock returning 200 vs 503. |
| 60 min | Verify Kafka producer end-to-end. Console-consume the topic; submit transactions; watch events arrive keyed by accountId. | Console consumer prints one event per transaction. |
| 30 min | Add `/api/v1/admin/users` proxy in the BFF. Confirm a `CUSTOMER` session gets 403; an `ADMIN` session gets 200. (Promote a user to `ADMIN` in Oracle: `UPDATE BANK_USERS SET ROLE='ADMIN' WHERE EMAIL='alice@example.com';`) | RBAC confirmed at the API layer. |

### Afternoon (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 60 min | Frontend: build `AdminUsersPage` and the `/admin/users` route gated by `useMe()`. The nav link is hidden for non-admins (UX); the API enforces (security). | Both gates demonstrable. |
| 60 min | Frontend: confirm CSRF on `/logout` works (form post with `_csrf` field). Confirm the SPA's `apiClient` attaches `X-XSRF-TOKEN` on POSTs. | Logout works; mutations work; manual missing-CSRF mutation gets 403. |
| 60 min | Hardening sweep: `grep` repo for committed secrets, raw token strings, and `console.log(...)` of tokens. Configure CSRF cookie + Same-origin path correctly in `application.yml`. | Zero hits. Hardening checklist (in [04-security.md](./04-security.md)) all ✓. |
| 60 min | Buffer: catch up if you're behind, or polish the UX. | — |

End-of-day-2 self-check (BFF security checklist from [04-security.md](./04-security.md)):

- [ ] Sign in works. DevTools shows ONLY `JSESSIONID` and `XSRF-TOKEN` cookies. No tokens in localStorage, sessionStorage, or any JS variable.
- [ ] Resource server: issuer + signature + expiry validated. Audience validated.
- [ ] BFF: client_secret in env var, never in repo. CSRF protection on. Session is HttpOnly.
- [ ] Logout clears the session — next API call returns 401, browser redirects to login.
- [ ] Ownership checks return 404 for non-owned resources.
- [ ] Admin gate enforced at URL filter **and** method security.
- [ ] Internal transfer creates two rows; external transfer's failure path leaves balance unchanged.
- [ ] Kafka events visible in the console consumer.

## Day 3 — Testing & security validation

Theme: prove it works, prove it's secure, demo it.

### Morning (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 90 min | Write/extend unit tests until each row in [07-testing.md](./07-testing.md) "What to cover" table has a test. Use Copilot, review every assertion. | `mvn test` shows ≥ 1 test per service/controller, all green. |
| 90 min | Write integration tests with `@SpringBootTest`, `@EmbeddedKafka`, MockMvc + `.with(jwt())` for the Resource Server. At minimum: 401, 403, ownership, deposit happy path with Kafka emission, transfer happy path, insufficient-funds. Add at least one BFF integration test that boots the BFF and confirms `/api/v1/users/me` returns 401 without a session and the OAuth login URL is exposed. | All listed scenarios green. |
| 30 min | Run Checkmarx SAST. Triage findings into `docs/sast-findings.md`. Remediate the highs. | One row per finding; high-severity all fixed; rescan results recorded. |

### Afternoon (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 60 min | Run DAST baseline (ZAP or instructor tool). Document in `docs/dast-payloads.md`. | Baseline scan complete, findings written up. |
| 60 min | Design and run the three custom-payload classes from [07-testing.md](./07-testing.md) (bulk transfers, malformed inputs, authorization probes). Document each. | Custom payloads section in `docs/dast-payloads.md`. |
| 30 min | Polish: README for clone-and-run instructions; a one-page `docs/architecture.md` with your diagram; sweep TODOs out of code. | A new dev can read the README and run the project in < 15 min. |
| 30 min | Demo dry-run as a team. Decide who speaks for which section. | Demo agenda in `docs/demo-script.md`. |
| 60 min | **Final demo**. Live login, browse, transaction, Kafka event tail, one SAST finding walkthrough, one DAST payload walkthrough, Q&A. | Demo done. |

## Realistic warnings

- **Day-1 trap:** Spending three hours debugging Oracle JDBC. If it isn't working in 30 min, ask the instructor — it's almost always a tnsnames/service-name issue, not your code.
- **Day-1 trap:** Trying to start the BFF before the mock-auth server is up. The BFF will fail to fetch `/.well-known/openid-configuration` and refuse to boot. Always start mock-auth first.
- **Day-2 trap:** Going down the rabbit hole of replacing the mock auth server with a real IdP (Google, Okta). Don't. Document it as future work in `docs/security-decisions.md` and move on.
- **Day-2 trap:** Forgetting the Vite proxy. If the SPA on `:5173` calls `/api/v1/...` and gets a 404, you didn't start the proxy. The browser **must** see the BFF as same-origin.
- **Day-3 trap:** Trying to fix every Checkmarx finding. Triage first, fix the highs, accept-with-rationale the rest.

## What "behind schedule" looks like

If at the end of Day 1 you don't have all four services running and a working sign-in, you're behind. Pair the team on the auth flow Day 2 morning before anything else.

If at the end of Day 2 you don't have internal transfers + Kafka working, drop the external transfer (WireMock path) entirely. Internal transfer + Kafka is enough to clear "Meets."

Next: [Deliverables & Rubric Mapping](./09-deliverables-and-rubric.md).
