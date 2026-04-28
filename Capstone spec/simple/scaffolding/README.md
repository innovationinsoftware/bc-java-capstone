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
├── scripts/
│   ├── setup-oracle.sql              One-time: create the bankapp Oracle user
│   ├── start-wiremock.sh             Run WireMock standalone on port 8089
│   ├── start-backend.sh              Convenience: load .env, mvn spring-boot:run
│   └── start-frontend.sh             Convenience: npm install (if needed) + npm run dev
├── wiremock-stubs/                   Mappings for the Payment Processor stub
│   └── mappings/
│       ├── payment-success.json
│       └── payment-failure-large.json
├── http-tests/banking.http           Hand-test the API against a real token
└── .env.example                      All env vars; copy to .env
```

## Prerequisites on your dev VM

You should already have these from the bootcamp:

| Tool | Version | From which module |
|---|---|---|
| JDK | 17 | Module 0 |
| Maven | 3.9+ | Module 1 |
| Node.js | 20+ | Module 9 |
| Oracle XE | 21c, listening on `localhost:1521`, PDB `XEPDB1` | Module 5 (Lab 3.0) |
| `sqlplus` | bundled with Oracle | Module 5 |
| `curl` or `wget` | system | — |

Quick sanity:

```bash
java -version          # → 17.x
mvn -version           # → 3.9.x
node --version         # → v20.x
sqlplus -V             # → SQL*Plus: Release 21.x
```

If anything is missing, install it before continuing — the scaffold does not bring its own runtime.

## What's done for you

| Concern | Status |
|---|---|
| Build, dependencies, profiles | Done — `pom.xml`, `application.yml`, `application-dev.yml` |
| Schema | Done — Flyway migrations `V1__initial_schema.sql`, `V2__seed_admin.sql` |
| JPA entities + repositories | Done |
| DTOs, validation annotations | Done — `NewTransactionRequest` has Bean Validation |
| Global exception handler (RFC 7807-ish) | Done — `GlobalExceptionHandler` |
| Spring Security as Resource Server | **TODO** — issuer + audience + signature + expiry validated |
| JWT → local-user + role conversion | Done — `JwtAuthConverter` (creates user on first login) |
| CORS for the SPA origin | Done |
| Kafka producer config + publisher | Done — keyed by accountId, `acks=all`, idempotent |
| GET /health, /api/v1/users/me, /api/v1/accounts/**  | **TODO**  — wired end-to-end |
| POST /api/v1/transactions for DEPOSIT and WITHDRAWAL | **TODO** — incl. insufficient-funds path |
| React routing, layout, `RequireAuth`, `RequireRole` | Done |
| Google sign-in via eact-oidc-context (PKCE) | **TODO** — drop in `VITE_GOOGLE_CLIENT_ID` |
| Pages for accounts, account detail, new transaction, admin users | Done — minimal, extend the UX |
| WireMock stub for the Payment Processor | Done — runs as a standalone JVM process |
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

## First-time setup

Do these once. They don't need to be repeated unless you wipe the schema or switch dev VMs.

### 1. Create the `bankapp` Oracle schema

Run as a privileged Oracle user (SYS or SYSTEM) against the XEPDB1 pluggable database:

```bash
cd scaffolding
sqlplus sys/<your-sys-password>@//localhost:1521/XEPDB1 as sysdba @scripts/setup-oracle.sql
```

This drops (if exists) and creates a fresh `BANKAPP` user with password `bankapp` and the grants Flyway needs to create tables. The script prints a confirmation row at the end.

If your VM already has a different Oracle user you want to reuse from earlier labs, you can skip this and just point `ORACLE_USER` / `ORACLE_PASSWORD` at that user — but make sure its schema is empty, or Flyway's first migration will fail.

### 2. Configure backend env vars

```bash
cp .env.example .env
# Edit .env. At minimum, set ORACLE_PASSWORD and (later) GOOGLE_CLIENT_ID.
```

`.env` is gitignored — never commit it.

### 3. Configure Google OAuth (Day 2 — skip on Day 1 if you only want curl tests)

1. Google Cloud Console → APIs & Services → Credentials
2. Create OAuth 2.0 Client ID, type "Web application"
3. Authorized JavaScript origin: `http://localhost:5173`
4. Authorized redirect URI: `http://localhost:5173/callback`
5. Copy the Client ID into:
   - `.env` → `GOOGLE_CLIENT_ID=...`
   - `frontend/.env.local` → `VITE_GOOGLE_CLIENT_ID=...` (copy `frontend/.env.example`)

