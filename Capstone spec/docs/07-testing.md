# 07 — Testing & Security Validation

The rubric's Testing & Security Validation slice is 15%. Treat it as four sub-deliverables: unit tests, integration tests, a SAST scan with written triage, and a DAST scan with custom payloads.

## What "Meets" looks like

- Unit tests over the main service and controller logic, generated with Copilot **assistance**, then reviewed and edited.
- Integration tests that exercise REST → service → persistence end-to-end (real Oracle or Testcontainers).
- One run of the **Checkmarx SAST** scan against the backend, with each finding triaged in writing as fix / accept / defer.
- One run of a **DAST** scan against the running API, with at least one custom banking payload designed by the team.

## What "Exceeds" looks like

- Edge-case unit tests beyond the happy path: ownership violations, boundary balances (`0.00`, exactly the balance, balance + 0.01), validation failures.
- Integration tests cover security paths (authenticated vs not, role checks, scope mismatches) and Kafka emission.
- SAST findings are rescanned after fix to confirm closure, with a one-paragraph rationale per finding.
- DAST custom payloads probe specific banking attack patterns (bulk transfers, scheduled-payment payloads with malformed dates, race-condition concurrent requests against the same account). Document how each was handled.

## Unit tests

Stack: JUnit 5, Mockito (transitive via `spring-boot-starter-test`).

What to cover, by class:

| Class | Tests |
|---|---|
| `AccountService` | Returns owner's accounts; rejects non-owner with 404 path; handles empty list |
| `TransactionService.submit` (deposit) | Happy path increments balance, inserts row, returns DTO |
| `TransactionService.submit` (withdrawal) | Happy path; insufficient-funds throws; below-zero validation |
| `TransactionService.submit` (internal transfer) | Both rows inserted, share `transferGroupId`, balances correct |
| `TransactionService.submit` (external transfer) | Calls `PaymentService`; on success completes; on processor failure marks `FAILED` and does **not** debit |
| `PaymentService` | Calls processor with API key in header; timeouts produce `PaymentProcessorException`; 5xx produces same |
| `JwtAuthConverter` | First-login creates a `BANK_USERS` row; subsequent login returns existing user; admin role mapped to `ROLE_ADMIN` |
| `GlobalExceptionHandler` | Each mapped exception → expected status + RFC 7807 envelope |
| `TransactionEventPublisher` | Publishes with correct topic, key (= accountId), payload |

Use Copilot to generate the test scaffolds, then **read** what it produced. Reject tests that:

- Just verify a method was called without asserting outputs.
- Mock the class under test.
- Assert on tautologies (`assertThat(x).isEqualTo(x)`).

These are the failures the rubric's "Critical review of AI-generated code" line is grading. Copilot generates them often.

## Integration tests

Stack: Spring Boot Test (`@SpringBootTest`), MockMvc for the HTTP layer, real Oracle or Testcontainers for the DB, `spring-kafka-test`'s `@EmbeddedKafka` for the broker, WireMock for the Payment Processor.

Note: the bootcamp covered `@SpringBootTest` + MockMvc lightly and WireMock in detail (Module 4); Testcontainers was not covered. If you reach for Testcontainers you're heading for "Exceeds" — fine, but allocate time.

Minimum integration test surface:

```java
@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
@EmbeddedKafka(topics = "transactions.completed")
class TransactionFlowIntegrationTest {

    @Test void unauthenticated_returns_401();
    @Test void wrong_role_on_admin_endpoint_returns_403();
    @Test void list_accounts_returns_only_callers_accounts();
    @Test void get_other_users_account_returns_404();
    @Test void submit_deposit_happy_path_inserts_row_updates_balance_emits_event();
    @Test void submit_withdrawal_below_balance_returns_422();
    @Test void submit_external_transfer_processor_503_marks_failed_no_debit();
    @Test void submit_internal_transfer_creates_two_rows_same_transfer_group();
}
```

For the JWT layer, use Spring Security Test's `.with(jwt())` MockMvc post-processor. **Don't** spin up a real Google login in tests.

```java
mockMvc.perform(get("/api/v1/accounts")
        .with(jwt().jwt(j -> j.subject("test-google-sub").claim("email", "alice@example.com"))))
       .andExpect(status().isOk());
```

Custom JWT in tests bypasses the issuer check; that's exactly what you want — you're testing your authorization logic, not Google's signing.

## Checkmarx SAST scan

The instructor will provide access credentials and a one-page run-book. The scan is **expected**, not optional. Run it on Day 3, morning.

### How to run (briefly)

