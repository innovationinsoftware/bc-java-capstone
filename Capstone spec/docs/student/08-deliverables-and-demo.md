# 08 — Deliverables & Demo

Day 2, final hour. The code works, the scan is run. Time to package, polish,
and present. This chapter is intentionally brief — you've used most of your
time on real work.

The authoritative checklists are in [`../09-deliverables-and-rubric.md`](../09-deliverables-and-rubric.md)
and [`../10-definition-of-done.md`](../10-definition-of-done.md). This chapter
adds the practical "how to actually finish" steps.

## Task 8.1 — Run the Definition of Done checklist

Print [`../10-definition-of-done.md`](../10-definition-of-done.md). Tick each
box only when you have **observed** the behaviour, not when you believe the
code is right.

If anything is unchecked, work it. There is no negotiation here — the
instructor will run the same checklist during grading.

The most commonly-failed items:

- `mvn -f backend/pom.xml test` runs to completion (across all three modules)
  and is green.
- `npm install` from a clean checkout works, `npm run dev` boots, `npm run build`
  produces a static bundle without errors.
- DevTools shows **only** `JSESSIONID` (HttpOnly) and `XSRF-TOKEN`. Local
  storage and session storage are empty.
- `git log --pretty=format:"%an"` shows commits from every team member. (A
  team where one person has 90% of the commits loses the entire 5%
  Collaboration & Presentation slice.)
- A non-team-member can clone the repo and run it in under 15 minutes.

## Task 8.2 — Polish the README

Open the root `README.md`. A new dev should be able to read it once and run
the project. Include:

- Prerequisites and versions.
- Every required env var (point at `.env.example`).
- The exact start order: mock-auth, resource-server, BFF, frontend.
- Which port each service listens on.
- How to run the tests (`mvn test`, `npm test`).
- Demo credentials (`alice` / `password`, `admin` / `password`).

Don't paste your `application.yml` or your test scripts. Link to the relevant
spec page if a reader needs deeper detail.

## Task 8.3 — Architecture write-up

Edit `docs/architecture.md` to be **one diagram + one page** (the rubric
weights two of those bullets). The diagram should show:

- Browser ↔ BFF (JSESSIONID cookie + X-XSRF-TOKEN header).
- BFF ↔ Resource Server (Bearer JWT via WebClient OIDC filter).
- BFF ↔ mock-auth (OAuth2 Authorization Code + PKCE).
- Resource Server ↔ Oracle / Kafka / WireMock (Payment Processor).

Below the diagram, one page on:

- Why the architecture is shaped this way (BFF rationale).
- Trade-offs you accepted (in-memory session store, single Resource Server
  instance, etc.).
