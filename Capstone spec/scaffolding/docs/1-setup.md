# BC Java Capstone — Infrastructure Setup Guide

> Reflects the working configuration as of April 2026.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Google Cloud OAuth2 Setup](#google-cloud-oauth2-setup)
3. [Infrastructure Setup — Choose Your Path](#infrastructure-setup--choose-your-path)
   - [Option A: Docker Compose](#option-a-docker-compose-recommended)
   - [Option B: Local Installation](#option-b-local-installation-no-docker)
4. [Backend — IntelliJ Run Configuration](#backend--intellij-run-configuration)
5. [Seeding Demo Accounts](#seeding-demo-accounts)
6. [Frontend — Vite Dev Server](#frontend--vite-dev-server)
7. [Startup Order Checklist](#startup-order-checklist)
8. [Known Issues & Fixes Applied](#known-issues--fixes-applied)

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 17 | Microsoft Build of OpenJDK (`ms-17.0.18`) tested — IntelliJ downloads this automatically |
| IntelliJ IDEA | 2024+ | Used as the backend IDE and build tool |
| Node.js | 18+ | For the Vite frontend dev server |
| npm | 9+ | Comes with Node.js |
| **Docker Desktop** | Any recent | **Option A only** — must be running before starting containers |

---

## Google Cloud OAuth2 Setup

The application supports **Google OIDC** as well as a local **mock authorization server** for demo/testing.

### Setting Up Google OAuth2 (Optional — for Google login)

1. Go to [Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials).
2. Create an **OAuth 2.0 Client ID** of type **Web Application**.
3. Add the following **Authorized Redirect URIs**:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
4. Note your **Client ID** and **Client Secret**.
5. Add them to the BFF's `application.yml` or pass as environment variables:

   ```
   GOOGLE_CLIENT_ID=<your-client-id>
   GOOGLE_CLIENT_SECRET=<your-client-secret>
   ```

> [!NOTE]
> If you skip Google setup you can still use the **Demo login** (`alice` / `password` or `admin` / `password`) via the mock authorization server on port 9000.

> [!IMPORTANT]
> Never commit `client_secret` to version control.

---

## Infrastructure Setup — Choose Your Path

The capstone needs three backing services:

| Service | Docker port | Standalone port | Purpose |
|---|---|---|---|
| Oracle XE 21c | 1521 | 1522 | Primary database |
| Apache Kafka | 9092 | 9092 | Event streaming |
| WireMock | 8089 | 8089 | Payment processor stub |

---

### Option A: Docker Compose (Recommended)

Use this path if Docker Desktop is installed and working on your machine.

#### Start all services

```powershell
# From the solution/ directory
docker compose up -d
```

#### Service details

| Service | Image |
|---|---|
| `oracle` | `gvenzl/oracle-xe:21-slim` |
| `zookeeper` | `confluentinc/cp-zookeeper:7.5.0` |
| `kafka` | `confluentinc/cp-kafka:7.5.0` |
| `wiremock` | `wiremock/wiremock:3.3.1` |

#### Wait for Oracle

Oracle XE takes **30–90 seconds** to fully initialize on first run:

```powershell
docker logs -f capstone-oracle
```

Wait until you see: `DATABASE IS READY TO USE`

#### Oracle credentials (Docker)

| Variable | Value |
|---|---|
| `APP_USER` | `bankapp` |
| `APP_USER_PASSWORD` | `bankapp` |
| SYS/SYSTEM password | `my_secure_password` |

#### Stop services

```powershell
docker compose down
# Full reset (wipes database volume):
docker compose down -v
```

> [!CAUTION]
> `docker compose down -v` deletes all data. You will need to re-seed demo accounts after the next startup.

---

### Option B: Local Installation (No Docker)

Use this path if Docker is not available or not working on your machine.

You need to install and start three services manually: **Oracle XE**, **Kafka**, and **WireMock**.

---

#### B1. Oracle XE 21c (Local)

**Install**

1. Download **Oracle Database 21c Express Edition** for Windows from:
   https://www.oracle.com/database/technologies/xe-downloads.html
2. Run the installer. When prompted, set a password for SYS/SYSTEM (e.g., `my_secure_password`).
3. Oracle registers itself as a Windows service (`OracleServiceXE`) and starts automatically.

**Create the `bankapp` user**

Open SQL*Plus (installed with Oracle) and run the setup script:

```powershell
C:\app\<username>\product\21c\dbhomeXE\bin\sqlplus sys/<sys-password>@//localhost:1522/XEPDB1 as sysdba @scripts\setup-oracle.sql
```

> [!NOTE]
> Standalone Oracle XE uses port **1522** (not 1521). Port 1521 is for Docker.
> sqlplus is at `C:\app\<username>\product\21c\dbhomeXE\bin\sqlplus.exe` (e.g. `C:\app\johndoe\product\21c\dbhomeXE\bin\sqlplus.exe`)

> [!TIP]
> This script is **rerunnable** — it drops and recreates `bankapp` if it already exists.
> **Already done on this machine** — `bankapp` user exists in the standalone Oracle.

**Verify connection**

```powershell
sqlplus bankapp/bankapp_password@//localhost:1522/XEPDB1
```

---

#### B2. Apache Kafka (Local — KRaft mode, no Zookeeper)

Kafka 4.x runs in **KRaft mode** — no Zookeeper required.

**Install**

Download the latest Kafka binary from https://kafka.apache.org/downloads and extract to `C:\kafka`.  
*(Or use the existing installation at `C:\kafka` if already present.)*

**Set JAVA_HOME** (required before running any Kafka commands):

```powershell
$env:JAVA_HOME = "C:\Users\<username>\.jdks\ms-17.0.18"   # e.g. C:\Users\johndoe\.jdks\ms-17.0.18
$env:PATH = $env:JAVA_HOME + "\bin;" + $env:PATH
```

> [!IMPORTANT]
> Use `+` concatenation for PATH — **not** `"$env:JAVA_HOME\bin;..."` (the double-quoted string form breaks on Windows due to backslash parsing).

> [!TIP]
> Find your JDK path with: `Get-ChildItem "$env:USERPROFILE\.jdks"`

**Configure data directory**

Open `C:\kafka\config\server.properties` and update:

```properties
# Use forward slashes even on Windows
log.dirs=c:/kafka/data
```

**Format storage (one-time setup)**

```powershell
cd C:\kafka

# 1. Generate a cluster UUID
.\bin\windows\kafka-storage.bat random-uuid
# Copy the output, e.g.: Z9uw6GjRT7as3cFwihU8sw

# 2. Format the data directory with that UUID
.\bin\windows\kafka-storage.bat format -t <your-uuid> -c config\server.properties --standalone
```

Expected output: `Formatting dynamic metadata voter directory c:/kafka/data with metadata.version ...`

**Start the broker**

Use the script (handles JAVA_HOME automatically):

```powershell
scripts\start-kafka.bat
```

Or manually in a dedicated terminal:

```powershell
$env:JAVA_HOME = "C:\Users\<username>\.jdks\ms-17.0.18"   # e.g. C:\Users\johndoe\.jdks\ms-17.0.18
$env:PATH = $env:JAVA_HOME + "\bin;" + $env:PATH
cd C:\kafka
.\bin\windows\kafka-server-start.bat config\server.properties
```

Kafka is ready when you see: `[KafkaServer id=1] started`

**Verify**

In a new terminal:

```powershell
$env:JAVA_HOME = "C:\Users\<username>\.jdks\ms-17.0.18"   # e.g. C:\Users\johndoe\.jdks\ms-17.0.18
$env:PATH = $env:JAVA_HOME + "\bin;" + $env:PATH
C:\kafka\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

Expected: empty output (no topics yet). The `ERROR Reconfiguration failed` line is a harmless Log4j warning.

**Stop**

```powershell
.\bin\windows\kafka-server-stop.bat
```

---

#### B3. WireMock (Local)

WireMock runs as a standalone JAR. The `start-wiremock.bat` script downloads the JAR automatically on first run (into `scripts\.cache\`) and points it at the repo's stub mappings — no manual file copying needed.

**Start WireMock**

```powershell
# From the solution\ directory
scripts\start-wiremock.bat
```

Or manually:

```powershell
$env:JAVA_HOME = "C:\Users\<username>\.jdks\ms-17.0.18"   # e.g. C:\Users\johndoe\.jdks\ms-17.0.18
$env:PATH = $env:JAVA_HOME + "\bin;" + $env:PATH
cd "<repo>\solution"
java -jar scripts\.cache\wiremock-standalone-3.6.0.jar `
     --port 8089 `
     --root-dir wiremock-stubs `
     --global-response-templating
```

WireMock is ready when you see `Started WireMock` in the output.

**Verify**

```powershell
Invoke-RestMethod http://localhost:8089/__admin/mappings | ConvertTo-Json -Depth 3
```

Expected: 2 mappings — `payment-success` (200) and `payment-failure-large` (503 priority 1).

You should see `payment-success` and `payment-failure-large` mappings loaded.

**Stub behaviour**

| Request | Response |
|---|---|
| `POST /payments` with `amount <= 10000` | `200 ACCEPTED` with random processor reference |
| `POST /payments` with `amount > 10000` | `503 REJECTED` — exceeds processor limit |

---

## Backend — IntelliJ Run Configuration

The backend is a **multi-module Maven project** with three Spring Boot applications. Start them in this order:

| Module | Main class | Port |
|---|---|---|
| `mock-auth` | `com.example.mockauth.MockAuthApplication` | 9000 |
| `resource-server` | `com.example.banking.BankingApplication` | 8081 |
| `bff` | `com.example.bff.BffApplication` | 8080 |

### Required Environment Variables

Set these in the IntelliJ run/debug configuration under **Environment variables**:

**Resource Server (port 8081):**

| Variable | Docker value | Standalone value | Notes |
|---|---|---|---|
| `ORACLE_PASSWORD` | `bankapp_password` | `bankapp_password` | Must match the DB password |
| `ORACLE_URL` | *(omit — default used)* | `jdbc:oracle:thin:@//localhost:1522/XEPDB1` | **Standalone only** — port is 1522 |
| `ORACLE_USER` | *(omit — default `bankapp`)* | `bankapp` | Only needed if you used a different username |
| `GOOGLE_CLIENT_ID` | `<your-google-client-id>` | `<your-google-client-id>` | Required only if using Google login |

**BFF (port 8080):**

| Variable | Value | Notes |
|---|---|---|
| `GOOGLE_CLIENT_ID` | `<your-google-client-id>` | Required only if using Google login |
| `GOOGLE_CLIENT_SECRET` | `<your-google-client-secret>` | Required only if using Google login |

> [!NOTE]
> For **Docker**, `ORACLE_URL` defaults to `jdbc:oracle:thin:@//localhost:1521/XEPDB1` — no need to set it.
> For **standalone Oracle XE**, you must set `ORACLE_URL=jdbc:oracle:thin:@//localhost:1522/XEPDB1` in the IntelliJ run config.

### Database Migrations

Flyway runs automatically on resource-server startup. Migrations are at:

```
solution/backend/resource-server/src/main/resources/db/migration/
  V1__initial_schema.sql      — Creates BANK_USERS, ACCOUNTS, TRANSACTIONS tables
  V2__seed_admin.sql          — Seeds a placeholder ADMIN user row
  V3__seed_demo_accounts.sql  — Seeds demo accounts for the placeholder user
```

---

## Seeding Demo Accounts

After first login, your user row is auto-created but has no accounts. Seed them manually.

**1. Log in to the app at least once** at http://localhost:5173 (creates your `BANK_USERS` row).

**2. Connect to Oracle:**

*Docker:*
```powershell
docker exec -it capstone-oracle sqlplus bankapp/bankapp_password@XEPDB1
```

*Local (standalone):*
```powershell
C:\app\<username>\product\21c\dbhomeXE\bin\sqlplus bankapp/bankapp_password@//localhost:1522/XEPDB1
```

**3. Find your USER_ID:**

```sql
SELECT USER_ID, EMAIL, SUBJECT FROM BANK_USERS;
```

**4. Insert accounts** (replace `usr_abc123xyz` with your real `USER_ID`):

```sql
INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
VALUES ('acc_my_checking', 'usr_abc123xyz', 'CHECKING', 'USD', 5000.00);

INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
VALUES ('acc_my_savings', 'usr_abc123xyz', 'SAVINGS', 'USD', 12500.00);

COMMIT;
EXIT;
```

**5. Refresh** http://localhost:5173 — both accounts should appear.

> [!TIP]
> To grant yourself ADMIN access:
> ```sql
> UPDATE BANK_USERS SET ROLE = 'ADMIN' WHERE EMAIL = 'your@email.com';
> COMMIT;
> ```
> Then sign out and back in.

---

## Frontend — Vite Dev Server

### Starting the Dev Server

```powershell
cd solution/frontend
npm install     # First time only
npm run dev
```

The app will be available at **http://localhost:5173**.

---

## Startup Order Checklist

### Option A — Docker Compose

- [ ] **1.** Start Docker Desktop
- [ ] **2.** `docker compose up -d` (from `solution/`)
- [ ] **3.** Wait for Oracle: `docker logs -f capstone-oracle` → `DATABASE IS READY TO USE`
- [ ] **4.** Start `mock-auth` (port 9000) in IntelliJ
- [ ] **5.** Start `resource-server` (port 8081) in IntelliJ — confirm Flyway migrations applied
- [ ] **6.** Start `bff` (port 8080) in IntelliJ
- [ ] **7.** `npm run dev` in `solution/frontend/`
- [ ] **8.** Open http://localhost:5173 and sign in
- [ ] **9.** Seed demo accounts via SQL*Plus (first time only)

### Option B — Local Installation

- [ ] **1.** Ensure Oracle XE service is running (`OracleServiceXE` in Windows Services)
- [ ] **2.** Confirm `ORACLE_URL=jdbc:oracle:thin:@//localhost:1522/XEPDB1` is set in IntelliJ resource-server run config
- [ ] **3.** Start Kafka: run `scripts\start-kafka.bat` in its own terminal (leave it running)
- [ ] **4.** Start WireMock: run `scripts\start-wiremock.bat` in its own terminal (leave it running)
- [ ] **5.** Start `mock-auth` (port 9000) in IntelliJ
- [ ] **6.** Start `resource-server` (port 8081) in IntelliJ — confirm Flyway migrations applied
- [ ] **7.** Start `bff` (port 8080) in IntelliJ
- [ ] **8.** `npm run dev` in `solution/frontend/`
- [ ] **9.** Open http://localhost:5173 and sign in
- [ ] **10.** Seed demo accounts via SQL*Plus (first time only)

---

## Known Issues & Fixes Applied

### Flyway Checksum Mismatch on V2 Migration

**Symptom:** Resource server fails to start with:
```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 2
-> Applied to database : -769290703
-> Resolved locally    : 751285612
```

**Root Cause:** `V2__seed_admin.sql` was edited after Flyway had already applied it to the database. The stored checksum in `flyway_schema_history` no longer matches the local file.

**Fix applied:** `validate-on-migrate: false` is set in `application.yml`. Flyway will still run pending migrations but will not abort on checksum differences for already-applied ones. No action required — just restart the application.

---

### ORA-18716: not in any time zone (Critical)

**Symptom:** `ORA-18716: not in any time zone` on every authenticated request.

**Root Cause:** `gvenzl/oracle-xe:21-slim` does not ship Oracle timezone region data. Hibernate 6 + Oracle JDBC try to resolve timezone region names, which fails.

**Fixes applied:**

| Layer | Fix |
|---|---|
| JDBC | `oracle.jdbc.timezoneAsRegion=false` in HikariCP `connection-properties` |
| Hibernate | `hibernate.timezone.default_storage: NORMALIZE` |
| Entities | `java.time.Instant` → `java.time.LocalDateTime` in all entities/DTOs |

---

### docker-compose.yml — Wrong Environment Variable Name

**Symptom:** Oracle starts but `bankapp` user is never created. Spring Boot gets `ORA-01017`.

**Fix:** Use `APP_USER_PASSWORD`, not `APP_PASSWORD`.

---

### Logout Returns 403

**Symptom:** Clicking Sign out gets a `403 Forbidden` from the BFF.

**Root Cause:** The XSRF-TOKEN cookie may not be present when the form is submitted, sending an empty/missing `_csrf` parameter.

**Fix:** Sign out uses `apiFetch("/logout", { method: "POST" })` which sends the `X-XSRF-TOKEN` header — consistent with all other mutating API calls.

---

### Infinite Page Refresh on 401

**Symptom:** Unauthenticated page reloads infinitely.

**Root Cause:** `apiClient.js` redirected to `/` on 401 → page loaded → API called → 401 → redirect to `/` → loop.

**Fix:** On 401, `apiFetch` throws `ApiError(401)`. The `useMe` hook catches it and sets `user = null`, which causes `AppLayout` to render the sign-in page without any navigation.


---
