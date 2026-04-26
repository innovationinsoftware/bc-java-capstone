# 00 — Overview

## What you are building

A simplified online banking platform with three parts:

1. **Customer-facing React SPA** — a single-page web app where a customer signs in with their Google account, sees a list of their bank accounts, drills into an account to view its transaction history, and submits new transactions (deposit, withdrawal, transfer).
2. **Spring Boot REST API** — the system of record for accounts and transactions. It authenticates every request via a Google-issued JWT, enforces ownership and role rules, persists to Oracle, calls a downstream "payment processor" for external transfers, and emits a Kafka event for each completed transaction.
3. **Kafka producer** — every committed transaction publishes a `TransactionEvent` to the pre-deployed Kafka topic so downstream systems (fraud, statements, notifications) can react. You are not asked to build those consumers — only to publish the events correctly.

## Why this shape

Every piece of the capstone exercises material the bootcamp actually covered:

| Bootcamp module | Where it shows up in the capstone |
|---|---|
| Module 1 — Java 17 (records, streams, sealed types) | Domain records (`Account`, `Transaction`), stream-based filters in service methods |
| Module 2 — Spring Boot REST | All of `/api/v1/*` — controllers, validation, `@ControllerAdvice`, RFC 7807 errors |
| Module 3 — OAuth2 / Spring Security | Resource server, JWT validation against Google's JWKS, scope + role authorization |
| Module 4 — Secure external API consumption | The payment-processor call (RestTemplate or WebClient + WireMock in tests) |
| Modules 5–7 — Oracle, PL/SQL, Spring Data | The accounts/transactions schema, JPA mapping, `@Transactional` semantics |
| Module 8 — Kafka with Spring Boot | The `KafkaTemplate` producer for transaction events |
| Module 9 — React SPA & security | The whole frontend: components, routing, login, calling protected APIs |
| Module 0 — Copilot | Used throughout; explicitly graded |

If you can't see how a feature traces back to course material, you are probably overscoping it. Ask.

## Audience and constraints

**Audience.** A retail-banking customer using the SPA in a desktop browser. There is no mobile app, no service-to-service public API, no admin portal beyond a single read-only "list users" view for the `ADMIN` role.

**Scale.** This is a class project, not a production deployment. Single Spring Boot instance, single Oracle schema, the pre-deployed Kafka cluster.

**Out of scope (explicitly).** Do not implement any of the following — they are tempting and will eat your three days:

- Account opening / KYC / identity verification
- Email or SMS notifications (let Kafka events imply that someone else will send those)
- A separate auth server — Google **is** your auth server
- Refresh-token rotation in the backend (the SPA handles its own token lifecycle)
- Payment-processor sandbox integration with a real third-party API (you will mock it with WireMock or a tiny stub)
- Production-grade observability — Spring Boot Actuator's defaults are enough
- Mobile-responsive design beyond "doesn't break at 1024px"
- Internationalization, accessibility audits, or design-system adoption

## Definition of "done" at a glance

Your team is done when **all** of these are true. The full checklist lives in [Definition of Done](./10-definition-of-done.md); this is the one-paragraph version:

> A non-team-member can clone your repo, follow your README, sign in with their own Google account, see their (seeded) accounts, submit a transaction, and observe (a) the new row in Oracle, (b) the new event on the Kafka topic, and (c) a 401 if they delete the token from browser storage and retry. Your test suite runs green from `mvn test` and `npm test`. A Checkmarx scan has been run, findings have been triaged in writing, and the high-severity ones are remediated. A DAST scan has been run with at least one custom banking payload, and you can describe how the API responded.

## Roles on the team

You are graded as a team but you should still divide the surface so you don't trip over each other:

- **Backend lead** — Spring Boot service, Oracle schema, Kafka producer, security config
- **Frontend lead** — React SPA, routing, login flow, protected API calls, UX for forms
- **Quality lead** — Unit + integration tests, SAST/DAST runs, custom payloads, demo script

These are leads, not silos. Everyone touches everything; the lead owns the final review.

## How to use the rubric

[The rubric](../2609-JavaSpring-April6/Capstone%20Project/Capstone%20Rubric%20v2.xlsx) (also summarized in [Deliverables & Rubric Mapping](./09-deliverables-and-rubric.md)) defines what "Meets Expectations" looks like for each section. Read it on Day 1 before writing any code. Two specific things to internalise:

- **Meets ≠ ceiling.** The "Exceeds" column is where you target if you have time after Meets is solid. Don't reach for Exceeds at the cost of Meets.
- **The Collaboration & Presentation slice is small (5%) but binary.** A team with no commits from a member, or a demo where one person can't answer questions about their own code, loses that whole 5% — and it changes the perception of every other section.

Now go read [Architecture](./01-architecture.md).
