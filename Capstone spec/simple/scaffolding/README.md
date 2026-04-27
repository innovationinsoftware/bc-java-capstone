# Banking Scaffold

Starter project for the Capstone — Secure Digital Banking Platform.

This compiles, boots, and serves a working `GET /health` plus a React shell that lets you sign in with Google and call the API. **Most business logic is missing on purpose** — that's your work. Look for `// TODO` in the code; those are your task list.

> **Read the spec first.** [`../README.md`](../README.md) → [`requirements.md`](../requirements.md) → [`plan.md`](../plan.md). The spec is the contract; this scaffold is the head start.

## Layout

```
scaffolding/
├── backend/                          Spring Boot 3.x service
│   ├── pom.xml
│   └── src/main/java/com/example/banking/
│       ├── BankingApplication.java
│       ├── config/                   Security, CORS, JWT decoder, properties
│       ├── controller/               Health, User, Account, Transaction
│       ├── dto/                      Request/response records
│       ├── exception/                Domain exceptions + GlobalExceptionHandler
│       ├── kafka/                    TransactionEvent + Publisher
│       ├── model/                    JPA entities + enums
│       ├── repository/               Spring Data interfaces
│       ├── security/                 JwtAuthConverter (sub → local user + role)
│       └── service/                  AccountService, TransactionService, PaymentService
├── frontend/                         React 18 + Vite SPA
│   └── src/
│       ├── App.jsx, main.jsx
│       ├── auth/                     OIDC AuthProvider, RequireAuth, RequireRole
│       ├── api/                      apiClient + per-resource modules
│       └── routes/                   Page components
├── wiremock/mappings/                Stub Payment Processor for dev
├── http-tests/banking.http           Hand-test the API against a real token
├── docker-compose.yml                Optional: Oracle XE + WireMock
└── .env.example                      All env vars; copy to .env
```

## What's done for you

| Concern | Status |
|---|---|
| Build, dependencies, profiles | Done — `pom.xml`, `application.yml`, `application-dev.yml` |
| Schema | Done — Flyway migrations `V1__initial_schema.sql`, `V2__seed_admin.sql` |
| JPA entities + repositories | Done |
| DTOs, validation annotations | Done — `NewTransactionRequest` has Bean Validation |
| Global exception handler (RFC 7807-ish) | Done — `GlobalExceptionHandler` |
| Spring Security as Resource Server | Done — issuer + audience + signature + expiry validated |
| JWT → local-user + role conversion | Done — `JwtAuthConverter` (creates user on first login) |
| CORS for the SPA origin | Done |
| Kafka producer config + publisher | Done — keyed by accountId, `acks=all`, idempotent |
| `GET /health`, `/api/v1/users/me`, `/api/v1/accounts/**` | Done — wired end-to-end |
| `POST /api/v1/transactions` for DEPOSIT and WITHDRAWAL | Done — incl. insufficient-funds path |
| React routing, layout, `RequireAuth`, `RequireRole` | Done |
| Google sign-in via `react-oidc-context` (PKCE) | Done — drop in `VITE_GOOGLE_CLIENT_ID` |
| Pages for accounts, account detail, new transaction, admin users | Done — minimal, extend the UX |
| WireMock stub for the Payment Processor | Done |
| Smoke test for `/health` | Done |
| Sample unit test for `TransactionService` | Done — extend it |

## What you must build

Look in the code for `// TODO` and `throw new UnsupportedOperationException`. The big ones:

| Where | What |
|---|---|
| `TransactionService.applyTransferOut(...)` | Internal transfer (two rows) **and** external transfer (Payment Processor call). |
| `TransactionServiceTest` | Unit tests for deposit, ownership, transfer (internal + external happy/failure). |
| Integration tests | New file under `src/test/java`. Use `@SpringBootTest` + MockMvc + `.with(jwt())` + `@EmbeddedKafka`. Cover 401, 403, ownership, deposit happy path with Kafka emission. |
| `docs/sast-findings.md` | Run Checkmarx, write up findings. |
| `docs/dast-payloads.md` | Run a DAST scan + 3 custom banking payloads. |
| `docs/architecture.md` | One diagram + one page describing your design decisions. |

