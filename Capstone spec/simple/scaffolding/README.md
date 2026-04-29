# Banking Scaffold (BFF Edition)

Starter project for the Capstone — Secure Digital Banking Platform.

This scaffold uses the **Backend-for-Frontend (BFF) pattern**: the React SPA holds **no tokens** — only an HttpOnly session cookie. A Spring Boot **BFF** service drives the OAuth2 flow, holds tokens server-side, and proxies API calls to a separate **Resource Server** using **Spring `WebClient`** with the OAuth2 filter. Tokens are never exposed to JavaScript.

> **Read the spec first.** [`../README.md`](../README.md) → [`requirements.md`](../requirements.md) → [`plan.md`](../plan.md). The spec is the contract; this scaffold is the head start.

## Layout

```
scaffolding/
├── backend/                                 multi-module Maven build
│   ├── pom.xml                              parent
│   ├── mock-auth/                           Spring Authorization Server, port 9000
│   │   └── src/main/java/com/example/mockauth/
│   │       ├── MockAuthApplication.java
│   │       └── AuthorizationServerConfig.java
│   ├── bff/                                 OAuth2 client + WebClient proxy, port 8080
│   │   └── src/main/java/com/example/bff/
│   │       ├── BffApplication.java
│   │       ├── config/                      SecurityConfig, WebClientConfig
│   │       └── controller/                  AccountsBff, TransactionsBff, UsersBff
│   └── resource-server/                     Banking REST API, port 8081
│       └── src/main/java/com/example/banking/
│           ├── BankingApplication.java
│           ├── config/                      SecurityConfig, JwtDecoderConfig, ...
│           ├── controller/, dto/, exception/, kafka/, model/, repository/, security/, service/
│           └── resources/db/migration/      Flyway: V1 schema + V2 seed
├── frontend/                                React 18 + Vite (no OIDC library)
│   ├── package.json
│   ├── vite.config.js                       proxies /api,/login,/logout,/oauth2 → :8080
│   └── src/
│       ├── App.jsx, main.jsx
│       ├── api/                             apiClient (cookie + CSRF, no tokens)
│       ├── hooks/useMe.js
│       ├── routes/                          AppLayout, AccountsPage, ...
│       └── components/
├── scripts/
│   ├── setup-oracle.sql                     one-time: create the bankapp schema
│   ├── verify-prereqs.ps1                   Day-0: confirm Java/Maven/Node/Oracle on the VM
│   ├── start-mock-auth.{sh,ps1}             start FIRST (port 9000)
│   ├── start-resource-server.{sh,ps1}       port 8081
│   ├── start-bff.{sh,ps1}                   port 8080
│   ├── start-frontend.{sh,ps1}              port 5173
│   └── start-wiremock.{sh,ps1}              port 8089 (Payment Processor stub)
├── wiremock-stubs/
└── http-tests/banking.http                  hand-test the API
```

## Prerequisites on your dev VM

| Tool | Version | From which module |
|---|---|---|
| JDK | 17 | Module 0 |
| Maven | 3.9+ | Module 1 |
| Node.js | 20+ | Module 9 |
| Oracle XE | 21c, on `localhost:1521`, PDB `XEPDB1` | Module 5 (Lab 3.0) |
| `sqlplus` | bundled with Oracle | Module 5 |
| `curl` (Windows 10+ has it built-in) | system | — |

The bootcamp dev VM is **Windows**. The scaffold's helper scripts come in two flavours:

- **PowerShell `.ps1`** — what you'll usually run on the bootcamp VM
- **bash `.sh`** — for Git Bash, WSL, macOS, or Linux

The two sets are functionally equivalent. Use whichever your shell supports.

### Day 0: run the prereqs check

Before anything else, run the prereqs verifier and paste the output to the instructor if anything fails:

```powershell
PS> .\scripts\verify-prereqs.ps1
```

It checks Java, Maven, Node, sqlplus, curl, that the five capstone ports are free, and that Oracle is listening. Each row prints `[OK]` or `[FAIL]` with a short reason — easy to share.

If you prefer to spot-check by hand:

```powershell
PS> java -version          # → 17.x
PS> mvn -version           # → 3.9.x
PS> node --version         # → v20.x
PS> sqlplus -V             # → SQL*Plus: Release 21.x
```

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
| Google sign-in via 
eact-oidc-context (PKCE) | **TODO** — drop in `VITE_GOOGLE_CLIENT_ID` |
| Pages for accounts, account detail, new transaction, admin users | Done — minimal, extend the UX |
| WireMock stub for the Payment Processor | Done — runs as a standalone JVM process |
| Smoke test for `/health` | Done |
| Sample unit test for `TransactionService` | Done |
| Smoke integration test for the BFF | Done |

## What you must build

Look in the code for `// TODO` and `throw new UnsupportedOperationException`. The big ones:

| Where | What |
|---|---|
| `resource-server/.../service/TransactionService.applyTransferOut(...)` | Internal transfer (two rows) **and** external transfer (Payment Processor call). |
| `resource-server/.../service/TransactionServiceTest` | Unit tests for deposit, ownership, transfer (internal + external happy/failure). |
| Resource Server integration tests | Use `@SpringBootTest` + MockMvc + `.with(jwt())` + `@EmbeddedKafka`. Cover 401, 403, ownership, deposit happy path with Kafka emission. |
| `docs/sast-findings.md` | Run Checkmarx, write up findings. |
| `docs/dast-payloads.md` | Run a DAST scan + 3 custom banking payloads. |
| `docs/architecture.md` | One diagram + one page describing your design decisions. |

## First-time setup

### 1. Create the `bankapp` Oracle schema (once)

