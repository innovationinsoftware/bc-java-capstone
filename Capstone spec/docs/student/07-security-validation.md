# 07 — Security Validation

Day 2 late afternoon. ~30 minutes. The full SAST/DAST workflow from a 2-day
capstone is too big for two days. You will run a tighter pass: a hardening
grep sweep, one Checkmarx scan, and a write-up of the findings.

Re-read [`../07-testing.md`](../07-testing.md) sections "Checkmarx SAST scan"
and "DAST scan" if you have time. The full version is what you would do on
a real project.

## Task 7.1 — Hardening grep sweep

Run these checks on your repo and fix anything that fails:

```bash
# No secrets in source
grep -rn "client_secret\|password=\|Bearer eyJ" backend frontend/src

# No tokens logged
grep -rn "log\.\(info\|debug\|trace\)(.*token" backend
grep -rn "console\.log(.*token" frontend/src

# No OAuth library in the SPA
grep -rn "oidc-client-ts\|react-oidc-context" frontend/src

# No leftover TODOs blocking grading
grep -rn "TODO" backend/*/src frontend/src
```

The first three should produce **zero** matches. The last one should only
match TODOs that are explicitly future-work.

If a `grep` finds something, fix it. The most common offenders are
`log.info("processing token=" + token)` from a Copilot session and
secrets accidentally committed in `.env` or `application-local.yml`. If
the latter happened, **rotate those credentials** — git history makes them
permanent unless you rewrite history.

## Task 7.2 — Run Checkmarx (one scan)

Your instructor will give you access credentials and a one-page run-book.
Schedule the scan as soon as you can — the report can take 15-30 minutes,
and you need time to triage.

For each finding, decide:

- **Fix** — change the code, re-run, confirm the finding is closed.
- **Accept** — finding is real but the risk is acceptable for the capstone
  (e.g., `dev-only-not-secret` API key in `.env.example`). Justify in writing.
- **Defer** — out of scope (e.g., production-grade session store). Justify
  and note as "future work."

Document your triage in `docs/sast-findings.md` using the table from the
spec:

| Finding ID | Severity | Category | File:line | Decision | Rationale | Rescan |
|---|---|---|---|---|---|---|

Aim to fix every **high-severity** finding. Mediums and lows can be Accept
or Defer with a sentence each.

### Common findings to expect

- Hardcoded credentials in test fixtures or `application.yml`.
- Logging sensitive data (`log.info("token={}", token)`).
- `permitAll()` configured too broadly.
- Information exposure via raw `e.getMessage()` in error responses.
- `double` used for currency anywhere (you used `BigDecimal` everywhere — but
  Copilot may have regressed that).
- SQL injection via string concatenation. Should be zero findings if you
  used Spring Data — verify.

## Task 7.3 — Confirm the rubric's hardening checklist

Run through these manually. They are the items the rubric grades you on
during the demo:

| # | Check | How to verify |
|---|---|---|
| 1 | DevTools after sign-in shows only `JSESSIONID` (HttpOnly) and `XSRF-TOKEN`. Storage is empty. | Sign in, open DevTools → Application → Cookies + Storage |
| 2 | `curl http://localhost:8082/api/v1/accounts` direct to RS → 401 | Bearer-less request |
| 3 | POST without `X-XSRF-TOKEN` → 403 | Use curl/Postman without the header |
| 4 | Customer hitting `/api/v1/admin/users` → 403 | Sign in as alice |
| 5 | Customer hitting another customer's `/api/v1/accounts/{id}` → **404** (not 403) | Use a known account ID owned by another user |
| 6 | Sign out → next API call → 401 | Click Sign out, check Network tab |
| 7 | External transfer with WireMock 503 → 502, balance unchanged | Amount > 10000 |

Any row that fails is a **must-fix** before the demo.

## Task 7.4 — Security write-up

Update `docs/security-decisions.md` to cover, briefly:

- Why BFF over pure-SPA tokens (one paragraph).
- Where tokens live in your build (server-side, never browser).
- How CSRF is handled (cookie token, mutations echo it).
- How the BFF authenticates to the Resource Server (the WebClient OIDC
  filter — read it in the scaffolding so you can describe it).
- How RBAC is enforced (URL filter + `@PreAuthorize` + service-layer
  ownership returning 404).
- What you would do differently in production (Redis session store, HTTPS,
  rate limiting, an outbox pattern for Kafka).

One page is plenty. The grader wants to see you understood the model,
not a thesis.

## Cut from the 2-day version (with consequences)

The parent rubric and Definition of Done both require a DAST run plus
custom banking payloads. We've cut these from the 2-day plan because
they don't fit. **You will lose points on the Testing & Security
Validation slice (15%) for skipping them** — that's a deliberate
trade-off, not "not required":

- A DAST baseline scan with OWASP ZAP — see [`../09-deliverables-and-rubric.md`](../09-deliverables-and-rubric.md)
  Section 4 and [`../10-definition-of-done.md`](../10-definition-of-done.md)
  "Testing & security validation."
- Three custom banking payload classes (bulk-transfer abuse, malformed
  inputs, authorization probes) per [`../07-testing.md`](../07-testing.md).
  The integration tests in chapters 04 and 05 already prove most of those
  paths, but the rubric grades the dedicated payload write-ups separately.

**If you finish early**, run a ZAP baseline against `http://localhost:8081`
(active scan disabled — passive only) and append the findings to
`docs/dast-payloads.md`. It only takes 5-10 minutes and recovers most of
the points. A full active scan plus three custom payloads pushes you toward
"Exceeds" on this slice.

## Done when

- [ ] All four `grep` sweeps in 7.1 are clean.
- [ ] One Checkmarx scan run; `docs/sast-findings.md` has a row per finding.
- [ ] All seven hardening checklist rows in 7.3 behave correctly.
- [ ] `docs/security-decisions.md` reflects the actual implementation.

Next: [08-deliverables-and-demo.md](./08-deliverables-and-demo.md).
