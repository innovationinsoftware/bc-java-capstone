# Capstone Spec — Secure Digital Banking Platform

This folder is the authoritative specification for the Java Upskilling Capstone. It expands on the high-level capstone document (`2609-JavaSpring-April6/Capstone Project/Capstone.md`) with the concrete contracts, schemas, flows, and acceptance criteria your team must hit to be graded "Meets Expectations" against the rubric.

If a detail in the original capstone PDF and this spec disagree, **this spec wins** — the PDF was the elevator pitch; this spec is the contract.

## How to read this spec

Read the documents in order the first time. After that, treat them as reference.

| # | Document | What it answers |
|---|---|---|
| 00 | [Overview](./00-overview.md) | What are we building? Who is it for? What does success look like? |
| 01 | [Architecture](./01-architecture.md) | What are the components, ports, repos, and how do they talk? |
| 02 | [Domain Model & Database](./02-domain-model.md) | Entities, Oracle schema, JPA mapping, sample data |
| 03 | [REST API Contract](./03-api-contract.md) | Every endpoint, DTO, status code, error envelope |
| 04 | [Security](./04-security.md) | Google OAuth2 + PKCE, scopes, RBAC, token validation |
| 05 | [React Frontend](./05-frontend.md) | Routes, components, hooks, login flow, calling the API |
| 06 | [Kafka Events](./06-kafka-events.md) | Topic, key, payload, producer config, idempotency |
| 07 | [Testing & Security Validation](./07-testing.md) | Unit, integration, SAST, DAST, custom payloads |
| 08 | [Day-by-Day Plan](./08-day-by-day-plan.md) | 3-day schedule with deliverables for each half-day |
| 09 | [Deliverables & Rubric Mapping](./09-deliverables-and-rubric.md) | What to submit and how it's graded |
| 10 | [Definition of Done](./10-definition-of-done.md) | The single checklist your team runs before demo |
| 11 | [Appendix](./11-appendix.md) | Copilot tips, common pitfalls, references |

## At-a-glance scope

You are building a **Secure Digital Banking Platform** consisting of:

- A **Spring Boot 3.x / Java 17** backend exposing a REST API for accounts and transactions, persisting to **Oracle 21c XE** via Spring Data JPA, and publishing transaction events to a pre-deployed **Kafka** topic.
- A **React SPA** (Vite + React Router) that lets a customer sign in with **Google**, view their accounts, and submit transactions.
- An OAuth2/OIDC integration where Google is the Identity Provider, the React SPA is a public client using **Authorization Code + PKCE**, and the Spring Boot service is a **Resource Server** that validates Google-issued JWTs.
- **RBAC** with two roles — `CUSTOMER` and `ADMIN` — layered on top of OAuth2 scopes.
- An **external payment API** call made securely from the backend to simulate a downstream payments processor.
- **Tests** (unit + integration), a **Checkmarx SAST scan**, and a **DAST scan** with custom banking payloads.

Everything you need was covered in Modules 0–9 of the bootcamp. Where the capstone goes one step beyond the labs, this spec calls it out.

## Tech stack (locked)

These are not negotiable — pick anything else and you'll fall outside the rubric and the scaffold.

| Layer | Technology | Version | Source in course |
|---|---|---|---|
| Language (backend) | Java | 17 | Module 1 |
| Build (backend) | Maven | 3.x | Module 1 |
| Backend framework | Spring Boot | 3.x (latest stable 3.4.x) | Modules 2–4, 7 |
| Persistence | Spring Data JPA + Hibernate | bundled | Module 7 |
| Database | Oracle Database | 21c XE | Modules 5–7 |
| Security | Spring Security OAuth2 Resource Server | bundled | Module 3 |
| External calls | RestTemplate or WebClient | bundled | Module 4 |
| Messaging | spring-kafka | bundled | Module 8 |
| Frontend tooling | Vite + npm | latest | Module 9 |
| Frontend framework | React | 18.x | Module 9 |
| Routing | react-router-dom | 6.x | Module 9 |
| HTTP client | fetch or axios | — | Module 9 |
| OAuth in SPA | oidc-client-ts or react-oidc-context (or equivalent) | — | Module 9 |
| Testing | JUnit 5, Spring Boot Test, WireMock | bundled | Modules 0, 4 |
| AI assistant | GitHub Copilot | — | Module 0 |

## Working agreement (read this once)

- **Teams** are pre-assigned — see `Capstone Project/Capstone.md`.
- **Repository** — one Git repo per team on GitHub. Both backend and frontend live in this single repo. See [Architecture](./01-architecture.md) for the layout. Push from day one — your commits are visible evidence of collaboration (rubric: Collaboration).
- **Scaffold** — a starter scaffold is provided. **Do not** delete its tests; **do** extend them. The scaffold encodes the API contract and Kafka topic name.
- **Pair / ensemble** — you are graded as a team. Reviewing a teammate's PR counts; silently doing all the work alone does not.
- **Copilot** — required, but you must understand what it generates. The rubric explicitly grades critical review of AI output.
- **Demo** — Day 3 ends in a team demo. Practice it once before presenting.

If anything in this spec is ambiguous, ask before assuming. "We thought it meant…" is not a defence at grading.
