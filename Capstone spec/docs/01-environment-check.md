# 01 — Environment Check

Before you touch a line of code, prove every backing service is up and reachable.
You will burn an entire day chasing logic bugs that are really infrastructure
problems if you skip this.

## Prerequisites

Confirm each tool is installed and on your PATH:

| Tool | Version |
|---|---|
| JDK | 17 |
| Node.js | 18+ |
| Oracle XE | 21c (standalone on `:1521`) |
| Apache Kafka | 4.x in KRaft mode |

Maven is not required separately — IntelliJ ships with a bundled Maven that
the backend modules use.

If you are missing any of these, follow the setup guide that ships with
the scaffolding at `scaffolding/docs/1-setup.md`.

## Tasks

### Task 1.1 — Bring up infrastructure

Start Oracle XE (port 1521), Kafka (port 9092), and WireMock (port 8089)
using the helper scripts in `scripts/`. See the setup guide for full
install instructions.

### Task 1.2 — Create the `bankapp` schema

Run `scripts/setup-oracle.sql` as a privileged Oracle user. Verify with:

```powershell
sqlplus bankapp/bankapp_password@//localhost:1521/XEPDB1
```

You should land at the `SQL>` prompt without errors.

### Task 1.3 — Start the three backend services and the frontend

Use three IntelliJ run configurations for the backend services and a
terminal for the frontend. Start them **in this order**:

1. `mock-auth` (port 9000) — the BFF cannot boot without `/.well-known/openid-configuration`
2. `resource-server` (port 8081) — Flyway migrations apply on first start
3. `bff` (port 8080)
4. Frontend (`npm run dev`, port 5173)

Ports match Lab 4.6 — auth server on 9000, BFF on 8080, resource server
on 8081 — so what you learned in that lab transfers directly here.

If something fails at startup, fix it before starting the next one. A common
trap is forgetting `JAVA_HOME` for Kafka commands on Windows — see the setup
guide for the right way to set it.

### Task 1.4 — Smoke test every endpoint

The scaffolding ships an `http-tests/banking.http` file. Open it in IntelliJ
(or use `curl`) and confirm:

| Request | Expected |
|---|---|
| `GET http://localhost:9000/.well-known/openid-configuration` | 200, JSON metadata |
| `GET http://localhost:8081/health` | 200, `{"status":"UP"}` (Resource Server) |
| `GET http://localhost:8080/health` | 200 (BFF) |
| `GET http://localhost:8081/api/v1/accounts` | **401** (no token, direct to RS) |
| `GET http://localhost:8080/api/v1/accounts` | **401** or 302 (no session, via BFF) |
| `http://localhost:5173/` | renders the SPA shell |

If any of these are red, **stop and fix infrastructure**. Code work assumes a
healthy environment.

### Task 1.5 — Verify Kafka

In a separate terminal:

```bash
kafka-topics.bat --list --bootstrap-server localhost:9092
```

Empty output is fine. The topic `banking.transactions` will be auto-created
the first time the resource server publishes an event. If the command errors
out, your Kafka broker is not running.

### Task 1.6 — Verify WireMock (you can skip this until Day 2)

```bash
curl http://localhost:8089/__admin/mappings
```

Expect two stubs: `payment-success` (any `/payments` POST) and
`payment-failure-large` (POST `/payments` with `amount > 10000`).

## Done when

- [ ] All three backend services and the frontend start cleanly from a fresh shell.
- [ ] Every smoke-test row above is green.
- [ ] Every team member has run the smoke test on their own machine.
- [ ] `docs/team-plan.md` records who owns which lead role (see chapter 00).

Now move on to [02-codebase-tour.md](./02-codebase-tour.md).