```powershell
PS> cd scaffolding
PS> sqlplus "sys/<your-sys-password>@//localhost:1521/XEPDB1 as sysdba" '@scripts/setup-oracle.sql'
```

(The same command works in bash; the only Windows quirk is quoting the SYSDBA connect string.)

### 2. Configure backend env vars

PowerShell:
```powershell
PS> Copy-Item .env.example .env
PS> notepad .env
# Edit .env. At minimum, set ORACLE_PASSWORD.
# Defaults for AUTH_SERVER_URL / OAUTH_CLIENT_ID / etc. work with the
# in-scaffold mock-auth out of the box.
```

bash:
```bash
cp .env.example .env
$EDITOR .env
```

`.env` is gitignored — never commit it.

## Running the stack

You'll need **four** terminals. Start them in order — later services depend on earlier ones being up.

The `.ps1` and `.sh` versions are interchangeable; pick whichever your shell supports. Examples below show PowerShell first.

### Terminal 1 — mock Authorization Server (start first)

```powershell
PS> cd scaffolding
PS> .\scripts\start-mock-auth.ps1
# bash: ./scripts/start-mock-auth.sh
```

Wait for "Started MockAuthApplication". Verify:

```powershell
PS> curl -UseBasicParsing http://localhost:9000/.well-known/openid-configuration | Select -First 1
# {"issuer":"http://localhost:9000",...}
```

### Terminal 2 — Resource Server

```powershell
PS> cd scaffolding
PS> .\scripts\start-resource-server.ps1
# bash: ./scripts/start-resource-server.sh
```

Wait for "Started BankingApplication". Flyway runs the schema migrations on first boot.

```powershell
PS> curl -UseBasicParsing http://localhost:8081/health
# {"status":"UP"}
PS> curl -UseBasicParsing http://localhost:8081/api/v1/accounts
# 401 (no token)
```

### Terminal 3 — BFF

```powershell
PS> .\scripts\start-bff.ps1
# bash: ./scripts/start-bff.sh
```

Wait for "Started BffApplication".

### Terminal 4 — Frontend

```powershell
PS> .\scripts\start-frontend.ps1
# bash: ./scripts/start-frontend.sh
```

First run does `npm install` (a few minutes). Then Vite serves on `http://localhost:5173`.

Open `http://localhost:5173` in a browser. Click **Sign in**. Log in as `alice` / `alice`. You should land back signed in.

**The headline check:** open DevTools → Application. You should see exactly two cookies:
- `JSESSIONID` (HttpOnly)
- `XSRF-TOKEN`

`sessionStorage` and `localStorage` should be **empty**. That's the BFF win.

### Optional terminal — WireMock

```powershell
PS> .\scripts\start-wiremock.ps1
# bash: ./scripts/start-wiremock.sh
```

Needed only when you're testing external transfers.

### Optional terminal — Kafka console consumer

The exact command depends on your Kafka install. On the bootcamp Windows VM (Lab 4.1), the tools are `.bat` scripts:

```cmd
kafka-console-consumer.bat ^
  --bootstrap-server %KAFKA_BOOTSTRAP_SERVERS% ^
  --topic transactions.completed ^
  --from-beginning ^
  --property print.key=true --property key.separator=" | "
```

On Linux/macOS the equivalent is `kafka-console-consumer.sh` with `\` line continuations.

## Tests

```powershell
PS> cd backend
PS> mvn test                                    # all three modules
PS> mvn -pl resource-server test                # just RS
PS> mvn -pl bff test                            # just BFF
```

(Same commands work in bash.)

## Common gotchas

- **PowerShell script execution blocked** — first time you run a `.ps1`, Windows may say "running scripts is disabled." Run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once (in an admin or user PowerShell), or invoke as `powershell -ExecutionPolicy Bypass -File .\scripts\start-bff.ps1`.
- **BFF fails to boot with "Connection refused"** — mock-auth isn't up yet. The BFF fetches `/.well-known/openid-configuration` at startup. Always start mock-auth first.
- **Resource Server fails to boot with "Connection refused"** — same issue. Both BFF and RS depend on mock-auth.
- **SPA fetches return 404** — Vite proxy isn't running. Use `start-frontend.ps1` (which runs `npm run dev` from the right directory).
- **CSRF 403 on POST** — the SPA isn't reading the `XSRF-TOKEN` cookie correctly. The cookie is `HttpOnly: false` on purpose so JS can read it. Check `apiClient.js`.
- **Login bounces in a loop** — the BFF `redirect-uri` doesn't match what's registered in `mock-auth/AuthorizationServerConfig.java`. They both must say `http://localhost:8080/login/oauth2/code/mock-auth`.
- **Flyway error: "Found non-empty schema"** — your `bankapp` schema already has tables. Re-run `scripts/setup-oracle.sql` (it drops + recreates the user).
- **`spring.jpa.hibernate.ddl-auto: validate` is intentional.** Schema lives in Flyway migrations under `db/migration/`. Don't switch to `update`.
- **Never put real secrets in `application.yml` or `.env.example`.** Real secrets go in `.env` (gitignored) or your shell environment.

## Where to learn more

- The full spec lives in [`../README.md`](../README.md) and friends — that's the contract.
- The "BFF Pattern with Spring Boot" section of `2609-JavaSpring-April6/Week 4 .../9. React/5. React and Security.pdf` (slides 61–80) is the conceptual foundation. Reread it.
- For a deep dive on the Spring components used here: `spring-boot-starter-oauth2-client`, `spring-boot-starter-oauth2-authorization-server`, and `ServletOAuth2AuthorizedClientExchangeFilterFunction` are all documented at https://docs.spring.io/spring-security/reference/.
