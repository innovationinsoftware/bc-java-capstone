# 08 — Day-by-Day Plan

Three days. Each day has a morning and an afternoon block. The deliverables column lists what should be **demonstrably working** at the end of that block — not "code written." If it doesn't compile, doesn't pass tests, or you can't show it, it doesn't count.

This plan is a guideline, not a contract. Adjust to your team's pace, but don't push security work to Day 3 — there's never time.

## Day 1 — Architecture & implementation

Theme: get the backend reading and writing real data, get the SPA showing accounts. No security yet beyond what the scaffold gave you.

### Morning (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 30 min | Whole-team kickoff: read the spec, read the rubric, decide who owns what. | A 1-page `docs/team-plan.md` listing roles and which spec section each owns. |
| 60 min | Clone scaffold. Verify `mvn test` green and `npm run dev` shows the placeholder page. Connect Oracle. Run `db/migration/V1__initial_schema.sql`. | Backend boots, hits `/health` through the SPA. |
| 60 min | Implement `AccountEntity`, `TransactionEntity`, `BankUserEntity`, repositories. Use Copilot to scaffold; review every file. | All three entities mapped; `mvn test` green. |
| 60 min | Wire `GET /api/v1/accounts` and `GET /api/v1/accounts/{id}` end-to-end against Oracle (skip ownership check for now — placeholder caller user). | curl returns seeded account JSON. |

### Afternoon (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 90 min | Implement `POST /api/v1/transactions` for `DEPOSIT` and `WITHDRAWAL` only. Wire balance update inside `@Transactional`. Insufficient-funds path. | curl can deposit/withdraw; balance reflects in Oracle. |
| 60 min | Implement `GET /api/v1/accounts/{id}/transactions`. | curl returns transaction list ordered desc. |
| 60 min | Frontend: replace placeholder with `AccountsPage` calling `/api/v1/accounts` (no auth yet — point it at the placeholder caller). Show loading and empty states. | SPA at `/` lists accounts. |
| 30 min | Frontend: `AccountDetailPage` route + `TransactionList`. | SPA `/accounts/:id` shows account + its transactions. |

End-of-day-1 self-check:

- [ ] Three Oracle tables exist with seed data.
- [ ] Three GET endpoints return real data through the SPA.
- [ ] `POST /api/v1/transactions` works for deposit and withdrawal.
- [ ] `mvn test` and `npm test` are green (even if test count is low).
- [ ] Everyone has at least one merged commit.

If you don't hit this list, **don't start security tomorrow** — finish today's work first thing.

## Day 2 — Security integration

Theme: every request is authenticated, every authorization rule is enforced, secrets are out of the repo.

### Morning (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 30 min | Register Google OAuth client in Cloud Console. Add redirect URI. Get the client ID into `.env` (frontend) and env var (backend audience check). | A working Google client ID, captured in your `.env.example` (placeholder only — no secrets in repo). |
| 90 min | Backend: configure resource server with `issuer-uri` + `audiences`. Add `JwtAuthConverter` for first-login user creation + role mapping. Lock everything except `/health` behind authentication. | curl with no Bearer = 401; curl with a hand-pasted Google ID token = 200. |
| 60 min | Backend: ownership check in services. `GET /accounts/{notMine}` returns 404. `@PreAuthorize("hasRole('ADMIN')")` on admin endpoints. | Negative tests demonstrate ownership and role enforcement. |
| 60 min | Frontend: install `react-oidc-context`, build `AuthProvider`, `LoginPage`, `CallbackPage`, `RequireAuth`. Wire `apiClient` to attach Bearer token. | Sign-in with Google works end-to-end through the SPA. |

### Afternoon (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 60 min | Implement `TRANSFER_OUT` (internal — both rows in same transaction). Add transfer-group ID. | SPA can transfer between two of caller's own accounts; both rows visible; balances correct. |
| 60 min | Implement `PaymentService` + WireMock stub. `TRANSFER_OUT` to an external `counterparty` calls processor. Failure path marks transaction `FAILED`, no debit. | Manual test with WireMock returning 200 vs 503. |
| 60 min | Implement Kafka publisher. Wire the post-commit publish from `TransactionService`'s caller (or `@TransactionalEventListener`). | Console consumer prints one event per transaction. |
| 30 min | Implement `/admin/users` page (frontend) and `/api/v1/admin/users` (backend). Confirm 403 for non-admin. | An admin user can list users; a customer gets 403. |
| 30 min | CORS lockdown: SPA-origin only. Sweep code for any committed secrets. | grep for `client_secret`, `password=`, `Bearer eyJ` — zero hits. |

End-of-day-2 self-check (security hardening checklist from [04-security.md](./04-security.md)):

- [ ] Issuer, audience, signature, expiry all validated.
- [ ] CSRF disabled, sessions stateless, CORS specific.
- [ ] Ownership checks return 404 (not 403) for non-owned accounts.
- [ ] Admin gates enforced at URL filter **and** method security.
- [ ] No secrets in git. `.env.example` exists; `.env` is `.gitignore`d.
- [ ] Token in sessionStorage, never in URL or logs.

## Day 3 — Testing & security validation

Theme: prove it works, prove it's secure, demo it.

### Morning (≈ 4 hrs)

| Block | Activity | Deliverable |
|---|---|---|
| 90 min | Write/extend unit tests until each row in [07-testing.md](./07-testing.md) "What to cover" table has a test. Use Copilot, review every assertion. | `mvn test` shows ≥ 1 test per service/controller, all green. |
| 90 min | Write integration tests with `@SpringBootTest`, `@EmbeddedKafka`, MockMvc + `.with(jwt())`. At minimum: 401, 403, ownership, deposit happy path with Kafka emission, transfer happy path, insufficient-funds. | All listed scenarios green. |
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

- **Day-1 trap:** Spending three hours debugging an Oracle JDBC connection. If it isn't working in 30 min, ask the instructor — it's almost always a tnsnames/service-name issue, not your code.
- **Day-2 trap:** Spending two hours on Google's OAuth consent screen configuration. The "Testing" mode with team members added as test users is enough; do **not** try to publish the app.
- **Day-3 trap:** Trying to fix every Checkmarx finding. Triage first, fix the highs, accept-with-rationale the rest. Time-box the SAST block to 30 min as written.

## What "behind schedule" looks like

If at the end of Day 1 you don't have GET endpoints reading from Oracle, you're behind. Consider dropping `TRANSFER_OUT` external (the WireMock path) from Day 2 — internal transfer + Kafka is enough to clear "Meets" and you can come back to external if there's time on Day 3.

If at the end of Day 2 you don't have a working Google login, **stop and pair the whole team on it Wednesday morning** before doing anything else. There is no testing rubric to score if the security layer doesn't work.

Next: [Deliverables & Rubric Mapping](./09-deliverables-and-rubric.md).