The spec's [done.md](../done.md) is your single end-of-capstone checklist.

## Run it locally

### 1. Bring up infra

If you have Oracle and WireMock running already, use those. Otherwise:

```bash
cp .env.example .env
# edit .env if you want to change defaults
docker compose up -d oracle wiremock
docker compose logs -f oracle    # wait for "DATABASE IS READY TO USE"
```

### 2. Configure Google OAuth (Day 2 — skip this on Day 1 if you just want curl tests)

1. Google Cloud Console → APIs & Services → Credentials
2. Create OAuth 2.0 Client ID, type "Web application"
3. Authorized JavaScript origin: `http://localhost:5173`
4. Authorized redirect URI: `http://localhost:5173/callback`
5. Copy the Client ID into:
   - `.env` → `GOOGLE_CLIENT_ID=...`
   - `frontend/.env.local` → `VITE_GOOGLE_CLIENT_ID=...`

### 3. Start the backend

```bash
cd backend
set -a && source ../.env && set +a       # export env vars (mac/linux)
./mvnw spring-boot:run                   # or: mvn spring-boot:run
```

Verify: `curl http://localhost:8081/health` → `{"status":"UP"}`

### 4. Start the frontend

```bash
cd frontend
cp .env.example .env.local
# edit VITE_GOOGLE_CLIENT_ID
npm install
npm run dev
```

Open http://localhost:5173. You should be redirected to `/login`.

### 5. Tail the Kafka topic (your instructor will give you the broker URL)

```bash
kafka-console-consumer.sh \
  --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS \
  --topic transactions.completed \
  --from-beginning \
  --property print.key=true \
  --property key.separator=" | "
```

## Day-1 sanity test (no Google needed)

Once the backend is up and Oracle is reachable:

```bash
curl -s http://localhost:8081/health
# {"status":"UP"}

curl -s http://localhost:8081/api/v1/accounts
# 401 — expected, the endpoint requires a token
```

If both of those work, your build, DB connection, and security config are wired. You can now move on to entities/services per the [day-by-day plan](../plan.md).

## Common gotchas

- **Oracle XE first boot is slow** (3–5 minutes). Watch `docker compose logs -f oracle` for "DATABASE IS READY TO USE" before starting the backend.
- **`Flyway baseline error`** if the schema already has tables: drop your test schema or set `spring.flyway.baseline-on-migrate=true`.
- **Google "redirect_uri_mismatch"**: the URI in the Cloud Console must be `http://localhost:5173/callback` *exactly*, including the `http://` and the port.
- **CORS preflight failing**: `SPA_ORIGIN` env var must match the actual origin the browser uses (default `http://localhost:5173`). It's a string match, not a wildcard.
- **`audiences` rejects all tokens**: check that `GOOGLE_CLIENT_ID` is the same value the SPA used to sign in. Different IDs ⇒ different `aud` claim ⇒ rejected.
- **`spring.jpa.hibernate.ddl-auto: validate` is intentional.** Schema lives in Flyway migrations under `db/migration/`. Don't switch to `update`.
- **Never put real secrets in `application.yml` or `.env.example`.** Real secrets go in `.env` (gitignored) or your shell.

## Tests

```bash
cd backend && ./mvnw test
cd frontend && npm test     # (no tests in scaffold; add your own)
```

The included `HealthControllerTest` and `TransactionServiceTest` are templates. Extend them — see the rubric for what to cover.

## Where to learn more

- The full spec lives in `../README.md` and friends — that's the contract.
- For deeper background on any topic, the bootcamp slides + labs are at `../../../2609-JavaSpring-April6/`.
- The closest thing to this capstone in the labs is `2609-JavaSpring-April6/Week 2/Lab 5 - Banking API Validation Security.md`. Reread it.
