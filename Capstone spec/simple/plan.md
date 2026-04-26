# 3-Day Plan

A guideline, not a contract. Adjust to your team's pace — but **don't push security to Day 3**, there's never time.

## Day 1 — Get the data flowing

Theme: backend reads/writes Oracle, SPA shows accounts. No real auth yet.

**Morning**
- Kickoff: read the spec and the rubric. Decide who owns what.
- Clone the scaffold. Get backend booting against Oracle. Run the schema migrations.
- Implement JPA entities and repositories for `BANK_USERS`, `ACCOUNTS`, `TRANSACTIONS`.
- Wire `GET /api/v1/accounts` and `GET /api/v1/accounts/{id}` end-to-end.

**Afternoon**
- Implement `POST /api/v1/transactions` for `DEPOSIT` and `WITHDRAWAL`. Use `@Transactional`. Handle insufficient funds (422).
- Implement `GET /api/v1/accounts/{id}/transactions`.
- Frontend: `AccountsPage` and `AccountDetailPage` calling the API. Show loading/empty/error states.

**End of Day 1, you should have:**
- [ ] Oracle tables exist and are seeded
- [ ] Three GET endpoints return real data
- [ ] Deposit and withdrawal work via curl
- [ ] SPA shows your accounts
- [ ] Every team member has at least one merged commit

## Day 2 — Lock it down

Theme: real Google login, ownership and roles enforced, secrets out of the repo.

**Morning**
- Register the Google OAuth client in Cloud Console.
- Backend: configure resource server (issuer-uri, audiences). Add `JwtAuthenticationConverter` that creates a `BANK_USERS` row on first login and maps the role.
- Backend: ownership check in services (404 for non-owned). `@PreAuthorize("hasRole('ADMIN')")` on admin endpoints.
- Frontend: install `react-oidc-context`, build `AuthProvider`, `LoginPage`, `CallbackPage`, `RequireAuth`. Make `apiClient` attach the Bearer token.

**Afternoon**
- Implement internal `TRANSFER_OUT` (two rows, same `transferGroupId`, one transaction).
- Implement external `TRANSFER_OUT` calling the WireMock-stubbed Payment Processor. Failure path marks `FAILED`, no debit.
- Implement Kafka publisher. Console-consume the topic and verify events appear.
- Implement `/admin/users` page and endpoint. Confirm 403 for customers.
- CORS lockdown. `grep` the repo for committed secrets — fix any hits.

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
