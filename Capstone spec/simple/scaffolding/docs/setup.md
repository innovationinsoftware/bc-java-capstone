# BC Java Capstone — Infrastructure Setup Guide

> Generated from active debugging session. Reflects the working configuration as of April 2026.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Google Cloud OAuth2 Setup](#google-cloud-oauth2-setup)
3. [Docker Infrastructure](#docker-infrastructure)
4. [Backend — IntelliJ Run Configuration](#backend--intellij-run-configuration)
5. [Seeding Demo Accounts](#seeding-demo-accounts)
6. [Frontend — Vite Dev Server](#frontend--vite-dev-server)
7. [Startup Order Checklist](#startup-order-checklist)
8. [Known Issues & Fixes Applied](#known-issues--fixes-applied)

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 17 | Microsoft Build of OpenJDK (`ms-17.0.18`) tested |
| IntelliJ IDEA | 2024+ | Used as the backend IDE and build tool |
| Docker Desktop | Any recent | Must be running before starting containers |
| Node.js | 18+ | For the Vite frontend dev server |
| npm | 9+ | Comes with Node.js |

---

## Google Cloud OAuth2 Setup

The application uses **Google OIDC** (OpenID Connect) for user authentication.

### Steps

1. Go to [Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials).
2. Create an **OAuth 2.0 Client ID** of type **Web Application**.
3. Add the following **Authorized Redirect URI**:
   ```
   http://localhost:5173
   ```
4. Note down the **Client ID** and **Client Secret** — you will need both below.

### Why Both Client ID and Client Secret?

Google's OAuth2 flow for **Web Application** clients (as opposed to SPA/public clients) requires the `client_secret` to be sent during the token exchange. The `oidc-client-ts` library in the frontend requires this to be provided explicitly.

> [!IMPORTANT]
> Never commit the `client_secret` to version control. It is stored only in `.env.local` which is git-ignored.

---

## Docker Infrastructure

All backing services run via Docker Compose. The compose file is located at:

```
Capstone spec/docker-compose.yml
```

### Services

| Service | Image | Port | Purpose |
|---|---|---|---|
| `oracle` | `gvenzl/oracle-xe:21-slim` | `1521` | Primary database |
| `zookeeper` | `confluentinc/cp-zookeeper:7.5.0` | `2181` | Kafka coordinator |
| `kafka` | `confluentinc/cp-kafka:7.5.0` | `9092` | Event streaming |
| `wiremock` | `wiremock/wiremock:3.3.1` | `8089` | Payment processor stub |

### Starting Infrastructure

```powershell
# From the "Capstone spec" directory
docker compose up -d
```

### Stopping Infrastructure

```powershell
docker compose down
# To also wipe the database volume (full reset):
docker compose down -v
```

### Oracle Database Credentials

The `docker-compose.yml` sets up the Oracle container with these credentials:

| Variable | Value |
|---|---|
| `ORACLE_PASSWORD` | `my_secure_password` (SYS/SYSTEM password) |
| `APP_USER` | `bankapp` |
| `APP_USER_PASSWORD` | `bankapp_password` |

The Spring Boot backend connects as the `bankapp` user to the `XEPDB1` pluggable database.

> [!CAUTION]
> The environment variable for the app user password **must** be `APP_USER_PASSWORD` (not `APP_PASSWORD`). Using the wrong name causes the Oracle container to start without creating the `bankapp` schema, resulting in Flyway/connection failures.

### Waiting for Oracle to Be Ready

Oracle XE takes **30–90 seconds** to fully initialize on first run. The `healthcheck.sh` script in the container signals readiness. Watch for it:

```powershell
docker logs -f capstone-oracle
```

Wait until you see a line containing `DATABASE IS READY TO USE`.

---

## Backend — IntelliJ Run Configuration

### Build

The backend is a **Maven** project. Use IntelliJ's built-in Maven integration:
- Run → Edit Configurations → **Application** configuration (not Maven)
- Main class: `com.example.banking.BankingApplication`

### Required Environment Variables

Set these in the IntelliJ run/debug configuration under **Environment variables**:

| Variable | Value | Notes |
|---|---|---|
| `ORACLE_PASSWORD` | `bankapp_password` | Must match `APP_USER_PASSWORD` in `docker-compose.yml` |
| `GOOGLE_CLIENT_ID` | `<your-google-client-id>` | From Google Cloud Console |
| `PAYMENT_PROCESSOR_API_KEY` | `dev-only-not-secret` | Any value works in dev; WireMock doesn't validate it |

> [!NOTE]
> `ORACLE_URL` and `ORACLE_USER` have defaults in `application.yml` (`jdbc:oracle:thin:@//localhost:1521/XEPDB1` and `bankapp`) and do **not** need to be set unless you change the database location.

### application.yml — Key Configuration

Located at `solution/backend/src/main/resources/application.yml`.

**Critical settings applied during this session:**

```yaml
spring:
  datasource:
    hikari:
      # Prevents ORA-18716: gvenzl/oracle-xe:21-slim is missing timezone region data.
      # Forces the JDBC driver to use numeric UTC offsets instead of region names.
      connection-properties: oracle.jdbc.timezoneAsRegion=false

  jpa:
    properties:
      # Hibernate 6 maps Instant to TIMESTAMP WITH TIME ZONE by default on Oracle.
      # Our DDL uses plain TIMESTAMP, so NORMALIZE prevents ORA-18716 at the Hibernate layer.
      hibernate.timezone.default_storage: NORMALIZE

  security:
    oauth2:
      resourceserver:
        jwt:
          jwks-uri: https://www.googleapis.com/oauth2/v3/certs
          issuer-uri: https://accounts.google.com

bank:
  google-client-id: ${GOOGLE_CLIENT_ID}
  cors:
    allowed-origin: ${SPA_ORIGIN:http://localhost:5173}
  payment-processor:
    base-url: ${PAYMENT_PROCESSOR_URL:http://localhost:8089}
    api-key: ${PAYMENT_PROCESSOR_API_KEY:dev-only-not-secret}

server:
  port: 8081
```

### Database Migrations

Flyway runs automatically on startup. Migration scripts are located at:

```
solution/backend/src/main/resources/db/migration/
  V1__initial_schema.sql      — Creates BANK_USERS, ACCOUNTS, TRANSACTIONS tables
  V2__seed_admin.sql          — Seeds a placeholder ADMIN user row
  V3__seed_demo_accounts.sql  — Seeds two demo accounts (CHECKING + SAVINGS) for the placeholder admin
```

> [!NOTE]
> `ddl-auto: validate` is set — Hibernate will **validate** the schema against the entities but will not create or alter tables. All schema changes must go through Flyway migrations.

---

## Seeding Demo Accounts

The domain spec intentionally omits an "open account" UI — account creation is out of scope. For dev and demo, accounts are seeded manually after first login.

### Why Manual Seeding Is Required

Flyway V2 seeds a placeholder `BANK_USERS` row (`usr_seed_admin`) whose `SUBJECT` does not match any real Google account. When **you** log in with Google for the first time, `JwtAuthConverter` auto-creates a *new* row for your real `sub` claim. Your accounts must be linked to **your** user row, so they can only be inserted after your first login.

### Step-by-Step

**1. Log in to the app at least once** so your `BANK_USERS` row is created.

**2. Open a SQL*Plus session inside the Oracle container:**

```powershell
docker exec -it capstone-oracle sqlplus bankapp/bankapp_password@XEPDB1
```

**3. Find your USER_ID:**

```sql
SELECT USER_ID, EMAIL, SUBJECT FROM BANK_USERS;
```

Expected output:
```
USER_ID              EMAIL                      SUBJECT
-------------------- -------------------------- ---------------------------
usr_seed_admin       admin@example.com          google-sub-placeholder
usr_abc123xyz        your@gmail.com             115234567890   ← yours
```

**4. Insert your accounts** (replace `usr_abc123xyz` with your real `USER_ID`):

```sql
INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
VALUES ('acc_my_checking', 'usr_abc123xyz', 'CHECKING', 'USD', 5000.00);

INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
VALUES ('acc_my_savings', 'usr_abc123xyz', 'SAVINGS', 'USD', 12500.00);

COMMIT;

EXIT;
```

**5. Refresh the AccountsPage** — you should now see both accounts.

> [!NOTE]
> This is a one-time step per database volume. If you run `docker compose down -v` (which wipes the volume), you will need to repeat this after the next first login.

> [!TIP]
> To make your Google account an ADMIN, run:
> ```sql
> UPDATE BANK_USERS SET ROLE = 'ADMIN' WHERE EMAIL = 'your@gmail.com';
> COMMIT;
> ```
> Then log out and back in so the backend re-reads your role from the DB.

## Frontend — Vite Dev Server

### Environment File

Create `solution/frontend/.env.local` (already git-ignored):

```env
VITE_GOOGLE_CLIENT_ID=<your-google-client-id>
VITE_GOOGLE_CLIENT_SECRET=<your-google-client-secret>
VITE_API_BASE_URL=http://localhost:8081
```

> [!IMPORTANT]
> Both `VITE_GOOGLE_CLIENT_ID` **and** `VITE_GOOGLE_CLIENT_SECRET` are required. Google's **Web Application** OAuth2 client type requires the secret during the token exchange step performed by `oidc-client-ts`.

### Starting the Dev Server

```powershell
cd "Capstone spec/solution/frontend"
npm install     # First time only
npm run dev
```

The app will be available at **http://localhost:5173**.

### How Authentication Works (Frontend → Backend)

1. User clicks **Sign in with Google**.
2. `oidc-client-ts` redirects to Google's authorization endpoint.
3. Google issues an **ID token** (JWT) + access token.
4. The frontend stores both in `sessionStorage`.
5. Every API request to the backend sends the **ID token** in the `Authorization: Bearer <id_token>` header.
6. The backend's `BearerTokenAuthenticationFilter` validates the JWT against Google's JWKS endpoint.
7. `JwtAuthConverter` looks up or creates the local `BankUserEntity` by the JWT `sub` (subject) claim.

> [!NOTE]
> The **ID token** is used (not the opaque access token) because the backend is configured as an OAuth2 Resource Server expecting a signed JWT. The access token Google issues for its own APIs is opaque and cannot be validated by Spring Security's JWT filter.

---

## Startup Order Checklist

Follow this order every time you start the development environment:

- [ ] **1. Start Docker Desktop** — ensure it is running
- [ ] **2. Start infrastructure containers**
  ```powershell
  docker compose up -d
  ```
- [ ] **3. Wait for Oracle to be ready** — check `docker logs capstone-oracle` for `DATABASE IS READY TO USE`
- [ ] **4. Start the Spring Boot backend** via IntelliJ (Debug or Run configuration)
  - Confirm Flyway logs: `Successfully applied 3 migrations to schema "BANKAPP"`
  - Confirm startup: `Started BankingApplication in X seconds`
- [ ] **5. Start the frontend dev server**
  ```powershell
  npm run dev
  ```
- [ ] **6. Open http://localhost:5173 and log in with Google** (creates your BANK_USERS row)
- [ ] **7. Seed your demo accounts** via SQL*Plus — see [Seeding Demo Accounts](#seeding-demo-accounts)
- [ ] **8. Refresh the AccountsPage** — accounts should now appear

---

## Known Issues & Fixes Applied

### ORA-18716: not in any time zone (Critical)

**Symptom:** `java.sql.SQLException: ORA-18716: {0} not in any time zone.DATE` on every authenticated request.

**Root Cause (two-part):**

1. **Docker image:** `gvenzl/oracle-xe:21-slim` does not ship Oracle's timezone region data files. When the JDBC driver tries to resolve a timezone by region name (e.g., `America/Vancouver`), it fails.
2. **Hibernate 6 type mapping:** Hibernate 6 maps `java.time.Instant` to `TimestampUtcAsOffsetDateTimeJdbcType` on Oracle, which calls `ResultSet.getObject(col, OffsetDateTime.class)`. This triggers the region-based timezone lookup that the slim image cannot satisfy.

**Fixes Applied:**

| Layer | Fix |
|---|---|
| JDBC Driver | Added `oracle.jdbc.timezoneAsRegion=false` via HikariCP `connection-properties` in `application.yml` |
| Hibernate | Added `hibernate.timezone.default_storage: NORMALIZE` in `application.yml` |
| Java Entities | Changed `java.time.Instant` → `java.time.LocalDateTime` in `BankUserEntity`, `AccountEntity`, `TransactionEntity`, `AccountDto`, `TransactionDto` |

**Why `LocalDateTime` works:** Hibernate maps `LocalDateTime` to `TimestampJdbcType`, which calls `ResultSet.getTimestamp()` — a plain JDBC call with no timezone region resolution required.

---

### docker-compose.yml — Wrong Environment Variable Name

**Symptom:** Oracle container starts but `bankapp` user/schema is never created. Spring Boot fails with `ORA-01017: invalid username/password`.

**Fix:** The correct variable name is `APP_USER_PASSWORD`, not `APP_PASSWORD`.

```yaml
# WRONG
- APP_PASSWORD=bankapp_password

# CORRECT
- APP_USER_PASSWORD=bankapp_password
```

---

### Frontend Sends Wrong Token Type

**Symptom:** Backend returns `401 Unauthorized` even with a valid Google login. Backend logs show JWT validation errors.

**Root Cause:** `oidc-client-ts` by default uses the opaque `access_token` for API calls. The Spring Boot backend is a JWT Resource Server and cannot validate an opaque token.

**Fix:** Updated `apiClient.js` to extract the `id_token` (a signed JWT) from `sessionStorage` and use it in the `Authorization` header instead of the `access_token`.

---

### Google OAuth2 — Missing client_secret

**Symptom:** `{"error": "invalid_request", "error_description": "client_secret is missing."}`

**Root Cause:** The Google Cloud project uses a **Web Application** OAuth2 client, which requires the `client_secret` during token exchange. The `oidc-client-ts` `OidcClient` config was missing it.

**Fix:** Added `VITE_GOOGLE_CLIENT_SECRET` to `.env.local` and passed it as `client_secret` in the `AuthProvider.jsx` OIDC configuration.
