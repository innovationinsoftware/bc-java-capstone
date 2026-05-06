# BC Java Capstone — Infrastructure Setup Guide

> Reflects the working configuration as of April 2026.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Infrastructure Setup](#infrastructure-setup)
   - [Oracle XE 21c](#oracle-xe-21c)
   - [Apache Kafka](#apache-kafka)
   - [WireMock](#wiremock)
3. [Backend — IntelliJ Run Configuration](#backend--intellij-run-configuration)
4. [Seeding Demo Accounts](#seeding-demo-accounts)
5. [Frontend — Vite Dev Server](#frontend--vite-dev-server)
6. [Startup Order Checklist](#startup-order-checklist)
7. [Known Issues & Fixes Applied](#known-issues--fixes-applied)

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 17 | Microsoft Build of OpenJDK (`ms-17.0.18`) tested — IntelliJ downloads this automatically |
| IntelliJ IDEA | 2024+ | Used as the backend IDE and build tool |
| Node.js | 18+ | For the Vite frontend dev server |
| npm | 9+ | Comes with Node.js |

---

## Infrastructure Setup

The capstone needs three backing services, all run natively on your VM:

| Service | Port | Purpose |
|---|---|---|
| Oracle XE 21c | 1521 | Primary database |
| Apache Kafka | 9092 | Event streaming |
| WireMock | 8089 | Payment processor stub |

You install and start each one as described below. The Kafka and WireMock
helper scripts in `scripts/` handle most of the busywork.

---

### Oracle XE 21c

**Install**

1. Download **Oracle Database 21c Express Edition** for Windows from:
   https://www.oracle.com/database/technologies/xe-downloads.html
2. Run the installer. When prompted, set a password for SYS/SYSTEM (e.g., `my_secure_password`).
3. Oracle registers itself as a Windows service (`OracleServiceXE`) and starts automatically.

**Create the `bankapp` user**

Open SQL*Plus (installed with Oracle) and run the setup script:

```powershell
sqlplus sys/password@localhost:1521/XEPDB1 as sysdba @scripts\setup-oracle.sql
```

> [!TIP]
> This script is **rerunnable** — it drops and recreates `bankapp` if it already exists.
> **Already done on this machine** — `bankapp` user exists in the standalone Oracle.

**Verify connection**

```powershell
sqlplus bankapp/bankapp_password@//localhost:1521/XEPDB1
```

---

### Apache Kafka (KRaft mode, no Zookeeper)

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

### WireMock

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

| Variable | Value | Notes |
|---|---|---|
| `ORACLE_URL` | `jdbc:oracle:thin:@//localhost:1521/XEPDB1` | Required — port is 1521 |
| `ORACLE_USER` | `bankapp` | Only needed if you used a different username |
| `ORACLE_PASSWORD` | `bankapp_password` | Must match the DB password |

**BFF (port 8080):**

The BFF reads its OAuth2 client configuration from `application.yml`,
pointed at the local mock authorization server on port 9000. No
environment variables are required for authentication.

### Mock-Auth Demo Users

Mock-auth ships with two in-memory users (defined in `AuthorizationServerConfig.java`).
There is no signup flow — these are the only credentials that work.

| Username | Password   | Role     |
|----------|------------|----------|
| `alice`  | `password` | CUSTOMER |
| `admin`  | `password` | ADMIN    |

> [!NOTE]
> The username is **not** the password. Both users share the literal password `password`.
> These users live in mock-auth, not Oracle — the DB-side `BANK_USERS` row is created
> on first login and linked by the JWT `sub` claim.

---

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

**1. Log in to the app at least once** at http://localhost:5173 as `alice` / `password` (creates your `BANK_USERS` row). See [Mock-Auth Demo Users](#mock-auth-demo-users).

**2. Connect to Oracle:**

```powershell
sqlplus bankapp/bankapp_password@//localhost:1521/XEPDB1
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
> The fastest way to get ADMIN access is to sign out and sign back in as `admin` / `password` —
> mock-auth's `admin` user already has the ADMIN role on its JWT.
>
> To promote your own `alice`-linked row instead:
> ```sql
> UPDATE BANK_USERS SET ROLE = 'ADMIN' WHERE EMAIL = 'your@email.com';
> COMMIT;
> ```
> Then sign out and back in.

---

## Frontend — Vite Dev Server

### Starting the Dev Server

```powershell
cd scaffolding/frontend
npm install     # First time only
npm run dev
```

The app will be available at **http://localhost:5173**.

---

## Startup Order Checklist

- [ ] **1.** Ensure Oracle XE service is running (`OracleServiceXE` in Windows Services)
- [ ] **2.** Confirm `ORACLE_URL=jdbc:oracle:thin:@//localhost:1521/XEPDB1` is set in IntelliJ resource-server run config
- [ ] **3.** Start Kafka: run `scripts\start-kafka.bat` in its own terminal (leave it running)
- [ ] **4.** Start WireMock: run `scripts\start-wiremock.bat` in its own terminal (leave it running)
- [ ] **5.** Start `mock-auth` (port 9000) in IntelliJ
- [ ] **6.** Start `resource-server` (port 8081) in IntelliJ — confirm Flyway migrations applied
- [ ] **7.** Start `bff` (port 8080) in IntelliJ
- [ ] **8.** `npm run dev` in `frontend/`
- [ ] **9.** Open http://localhost:5173 and sign in as `alice` / `password` (or `admin` / `password` for ADMIN)
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

### ORA-18716: not in any time zone

**Symptom:** `ORA-18716: not in any time zone` on every authenticated request that touches a TIMESTAMP column.

**Root Cause:** Hibernate 6 + Oracle JDBC can try to resolve timezone region names against an Oracle install that lacks the region data. Local standalone Oracle XE 21c ships the region data, but the codebase keeps the defensive fixes in place.

**Fixes already applied (no action required):**

| Layer | Fix |
|---|---|
| JDBC | `oracle.jdbc.timezoneAsRegion=false` in HikariCP `connection-properties` |
| Hibernate | `hibernate.timezone.default_storage: NORMALIZE` |
| Entities | `java.time.Instant` → `java.time.LocalDateTime` in all entities/DTOs |

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
