# 00 — Overview

## What you are building

A simplified online banking platform built with the **Backend-for-Frontend (BFF) pattern**. Four pieces:

1. **React SPA** — the customer-facing UI. Pages for accounts, transactions, and (for admins) user listing. The SPA holds **no tokens** — only an HttpOnly session cookie issued by the BFF.
2. **Spring Boot BFF** — the OAuth2 client. Drives the Authorization Code + PKCE login flow, holds the user's tokens server-side, serves the SPA, and proxies `/api/v1/**` calls to the Resource Server using `WebClient` with the `ServletOAuth2AuthorizedClientExchangeFilterFunction`. The browser only ever talks to the BFF.
3. **Spring Boot Resource Server** — the banking API. Persists to Oracle, enforces ownership and role rules, calls a downstream Payment Processor for external transfers, and emits a Kafka event for each completed transaction. Validates JWTs but knows nothing about cookies.
4. **Authorization Server** — issues tokens. The scaffold ships a tiny mock auth server (`backend/mock-auth/`, port 9000) you'll use locally — the same Spring Authorization Server pattern from Module 3 / Lab 5.

The Kafka events go to a pre-deployed topic; you are not asked to build the downstream consumer. The Payment Processor is mocked with WireMock locally.

## Why this shape

Every piece of the capstone exercises material the bootcamp actually covered:

| Bootcamp module | Where it shows up in the capstone |
|---|---|
| Module 1 — Java 17 (records, streams, sealed types) | Domain records (`Account`, `Transaction`), stream-based filters in service methods |
| Module 2 — Spring Boot REST | All of `/api/v1/*` — controllers, validation, `@ControllerAdvice`, RFC 7807 errors |
| Module 3 — OAuth2 / Spring Security | Resource server JWT validation, plus the mock Authorization Server pattern that backs `backend/mock-auth/` |
| Module 4 — Secure external API consumption | The Payment Processor call from the Resource Server (WebClient + WireMock in tests) |
| Modules 5–7 — Oracle, PL/SQL, Spring Data | The accounts/transactions schema, JPA mapping, `@Transactional` semantics |
| Module 8 — Kafka with Spring Boot | The `KafkaTemplate` producer for transaction events; new Labs 4.1–4.3 walk through this |
| Module 9 — React SPA + BFF section | The frontend, plus the BFF pattern itself — the slides explicitly recommend BFF over pure-SPA tokens for sensitive applications |
| Module 0 — Copilot | Used throughout; explicitly graded |

If you can't see how a feature traces back to course material, you are probably overscoping it. Ask.

## Audience and constraints

**Audience.** A retail-banking customer using the SPA in a desktop browser. There is no mobile app, no service-to-service public API, no admin portal beyond a single read-only "list users" view for the `ADMIN` role.

**Scale.** This is a class project, not a production deployment. Single Spring Boot instance, single Oracle schema, the pre-deployed Kafka cluster.

**Out of scope (explicitly).** Do not implement any of the following — they are tempting and will eat your three days:

- Account opening / KYC / identity verification
- Email or SMS notifications (let Kafka events imply that someone else will send those)
- A real external IdP (Google, Okta) — the scaffold's mock auth server is the path of least resistance. Document an external IdP as future work for "Exceeds."
- Refresh-token rotation logic — Spring Security handles this automatically inside the BFF; you do not write it.
- Payment-processor sandbox integration with a real third-party API (mocked with WireMock)
- Production-grade observability — Spring Boot Actuator's defaults are enough
- Production-grade session storage (Redis/JDBC) — the BFF uses in-memory sessions for the capstone; document this trade-off
- Mobile-responsive design beyond "doesn't break at 1024px"
- Internationalization, accessibility audits, or design-system adoption

## Definition of "done" at a glance

Your team is done when **all** of these are true. The full checklist lives in [Definition of Done](./10-definition-of-done.md); this is the one-paragraph version:

> A non-team-member can clone your repo, follow your README, start mock-auth + resource-server + bff + frontend, sign in as `alice`, see their (seeded) accounts, submit a transaction, and observe (a) the new row in Oracle, (b) the new event on the Kafka topic, (c) **no token visible in DevTools — only an HttpOnly session cookie**, and (d) a 401 (then automatic redirect to login) if they delete the cookie and retry. Your test suite runs green from `mvn test` and `npm test`. A Checkmarx scan has been run, findings have been triaged in writing, and the high-severity ones are remediated. A DAST scan has been run with at least one custom banking payload, and you can describe how the API responded.

## Roles on the team

You are graded as a team but you should still divide the surface so you don't trip over each other:

- **Resource Server lead** — banking domain logic, Oracle schema + JPA, Kafka producer, Payment Processor integration, JWT validation
- **BFF lead** — OAuth2 client config, WebClient with the OAuth2 filter, proxy controllers, CSRF wiring, session config
- **Frontend lead** — React SPA, routing, sign-in/sign-out UX, calling protected APIs, the `useMe` hook
- **Quality lead** — Unit + integration tests, SAST/DAST runs, custom payloads, demo script

The mock auth server is small enough that whoever finishes their slice first picks it up.

These are leads, not silos. Everyone touches everything; the lead owns the final review.

## How to use the rubric

[The rubric](../2609-JavaSpring-April6/Capstone%20Project/Capstone%20Rubric%20v2.xlsx) (also summarized in [Deliverables & Rubric Mapping](./09-deliverables-and-rubric.md)) defines what "Meets Expectations" looks like for each section. Read it on Day 1 before writing any code. Two specific things to internalise:

- **Meets ≠ ceiling.** The "Exceeds" column is where you target if you have time after Meets is solid. Don't reach for Exceeds at the cost of Meets.
- **The Collaboration & Presentation slice is small (5%) but binary.** A team with no commits from a member, or a demo where one person can't answer questions about their own code, loses that whole 5% — and it changes the perception of every other section.

Now go read [Architecture](./01-architecture.md).