1. Push your latest backend to GitHub.
2. Trigger the scan from the Checkmarx UI / CLI per the run-book.
3. Wait for the report.

### How to triage

Open `docs/sast-findings.md`. For **every** finding:

| Field | Example |
|---|---|
| Finding ID | CX-12345 |
| Severity | High |
| Category | Hardcoded Password |
| File:line | `application-dev.yml:7` |
| Decision | Fix / Accept / Defer |
| Rationale | "Test fixture password — not used in any deployed environment. Removed from repo and replaced with `${TEST_DB_PASSWORD}` env var." |
| Rescan result (if Fix) | Closed in CX-12350 |

The "Meets" rubric line says you must remediate the most important vulnerabilities. The "Exceeds" line wants written rationale **per finding** plus a rescan confirming the fix. Aim for that.

### Common findings to expect

- **Hardcoded credentials** in test files or `application.yml` — fix by moving to env vars.
- **Logging sensitive data** — `log.info("token={}", token)` shows up immediately. Remove.
- **SQL injection** — should be zero findings if you use Spring Data JPA repositories. If Copilot generated raw `JdbcTemplate.query("SELECT ... " + userInput)` somewhere, fix it.
- **Permissive CORS** — if you set `allowedOrigins("*")`, Checkmarx will flag it.
- **Missing authentication** — if you used `permitAll()` more broadly than `/health`, fix.
- **Information exposure via errors** — if your exception handler echoes the raw `e.getMessage()` for unhandled exceptions, fix.
- **Use of `double` for currency** — flagged as imprecise. The schema and DTOs use `BigDecimal`; if Copilot regressed any to `double`, fix.

## DAST scan

Tool of choice: OWASP ZAP (open source) or whatever the instructor provides. Run it against `http://localhost:8081` while the API is up.

### Baseline scan

1. Get a valid Bearer token (sign in to the SPA, copy from sessionStorage).
2. Configure ZAP with the token in a "Replacer" or "Authentication" rule so every probe carries it.
3. Run a baseline active scan against `/api/v1`.
4. Review findings.

Document the baseline run in `docs/dast-payloads.md` with the same fix/accept/defer structure as SAST.

### Custom banking payloads

The rubric ("Meets") explicitly asks for **custom payloads for specialised banking use cases**. Three classes you should design:

#### 1. Bulk-transfer abuse

Submit 50 `WITHDRAWAL` requests as fast as you can to the same account. Expected behaviour:

- All succeed in commit order until the balance is exhausted, then return 422 `INSUFFICIENT_FUNDS`.
- No transaction lands when the balance would go negative.
- Document what actually happened. If you observe race conditions where the balance briefly goes negative, that's a finding — write it up and remediate (e.g., pessimistic lock or atomic balance update).

#### 2. Scheduled-payment payload variations

The capstone does not require scheduled payments, but `description` and `counterparty` accept user input. Send payloads that try to break parsing or escape:

```json
{ "accountId":"acc_001", "type":"DEPOSIT", "amount": 1.00,
  "description": "<script>alert(1)</script>" }
```

```json
{ "accountId":"acc_001", "type":"WITHDRAWAL", "amount": 1.00,
  "description": "'; DROP TABLE TRANSACTIONS; --" }
```

```json
{ "accountId":"acc_001", "type":"WITHDRAWAL", "amount": "Infinity" }
```

```json
{ "accountId":"acc_001", "type":"WITHDRAWAL", "amount": -1.00 }
```

```json
{ "accountId":"acc_001", "type":"WITHDRAWAL", "amount": 1.0000000001 }
```

For each, document: what was sent, what the API returned, whether the response leaks any info, and whether the database state is consistent. The rubric's "Exceeds" wants a written paragraph per payload class — give it that.

#### 3. Authorization probes

- Submit a transaction with `accountId` belonging to a different user. Expected: 404. Confirm the API does not return 403 (which would confirm the account exists).
- Hit `/api/v1/admin/users` as a `CUSTOMER`. Expected: 403.
- Replay an old (expired) JWT. Expected: 401.
- Mutate the JWT — change one byte of the signature. Expected: 401.

These probes are easy and the rubric explicitly calls them out under "endpoint hardening — token handling resists common attacks (replay, token substitution)."

## What goes in the demo

You don't run a live SAST/DAST during the demo. You **do**:

- Show `docs/sast-findings.md` and walk through one or two interesting findings + how you remediated.
- Show `docs/dast-payloads.md` and walk through one custom payload — what you sent, what came back, what it told you.

Two minutes per topic is plenty. The point is to prove you ran the tools and thought about the output.

Next: [Day-by-Day Plan](./08-day-by-day-plan.md).