## Running the stack

You'll need three terminals:

### Terminal 1 — backend

```bash
cd scaffolding
./scripts/start-backend.sh
```

This loads `.env`, then runs `mvn spring-boot:run` from `backend/`. Watch for `Started BankingApplication`. Flyway runs the schema migrations on first boot.

Verify:
```bash
curl http://localhost:8081/health
# → {"status":"UP"}

curl -i http://localhost:8081/api/v1/accounts
# → HTTP/1.1 401 (no token)
```

### Terminal 2 — frontend

```bash
cd scaffolding
./scripts/start-frontend.sh
```

First run does `npm install` (one-time, a few minutes). Then Vite serves on `http://localhost:5173`. Without a Google client ID configured, `/login` will load but the sign-in button won't get past Google.

### Terminal 3 — WireMock (only needed once you implement external transfers)

```bash
cd scaffolding
./scripts/start-wiremock.sh
```

First run downloads the WireMock JAR (~25 MB) into `scripts/.cache/`. Then it serves on `http://localhost:8089` with the stubs in `wiremock-stubs/mappings/`.

The two pre-loaded stubs:
- Any `POST /payments` returns 200 with a fake processor reference.
- A `POST /payments` with `amount > 10000` returns 503 (use this to test your `FAILED` transaction path).

You can also stop and start WireMock independently — the backend doesn't depend on it being up unless you trigger an external transfer.

### Terminal 4 (optional) — Kafka console consumer

The Kafka broker URL is provided by your instructor and goes in `.env` as `KAFKA_BOOTSTRAP_SERVERS`. To watch transaction events:

```bash
kafka-console-consumer.sh \
  --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS \
  --topic transactions.completed \
  --from-beginning \
  --property print.key=true \
  --property key.separator=" | "
```

Substitute whatever Kafka CLI tools your instructor pointed you at.

## Day-1 sanity test (no Google needed)

Once the backend is up:

```bash
curl -s http://localhost:8081/health
# {"status":"UP"}

curl -s -i http://localhost:8081/api/v1/accounts | head -1
# HTTP/1.1 401
```

If both pass, your build, DB connection, and security config are wired. You can now move on to entity/service work per the [day-by-day plan](../plan.md).

## Tests

```bash
cd backend
mvn test
```

The included `HealthControllerTest` and `TransactionServiceTest` are templates. Extend them — see the [rubric](../rubric.md) and [requirements](../requirements.md) for what to cover.

## Common gotchas

- **Flyway error: "Found non-empty schema(s) without baseline"** — your `bankapp` user already has tables from earlier work. Either drop them, or set `spring.flyway.baseline-on-migrate=true` in `application.yml`. The cleanest fix is to re-run `scripts/setup-oracle.sql` (it drops + recreates the user).
- **Oracle "ORA-12514: listener does not currently know of service requested"** — your XE service name is different from `XEPDB1`. Check with `lsnrctl status` and update `ORACLE_URL` in `.env` accordingly.
- **Google "redirect_uri_mismatch"** — the URI in Cloud Console must be `http://localhost:5173/callback` exactly.
- **CORS preflight failing** — `SPA_ORIGIN` env var must match the actual origin the browser uses (default `http://localhost:5173`). It's a string match, not a wildcard.
- **`audiences` rejects all tokens** — `GOOGLE_CLIENT_ID` in `.env` must be the same value the SPA used when signing in. Different IDs ⇒ different `aud` claim ⇒ rejected.
- **`spring.jpa.hibernate.ddl-auto: validate` is intentional.** Schema lives in Flyway migrations under `db/migration/`. Don't switch to `update`.
- **`./scripts/*.sh: Permission denied`** — run `chmod +x scripts/*.sh`. (Should already be set, but git on Windows can drop it.)
- **WireMock script fails on download** — your VM may not have internet access. Download `wiremock-standalone-3.6.0.jar` from Maven Central manually and drop it into `scripts/.cache/`, then re-run.
- **Never put real secrets in `application.yml` or `.env.example`.** Real secrets go in `.env` (gitignored) or your shell environment.

## Where to learn more

- The full spec lives in [`../README.md`](../README.md) and friends — that's the contract.
- For deeper background on any topic, the bootcamp slides + labs are at `../../../2609-JavaSpring-April6/`.
- The closest thing to this capstone in the labs is `2609-JavaSpring-April6/Week 2/Lab 5 - Banking API Validation Security.md`. Reread it.
