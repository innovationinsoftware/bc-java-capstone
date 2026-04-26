# Capstone — Secure Digital Banking Platform

A 3-day team capstone. Build a small banking app with a Spring Boot REST API, a React UI, Oracle persistence, Google OAuth login, and Kafka event publishing.

## What you're building

A customer signs in with their Google account, sees their bank accounts, and submits transactions (deposit, withdrawal, transfer). The backend enforces who-owns-what, persists to Oracle, calls a stub "payment processor" for external transfers, and publishes a Kafka event for every completed transaction.

## Tech stack (locked)

| Layer | Tech |
|---|---|
| Backend | Java 17, Spring Boot 3.x, Spring Data JPA, Spring Security |
| Database | Oracle 21c XE |
| Messaging | Spring Kafka (broker is already deployed for you) |
| Frontend | React 18, Vite, react-router-dom, an OIDC client library |
| Auth | Google OAuth2/OIDC, Authorization Code + PKCE |
| Tests | JUnit 5, Spring Boot Test, WireMock |
| AI | GitHub Copilot (use it; review what it generates) |

A starter scaffold will be provided. Don't replace it — extend it.

## The four documents

1. **[requirements.md](./requirements.md)** — what to build (features, API endpoints, security rules)
2. **[plan.md](./plan.md)** — what to do each day
3. **[done.md](./done.md)** — the checklist before you demo
4. **[rubric.md](./rubric.md)** — how you're graded

Read them in order. Total reading time: 15 minutes.

## What you submit

A single GitHub repo with `backend/`, `frontend/`, and a `docs/` folder containing your SAST findings, DAST payloads, and a one-page architecture write-up. End the capstone with a 15-minute team demo.

## Working agreement

- **Teams** are pre-assigned (see `Capstone Project/Capstone.md`).
- **Everyone commits.** A team where one person did 90% of the work loses points.
- **Use Copilot, but understand what it generates.** You will be asked about your own code.
- **Ask early.** "We thought it meant…" is not a defence at grading.
