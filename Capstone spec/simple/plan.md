# 3-Day Plan

A guideline, not a contract. Adjust to your team's pace — but **don't push security to Day 3**, there's never time.

## Day 1 — Get the data flowing

Theme: backend reads/writes Oracle, SPA shows accounts. No real auth yet.

**Morning**
- Kickoff: read the spec and the rubric. Decide who owns what.
- Clone the [scaffold](./scaffolding/). Follow `scaffolding/README.md` to bring up Oracle and start the backend. Verify `GET /health` returns 200.
- Read the entity, repository, and controller code that's already there — you'll be extending it, not rewriting it.
- Run Flyway against your Oracle (the scaffold does this on startup). Add a migration that seeds a couple of `ACCOUNTS` rows owned by your future Google `userId` (you'll know the `userId` after your first login on Day 2 — for Day 1, hand-insert one BANK_USERS row + a few accounts).

**Afternoon**
- Verify `GET /api/v1/accounts` and `GET /api/v1/accounts/{id}` work via the scaffold's `http-tests/banking.http` (using a hand-crafted user row). Ownership and 404-not-403 are already wired in `AccountService`.
- `POST /api/v1/transactions` for DEPOSIT and WITHDRAWAL **already works** — exercise it via curl/HTTP file. Confirm balance updates and `INSUFFICIENT_FUNDS` returns 422.
- Frontend: bring up the SPA (`npm run dev`). The route stubs exist — flesh out the loading/empty/error states in `AccountsPage` and `AccountDetailPage` if they aren't yet.

**End of Day 1, you should have:**
- [ ] Oracle tables exist and are seeded
- [ ] Three GET endpoints return real data
- [ ] Deposit and withdrawal work via curl
- [ ] SPA shows your accounts
- [ ] Every team member has at least one merged commit

## Day 2 — Lock it down

Theme: real Google login, ownership and roles enforced, secrets out of the repo.

**Morning**
- Register the Google OAuth client in Cloud Console (see scaffold README for the exact steps).
- Drop the client ID into `.env` (`GOOGLE_CLIENT_ID`) and `frontend/.env.local` (`VITE_GOOGLE_CLIENT_ID`). The resource server and `JwtAuthConverter` (first-login + role mapping) are **already wired** — restart the backend and they pick up the env var.
- Sign in via the SPA (`/login`). Confirm a row appears in `BANK_USERS` for your Google `sub`. Make yourself ADMIN via SQL if you need it.
- Verify `/admin/users` returns 403 for a customer Google account and 200 for the admin one. Verify accessing another user's `/api/v1/accounts/{id}` returns 404.

**Afternoon**
- Implement internal `TRANSFER_OUT` in `TransactionService.applyTransferOut(...)` (two rows, same `transferGroupId`, one DB transaction). Replace the `UnsupportedOperationException`.
- Implement external `TRANSFER_OUT` — call `PaymentService.submitExternalTransfer(...)`; on failure mark `FAILED`, do not debit, let the exception flow up to a 502 response.
- Verify the Kafka publisher: tail the topic and confirm one event per row appears with the right key.
- CORS is already locked to `SPA_ORIGIN`. `grep` the repo for committed secrets — fix any hits.

**End of Day 2, you should have:**
- [ ] Real Google login works in the SPA
- [ ] Ownership returns 404; admin gate returns 403 for customers
- [ ] Issuer + audience + signature + expiry all validated
- [ ] Kafka events appear when transactions complete
- [ ] No secrets in the repo

## Day 3 — Prove it and demo it

Theme: tests, scans, polish, demo.

**Morning**
- Unit tests for services (deposit, withdrawal, transfer, insufficient funds, ownership).
- Integration tests with `@SpringBootTest` + MockMvc + `.with(jwt())` + `@EmbeddedKafka`. At minimum: 401, 403, ownership, deposit happy path with Kafka emission.
- Run **Checkmarx SAST**. Triage findings into `docs/sast-findings.md`. Fix the highs.

**Afternoon**
- Run **DAST** baseline and the 3 custom payload classes. Document in `docs/dast-payloads.md`.
- Polish: clone-and-run README, one-page architecture doc, sweep TODOs.
- Demo dry-run as a team. Decide who speaks for what.
- **Final demo (15 min).**

## Common traps

- **Day 1:** an hour debugging Oracle JDBC. If it doesn't work in 30 min, ask the instructor.
- **Day 2:** an hour on Google's OAuth consent screen. "Testing" mode with team members as test users is enough — don't try to publish.
- **Day 3:** trying to fix every Checkmarx finding. Triage, fix the highs, rationale for the rest. Time-box it.

## If you're behind

- Drop the **external** transfer (WireMock path). Internal transfer + Kafka still clears "Meets."
- If Day 2 ends without working Google login, **stop everything else Day 3 morning** and pair the whole team on it. There's nothing to test if auth doesn't work.
