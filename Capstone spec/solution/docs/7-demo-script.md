# Demo Script

## Timing overview

| Step | Topic | Time |
|---|---|---|
| 1 | Architecture intro | 2 min |
| 2 | Login flow (mock-auth + Google) | 3 min |
| 3 | Customer flow — accounts, transfers, Kafka | 5 min |
| 4 | Admin flow + 403 boundary test | 2 min |
| 5 | SAST highlight | 2 min |
| 6 | DAST highlight | 2 min |

---

## Step 1 — Architecture intro (2 min)

Open `docs/architecture.md` and walk through the Mermaid diagram.

Key points to hit:
- Three Spring Boot processes (mock-auth, BFF, resource-server) + React SPA + Oracle + Kafka + WireMock.
- BFF pattern — browser never holds a JWT; it only holds an `HttpOnly JSESSIONID` cookie.
- Resource server is stateless and validates JWTs from *two* issuers (mock-auth and Google).

---

## Step 2 — Login flow (3 min)

1. Open **http://localhost:5173** in Chrome (incognito recommended).
2. The sign-in page shows **two buttons**:
   - "Sign in (Demo)" — starts PKCE flow against mock-auth at `localhost:9000`
   - "Sign in with Google" — starts Authorization Code flow against Google
3. Click **Sign in (Demo)**. You are redirected to the mock-auth branded login page
   at **http://localhost:9000/login**.
4. Enter `alice` / `password` and submit.
5. You are redirected back to the app.
6. Open **Chrome DevTools → Application → Cookies → localhost:5173**.
   Show the `JSESSIONID` cookie (value is opaque; `HttpOnly` flag set — JavaScript
   cannot read it).  **No JWT appears in Application → Local/Session Storage.**
7. Also show the `XSRF-TOKEN` cookie (readable by JS) used for CSRF protection.

---

## Step 3 — Customer flow (5 min)

1. The accounts page lists Alice's accounts (loaded from the resource server via BFF).
2. Click into an account — see transaction history.
3. Click **New transaction**, fill in:
   - Type: Transfer
   - Amount: 100
   - Counterparty: another of Alice's account IDs
4. Submit — you are redirected back to the account detail page with the new balance.
5. In the terminal running the Kafka consumer:
   ```bat
   kafka-console-consumer.bat --bootstrap-server localhost:9092 ^
     --topic transactions.completed ^
     --from-beginning ^
     --property print.key=true
   ```
   Show the `TRANSFER_OUT` and `TRANSFER_IN` events keyed by `accountId`.
6. **Bonus:** trigger an external transfer with `amount > 10 000` (WireMock returns 503).
   Show the UI displaying the 502 error and the `PAYMENT_PROCESSOR_ERROR` code —
   the upstream 503 message is never leaked to the browser.

---

## Step 4 — Admin flow + 403 boundary test (2 min)

1. Sign out (clears `JSESSIONID`), then sign in as `admin` / `password`.
2. The navigation shows an **Admin** link — open it and see the full user list.
3. Sign out, sign in as `alice`. Open DevTools → Network, then navigate to
   `/api/v1/admin/users` directly. Show the **403 Forbidden** response and
   the `FORBIDDEN` error code in the JSON body.

---

## Step 5 — SAST highlight (2 min)

Open `docs/sast-findings.md`.

Highlight: all secrets (Oracle password, Google client secret, payment processor
API key) are injected via environment variables or `.env` — never committed to Git.
Show the `PaymentProcessorProperties` record and `application.yml` referencing
`${PAYMENT_PROCESSOR_API_KEY}`.

---

## Step 6 — DAST highlight (2 min)

Open `docs/dast-payloads.md`.

Highlight the concurrent-withdrawal probe that validated `@Transactional` +
`SELECT ... FOR UPDATE` prevents the balance from going negative under race
conditions.  Show the 422 `INSUFFICIENT_FUNDS` response captured in the log.