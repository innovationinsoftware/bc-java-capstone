# 01 — Architecture

## System diagram (logical)

```
                     ┌──────────────────────┐
                     │    Google OAuth2     │
                     │  Authorization /     │
                     │  Token / JWKS        │
                     └──────────▲───────────┘
                                │  (1) Authorization Code + PKCE
                                │  (3) JWKS for token validation
              ┌─────────────────┼─────────────────┐
              │                 │                 │
              ▼                 │                 ▼
┌────────────────────┐          │      ┌──────────────────────┐
│   React SPA        │  ─(2)──► │ ◄─── │  Spring Boot API     │
│   Vite + React 18  │   Bearer JWT     │  port 8081           │
│   port 5173 (dev)  │                  │  /api/v1/**          │
└─────────┬──────────┘                  └──────────┬───────────┘
          │ user                                   │
          │                                        ├──► (4) Oracle 21c XE
          │                                        │
          │                                        ├──► (5) Kafka topic
          │                                        │     transactions.completed
          │                                        │
          │                                        └──► (6) Payment Processor
          │                                              (WireMock stub)
          ▼
   browser, customer
```

Numbered flows:

1. **Login** — SPA initiates Authorization Code + PKCE against Google. After consent, Google redirects back to the SPA with an authorization code; the SPA exchanges the code (with the PKCE verifier) at Google's token endpoint and receives an ID token + access token.
2. **API call** — every SPA → backend request carries `Authorization: Bearer <access_token>`.
3. **Token validation** — the Spring Boot resource server fetches Google's JWKS once at startup (and on key-rotation) and verifies signature, `iss`, `aud`, and `exp` on every request.
4. **Persistence** — the service reads/writes accounts and transactions in Oracle via Spring Data JPA.
5. **Event emission** — every committed transaction publishes a `TransactionEvent` to the pre-deployed Kafka topic.
6. **External payment** — for transaction types that require an external clearing step (e.g., outbound transfer), the service calls a downstream Payment Processor over HTTPS with appropriate authentication.

## Components

### React SPA (`frontend/`)

- **Tooling.** Vite for bundling and dev server. `npm run dev` for local, `npm run build` for production bundle.
- **Routing.** `react-router-dom` v6 — `BrowserRouter` at the root, `<Routes>` with a layout route for authenticated pages and a separate `/login` and `/callback` outside the layout.
- **State.** Component-local state with `useState` and `useEffect` is sufficient. If you reach for Redux you've overscoped.
- **API client.** A single `apiClient.js` (or `apiClient.ts`) module that wraps `fetch` (or `axios`) and attaches the Bearer token from session storage. Treat it as the only place that knows about auth headers.
- **Auth library.** Use a maintained OIDC client for the browser — `oidc-client-ts` or `react-oidc-context` are both fine. Do **not** roll your own PKCE.

### Spring Boot service (`backend/`)

The package layout follows what Lab 5 established. **Stick with it** — the rubric grades organisation:

```
com.example.banking
├── BankingApplication.java
├── config         // SecurityConfig, KafkaConfig, RestClientConfig, CorsConfig
├── controller     // AccountController, TransactionController, UserController, HealthController
├── dto            // request and response DTOs (records)
├── exception      // domain exceptions + GlobalExceptionHandler (@ControllerAdvice)
├── model          // JPA entities: AccountEntity, TransactionEntity, UserEntity
├── repository     // Spring Data JPA repositories
├── service        // AccountService, TransactionService, PaymentService
├── kafka          // TransactionEventPublisher and the event payload record
└── security       // JwtAuthConverter, role mapping, ownership checks
```

### Oracle schema

A single schema (`bankapp` or whatever the scaffold provides). Three tables: `ACCOUNTS`, `TRANSACTIONS`, `BANK_USERS`. Schema details are in [Domain Model](./02-domain-model.md).

### Kafka

A pre-deployed cluster — the broker URL and topic name are provided by the instructor and pinned in `application.yml`. You do **not** stand up Kafka yourself.

## Repository layout

One Git repo per team. Recommended top-level layout:

