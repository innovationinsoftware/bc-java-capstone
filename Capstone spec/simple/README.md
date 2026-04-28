# Capstone — Secure Digital Banking Platform

A 3-day team capstone. Build a small banking app using the **Backend-for-Frontend (BFF) pattern**: a Spring Boot BFF holds OAuth2 tokens server-side, the React SPA only ever sees an HttpOnly session cookie, and a separate Spring Boot Resource Server holds the banking domain.

## What you're building

A customer signs in via the BFF (which drives an OAuth2 Authorization Code + PKCE flow against an Authorization Server), sees their bank accounts, and submits transactions. The BFF proxies API calls to the Resource Server using **Spring WebClient** with the `ServletOAuth2AuthorizedClientExchangeFilterFunction` — Spring attaches the bearer token automatically. The Resource Server persists to Oracle, calls a stub Payment Processor for external transfers, and publishes a Kafka event for every completed transaction.

## Tech stack (locked)

| Layer | Tech |
|---|---|
| Language (backend) | Java 17, Maven (multi-module) |
| Framework | Spring Boot 3.x |
| BFF | `spring-boot-starter-oauth2-client` + Spring `WebClient` |
| Resource Server | `spring-boot-starter-oauth2-resource-server`, Spring Data JPA, spring-kafka |
| Authorization Server | `spring-boot-starter-oauth2-authorization-server` (mock, for dev) |
| Database | Oracle 21c XE |
| Frontend | React 18 + Vite + react-router-dom (no OIDC library — BFF handles OAuth) |
| Tests | JUnit 5, Spring Boot Test, WireMock |
| AI | GitHub Copilot |

A starter scaffold lives in [`scaffolding/`](./scaffolding/). It has the multi-module Maven layout (mock-auth, bff, resource-server) plus the React frontend wired to the BFF via Vite proxy. Don't replace it — extend it.

## The five things to read

1. **[requirements.md](./requirements.md)** — what to build (features, API endpoints, BFF/security rules)
2. **[plan.md](./plan.md)** — what to do each day
3. **[done.md](./done.md)** — the checklist before you demo
4. **[rubric.md](./rubric.md)** — how you're graded
5. **[scaffolding/README.md](./scaffolding/README.md)** — what's already built and how to run it

Read 1–4 in order (15 min). Then open the scaffold README and start running it.

## What you submit

A single GitHub repo with `backend/` (multi-module: mock-auth, bff, resource-server), `frontend/`, and a `docs/` folder containing your SAST findings, DAST payloads, and a one-page architecture write-up. End the capstone with a 15-minute team demo.

## Working agreement

- **Teams** are pre-assigned (see `Capstone Project/Capstone.md`).
- **Everyone commits.** A team where one person did 90% of the work loses points.
- **Use Copilot, but understand what it generates.** You will be asked about your own code.
- **Ask early.** "We thought it meant…" is not a defence at grading.