- One or two specific design decisions that involved a real choice (e.g.,
  "internal transfer is two rows in one DB transaction; we chose denormalised
  TRANSFER_IN over a single-row TRANSFER because it makes per-account history
  queries cheaper").

## Task 8.4 — Demo script

Edit `docs/demo-script.md`. Allocate roughly:

| Time | Section | Owner |
|---|---|---|
| 2 min | Architecture intro — draw the diagram, point at where the cookie / Bearer / Kafka live | (assigned) |
| 3 min | Login flow — sign in as `alice`, open DevTools, show cookies and empty storage | (assigned) |
| 5 min | Customer flow — list accounts, drill in, deposit, internal transfer, watch Kafka events | (assigned) |
| 2 min | Admin flow — sign in as `admin`, show `/admin/users`, sign back as `alice`, show 403 | (assigned) |
| 2 min | SAST highlight — walk through one finding's fix and rescan | (assigned) |
| 2 min | DAST highlight — one custom payload, what you sent, what came back | (assigned) |
| 2 min | Q&A — be ready for "Why X?" on any decision | All |

Every team member must own at least one section. The rubric grades whether
each member can answer questions about the code under their name in `git log`.

## Task 8.5 — Demo Q&A prep

The frontend was pre-built; the BFF security chain was pre-built. That
makes the Q&A more important, not less — graders will probe to confirm
your team understands the code you didn't author. The AI-Assisted
Development slice (10%) is graded entirely during Q&A, and the
Collaboration & Presentation slice (5%) penalises a presenter who can't
answer about their own code.

Rehearse one-paragraph answers to each of these:

| Question | Where the answer lives |
|---|---|
| Walk me through how a transaction is submitted from the SPA. | `apiClient.js`, `TransactionForm.jsx`, `NewTransactionPage.jsx`, `AccountsBffController.java`, `TransactionService.java` |
| What happens in the SPA when an API call returns 401? | `apiClient.js`, `useMe.js`, `AppLayout.jsx` |
| How is the bearer token attached to BFF→RS calls? | `bff/.../config/WebClientConfig.java` |
| Why is the JwtAuthConverter's principal name the local userId, not the JWT subject? | `resource-server/.../security/JwtAuthConverter.java` (ownership semantics) |
| Why does the internal-transfer path produce two rows? | `TransactionService.applyTransferOut` and the `transferGroupId` column |
| What stops a CUSTOMER from hitting `/api/v1/admin/users`? | RS `SecurityConfig` URL filter **plus** `@PreAuthorize` (defence in depth) |
| How does the SPA send the CSRF token on a POST? | `apiClient.js` `readCsrfToken` + `X-XSRF-TOKEN` header |
| Why is the external-transfer balance debit inside the try block? | The "no debit on failure" invariant in `applyTransferOut` |

Don't memorise canned answers. Read the code, then talk about it like a
colleague would. If the grader asks "Copilot wrote this — what does it do?"
the right answer is "let me read it with you" and then doing it
out-loud, not "I don't know."

## Task 8.6 — Demo dry-run

Run the entire demo top-to-bottom **before** the real one. Time it. The first
dry-run is always 50% over. Cut features rather than rush.

Have a fallback plan:

- If Wi-Fi flakes: pre-record the demo or have screenshots ready.
- If Kafka dies mid-demo: have a screen recording of the console consumer
  printing events.
- If the BFF refuses to boot: have a known-good build tagged in git.

Practice on the same machine you will demo on.

## Task 8.7 — Final repo audit

Before pushing the final commit, run:

```bash
# Sweep TODOs out of code (only future-work TODOs should remain)
grep -rn "TODO" backend/*/src frontend/src

# Make sure secrets aren't tracked
git ls-files | xargs grep -l "client_secret\|password" 2>/dev/null

# Confirm .env is gitignored
git check-ignore -v .env

# Confirm tests pass
mvn -f backend/pom.xml test
cd frontend && npm test && cd ..

# Confirm the SPA bundle builds
cd frontend && npm run build && cd ..
```

Anything that fails: fix before submitting.

## What you submit

A single GitHub repo (URL to your instructor) containing:

```
<team-name>-banking/
├── README.md                       (clone-and-run, < 15 min)
├── backend/                        (multi-module Maven)
├── frontend/                       (npm test green)
├── scripts/                        (start-* helpers)
├── wiremock-stubs/
├── http-tests/                     (banking.http)
├── docs/
│   ├── architecture.md
│   ├── security-decisions.md
│   ├── sast-findings.md
│   ├── dast-payloads.md
│   ├── team-plan.md
│   └── demo-script.md
└── .env.example                    (no real secrets)
```

## After the demo

Tag the final commit (`git tag v1.0-final && git push --tags`) so the grading
state is preserved. If the instructor finds something during grading that
they want you to fix, you'll know exactly which commit was the demo.

## Done when

- [ ] DoD checklist 100% green.
- [ ] README runs the project in < 15 min on a fresh machine.
- [ ] Every doc in `docs/` is current and accurate.
- [ ] At least one full demo dry-run completed.
- [ ] Every team member has a section to present and can answer questions
      about their code.
- [ ] Final commit pushed and tagged.

Good luck.