```
<team-name>-banking/
├── README.md                       # how to clone, configure, run
├── .gitignore
├── docker-compose.yml              # optional: brings up Oracle + WireMock locally
├── backend/
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd
│   └── src/
│       ├── main/java/com/example/banking/...
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-dev.yml
│       │   └── db/migration/        # SQL files for the schema (or Flyway)
│       └── test/java/com/example/banking/...
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── main.tsx (or main.jsx)
│       ├── App.tsx
│       ├── routes/
│       ├── components/
│       ├── hooks/
│       ├── api/                     # apiClient + endpoint wrappers
│       └── auth/                    # OIDC config and helpers
├── docs/
│   ├── architecture.md              # one diagram + 1 page of explanation, written by you
│   ├── security-decisions.md        # per the rubric — how you handled token lifecycle, etc.
│   ├── sast-findings.md             # one row per Checkmarx finding: severity, fix/accept/defer, why
│   └── dast-payloads.md             # the custom payloads you sent and what happened
└── .github/
    └── workflows/                   # optional: CI for mvn test + npm test
```

## Ports and URLs (dev)

| Service | URL | Source |
|---|---|---|
| React SPA (dev) | http://localhost:5173 | Vite default |
| Spring Boot API | http://localhost:8081 | Lab 5 convention; pinned in `application.yml` |
| Oracle XE | jdbc:oracle:thin:@//localhost:1521/XEPDB1 | Module 5 convention |
| Kafka broker | provided by instructor | pinned in `application.yml` |
| Payment Processor stub | http://localhost:8089 | WireMock default |

The SPA's redirect URI for Google (registered in Google Cloud Console) is `http://localhost:5173/callback`. Production redirect URIs are out of scope.

## Configuration strategy

- `application.yml` holds non-secret defaults.
- `application-dev.yml` holds local-dev overrides.
- Anything secret (DB password, Google client ID, payment-processor API key) is read from **environment variables** via Spring's standard `${VAR}` placeholders. **Do not commit secrets** — even into a `.local.yml`. The rubric's "endpoint hardening" criterion specifically calls out credential handling.

```yaml
spring:
  datasource:
    url: ${ORACLE_URL:jdbc:oracle:thin:@//localhost:1521/XEPDB1}
    username: ${ORACLE_USER:bankapp}
    password: ${ORACLE_PASSWORD}
  security:
    oauth2:
      resourceserver:
        jwt:
          jwks-uri: https://www.googleapis.com/oauth2/v3/certs
          issuer-uri: https://accounts.google.com
          audiences: ${GOOGLE_CLIENT_ID}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      acks: all
      properties:
        enable.idempotence: true

bank:
  payment-processor:
    base-url: ${PAYMENT_PROCESSOR_URL:http://localhost:8089}
    api-key: ${PAYMENT_PROCESSOR_API_KEY}

server:
  port: 8081
```

## CORS

The SPA at `http://localhost:5173` calls the API at `http://localhost:8081` — different origins. Configure CORS in Spring (`config/CorsConfig.java`) to allow that origin for `GET, POST, OPTIONS` and the `Authorization` header. **Do not** set `Access-Control-Allow-Origin: *` — the rubric grades configuration cleanliness, and `*` is wrong with credentialed requests anyway.

## What the scaffold gives you

The instructor will provide a starter scaffold. At minimum it contains:

- A working `BankingApplication.java` and `pom.xml` with the right starters.
- Stub controllers (`AccountController`, `TransactionController`, etc.) with `@GetMapping` shells and `// TODO` markers — these are the patterns from Lab 5 carried forward.
- Empty service classes wired with constructor injection.
- Empty JPA entities and repository interfaces.
- An `application.yml` pre-pointed at the right Kafka broker and Oracle URL placeholder.
- A `frontend/` with `vite.config.ts`, an `App.tsx` shell, and one example component that calls `/health`.
- A `Capstone Project` Postman/REST-Client file with skeleton requests.

You are expected to **fill in the TODOs**, add features the scaffold deliberately omits (login, RBAC, Kafka publishing, payment integration), and write tests. Do not delete the scaffold's structure to start over — the rubric grades you against the patterns it sets up.

Next: [Domain Model & Database](./02-domain-model.md).
