# BC Java Capstone — Student Implementation Guide

This document is your step-by-step roadmap through the capstone. Follow the phases
in order. Each phase builds on the previous one.

**Before you start**: complete [1-setup.md](1-setup.md) so Oracle, Kafka, WireMock,
and the mock auth server are all running.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Architecture Recap](#architecture-recap)
- [Phase 0 — Verify Your Environment](#phase-0--verify-your-environment)
- [Phase 1 — Resource Server: Transaction Core Logic](#phase-1--resource-server-transaction-core-logic)
  - [Step 1.1 — Implement DEPOSIT](#step-11--implement-deposit)
  - [Step 1.2 — Implement WITHDRAWAL](#step-12--implement-withdrawal)
  - [Step 1.3 — Write Unit Tests (Day 1 set)](#step-13--write-unit-tests-day-1-set)
  - [Step 1.4 — Integration Test: Deposit happy path](#step-14--integration-test-deposit-happy-path)
- [Phase 2 — Resource Server: Security](#phase-2--resource-server-security)
  - [Step 2.1 — Configure SecurityFilterChain (Resource Server)](#step-21--configure-securityfilterchain-resource-server)
  - [Step 2.2 — Implement JwtAuthConverter](#step-22--implement-jwtauthconverter)
  - [Step 2.3 — Integration Tests: 401, 403, 404](#step-23--integration-tests-401-403-404)
  - [Step 2.4 — Smoke Test: Resource Server Endpoints](#step-24--smoke-test-resource-server-endpoints)
- [Phase 3 — BFF: OAuth2 Login & Token Forwarding](#phase-3--bff-oauth2-login--token-forwarding)
  - [Step 3.1 — Configure SecurityFilterChain (BFF)](#step-31--configure-securityfilterchain-bff)
  - [Step 3.2 — Implement WebClient with OIDC Bearer Filter](#step-32--implement-webclient-with-oidc-bearer-filter)
  - [Step 3.3 — Smoke Test: Full Login Flow](#step-33--smoke-test-full-login-flow)
- [Phase 4 — Resource Server: Transfer Feature](#phase-4--resource-server-transfer-feature)
  - [Step 4.1 — Implement TRANSFER_OUT](#step-41--implement-transfer_out)
  - [Step 4.2 — Write Unit Tests (Day 2 set)](#step-42--write-unit-tests-day-2-set)
  - [Step 4.3 — Integration Tests: Transfer scenarios](#step-43--integration-tests-transfer-scenarios)
  - [Step 4.4 — Smoke Test: WireMock External Transfer](#step-44--smoke-test-wiremock-external-transfer)
- [Phase 5 — Frontend: Connect the UI](#phase-5--frontend-connect-the-ui)
- [Phase 6 — Security Hardening Checklist](#phase-6--security-hardening-checklist)
- [Reference: Key Files](#reference-key-files)
- [Reference: Ports & Credentials](#reference-ports--credentials)

---

## Project Overview

You are building a small banking back-end composed of three Spring Boot modules and a Vite + React front-end:

| Module | Port | Role |
|---|---|---|
| `mock-auth` | 9000 | Spring Authorization Server — issues JWTs for `alice` and `admin` |
| `resource-server` | 8082 | Business logic — accounts, transactions, Kafka events |
| `bff` | 8081 | Backend-for-Frontend — session + CSRF, proxies to resource-server |
| Frontend (Vite) | 5173 | React SPA — talks only to the BFF |
| WireMock | 8089 | Simulates the external Payment Processor |

**The browser never talks directly to the resource server.** It talks to the BFF, which
holds the user's session, attaches the OIDC id_token as a Bearer header, and forwards
the call to the resource server.

---

## Architecture Recap

```
Browser ──(session/CSRF cookie)──► BFF :8081
                                    │
                              (Bearer JWT)
                                    │
                                    ▼
                          Resource Server :8082
                                    │
                          (REST + Idempotency-Key)
                                    │
                                    ▼
                            WireMock :8089
                          (Payment Processor stub)

mock-auth :9000  ◄──(OIDC login redirect)──  BFF
                  ──(id_token JWT)──►         BFF ──► Resource Server
```

Read [2-architecture.md](2-architecture.md) for full detail.

---

## Phase 0 — Verify Your Environment

Before writing any code, confirm all infrastructure is healthy.

```bash
# Oracle — should return rows
cd scripts
./start-oracle.bat      # or run manually

# Kafka
./start-kafka.bat

# WireMock
./start-wiremock.bat

# mock-auth + resource-server + bff
# Start from IntelliJ or: mvn spring-boot:run in each module
```

**Verify with HTTP calls** (use the `.http` file at `http-tests/banking.http` in REST Client):

```http
### Health checks
GET http://localhost:9000/.well-known/openid-configuration
GET http://localhost:8082/health
GET http://localhost:8081/health
GET http://localhost:8089/__admin/mappings
```

All should return 200. If any are red, fix infrastructure before proceeding.

---

## Phase 1 — Resource Server: Transaction Core Logic

### Overview

`TransactionService` is the core of the banking application. It receives a
`NewTransactionRequest` from the controller, validates it, applies balance changes,
persists a `TransactionEntity`, and returns a `TransactionDto`.

The controller (`TransactionController`) then publishes a Kafka event for each row
returned — **after** the database transaction has committed.

### Step 1.1 — Implement DEPOSIT

**File**: `resource-server/src/main/java/com/example/banking/service/TransactionService.java`  
**Method**: `applyDeposit(AccountEntity source, NewTransactionRequest req)`

#### What you need to understand first

Open `AccountEntity`. Notice it has:
- `getBalance()` / `setBalance(BigDecimal)` — mutates the in-memory entity
- `getAccountId()` — the primary key

Open `TransactionEntity`. Notice it is saved via `transactions.save(row)` in `persistRow()`.
You do **not** call `transactions.save()` directly — use `persistRow(...)`.

`AccountRepository` extends `JpaRepository<AccountEntity, String>` so `accounts.save(source)`
persists changes.

#### Implementation steps

1. If `req.counterparty()` is not `null`, throw:
   ```java
   throw new BusinessRuleException("counterparty must be null for DEPOSIT");
   ```

2. Add the deposit amount to the account balance:
   ```java
   source.setBalance(source.getBalance().add(req.amount()));
   ```

3. Persist the updated account:
   ```java
   accounts.save(source);
   ```

4. Create the transaction row using the private helper:
   ```java
   TransactionEntity row = persistRow(
       source.getAccountId(),
       TransactionType.DEPOSIT,
       req.amount(),
       TransactionStatus.COMPLETED,
       null,          // no counterparty
       null,          // no transferGroupId
       req.description()
   );
   ```

5. Return the DTO:
   ```java
   return TransactionDto.from(row);
   ```

#### Verify

Run `TransactionServiceTest` — the `deposit_credits_balance_and_returns_completed_row`
test will fail until you implement it (Phase 1 Step 1.3). You can still compile and run
the app; the test uses `throw new UnsupportedOperationException` as a placeholder.

---

### Step 1.2 — Implement WITHDRAWAL

**File**: Same `TransactionService.java`  
**Method**: `applyWithdrawal(AccountEntity source, NewTransactionRequest req)`

#### Key difference from DEPOSIT

You must check there are enough funds **before** touching the balance. The helper
`requireFunds(source, req.amount())` does this for you — call it early.

#### Implementation steps

1. Reject a counterparty:
   ```java
   if (req.counterparty() != null) {
       throw new BusinessRuleException("counterparty must be null for WITHDRAWAL");
   }
   ```

2. Guard against overdraft:
   ```java
   requireFunds(source, req.amount());
   // If balance < amount, InsufficientFundsException is thrown here.
   // Nothing below runs if the exception is thrown.
   ```

3. Debit the balance:
   ```java
   source.setBalance(source.getBalance().subtract(req.amount()));
   ```

4. Persist, create the row, and return — same pattern as DEPOSIT but with
   `TransactionType.WITHDRAWAL`.

#### Common mistake

Do NOT debit the balance before calling `requireFunds`. The unit test
`withdrawal_below_balance_throws_insufficient_funds_and_does_not_debit`
specifically verifies that the balance is unchanged when an exception is thrown.

---

### Step 1.3 — Write Unit Tests (Day 1 set)

**File**: `resource-server/src/test/java/com/example/banking/service/TransactionServiceTest.java`

The test class already has mocks wired up and a helper `account(id, owner, balance)`.
All `@Test` methods have `TODO` comments describing exactly what to verify.

#### Tests to implement

| Test method | What it verifies |
|---|---|
| `deposit_credits_balance_and_returns_completed_row` | Balance += amount, status = COMPLETED |
| `withdrawal_within_balance_succeeds_and_debits` | Balance -= amount, status = COMPLETED |
| `withdrawal_below_balance_throws_insufficient_funds_and_does_not_debit` | Exception thrown; balance unchanged |
| `submit_against_account_owned_by_another_user_throws_not_found` | Security: 404 for non-owned accounts |

#### Mockito patterns used in this class

```java
// Stub a method return
when(accounts.findById("acc_1")).thenReturn(Optional.of(acct));

// Stub save to echo the argument back (no database here)
when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

// Assert exception
assertThatThrownBy(() -> svc.submit(..., "usr_1"))
    .isInstanceOf(InsufficientFundsException.class);

// Assert BigDecimal equality (ignoring scale — 70.00 == 70.0)
assertThat(acct.getBalance()).isEqualByComparingTo("70.00");
```

#### Run the tests

```bash
# From resource-server directory
mvn test -Dtest=TransactionServiceTest
```

All 4 tests should be green before moving on.

---

### Step 1.4 — Integration Test: Deposit happy path

**File**: `resource-server/src/test/java/com/example/banking/controller/AccountControllerIntegrationTest.java`

**Method**: `deposit_happy_path_returns_201_and_publishes_kafka_event`

This test spins up the full Spring context with an embedded Kafka broker and
`MockMvc`. It mocks `AccountService`, `TransactionService`, and `TransactionEventPublisher`
so no real database is needed.

The `.with(jwt()...)` helper from `spring-security-test` injects a mock Bearer token
into the request. You do not need a real JWT.

#### Implementation guide

```java
@Test
void deposit_happy_path_returns_201_and_publishes_kafka_event() throws Exception {
    // 1. Build a DTO that represents the persisted transaction
    TransactionDto txDto = new TransactionDto(
            "txn_1", "acc_1", "DEPOSIT", new BigDecimal("50.00"),
            "COMPLETED", null, null, "paycheck", LocalDateTime.now());

    // 2. Stub the service (it's a @MockBean, no real DB)
    when(transactionService.submit(any(NewTransactionRequest.class), any()))
            .thenReturn(List.of(txDto));
    when(transactionService.toEvent(any(), any(), eq("USD")))
            .thenReturn(new TransactionEvent("evt_1", "txn_1", "acc_1",
                    "usr_1", "DEPOSIT", new BigDecimal("50.00"),
                    "USD", "COMPLETED", null, null, Instant.now()));

    // 3. Serialise the request body
    String body = mapper.writeValueAsString(
            new NewTransactionRequest("acc_1", "DEPOSIT",
                    new BigDecimal("50.00"), null, "paycheck"));

    // 4. Perform the request with a simulated JWT
    mockMvc.perform(post("/api/v1/transactions")
                   .with(jwt().jwt(j -> j
                       .subject("google-sub-123")
                       .claim("email", "alice@example.com"))
                       .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(body))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$[0].status").value("COMPLETED"))
           .andExpect(jsonPath("$[0].amount").value(50.00));

    // 5. Verify Kafka publish was triggered
    verify(transactionService).publishEvent(any(TransactionEvent.class));
}
```

---

## Phase 2 — Resource Server: Security

### Step 2.1 — Configure SecurityFilterChain (Resource Server)

**File**: `resource-server/src/main/java/com/example/banking/config/SecurityConfig.java`  
**Method**: `securityFilterChain(HttpSecurity http, JwtAuthConverter jwtAuthConverter)`

#### Concept

The resource server is **stateless**. It trusts only Bearer JWTs. It never redirects
browsers — it is only called server-to-server from the BFF.

#### Implementation

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                               JwtAuthConverter jwtAuthConverter) throws Exception {
    http
        // 1. No CORS — server-to-server from BFF only
        .cors(cors -> cors.disable())

        // 2. No CSRF — stateless JWT, no cookies
        .csrf(csrf -> csrf.disable())

        // 3. Never create an HTTP session
        .sessionManagement(s ->
            s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 4. Authorization rules
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/health").permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )

        // 5. JWT validation — delegate to our JwtAuthConverter
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
        );

    return http.build();
}
```

#### Why these choices?

| Setting | Reason |
|---|---|
| `cors.disable()` | No browser ever talks directly to the resource server |
| `csrf.disable()` | No cookies → no CSRF risk |
| `STATELESS` | Tokens arrive on every request; no need to remember sessions |
| `hasRole("ADMIN")` | URL-level guard; `@PreAuthorize` on the controller is a second layer |
| `jwtAuthenticationConverter` | Tells Spring to call our `JwtAuthConverter` to build the `Authentication` object |

---

### Step 2.2 — Implement JwtAuthConverter

**File**: `resource-server/src/main/java/com/example/banking/security/JwtAuthConverter.java`  
**Method**: `convert(Jwt jwt)`

#### Concept

When Spring validates a Bearer JWT, it calls this converter to turn the JWT into
a `Spring Security Authentication` object. We use this as an opportunity to:
1. Map the JWT `sub` claim to a row in `BANK_USERS` (creating it on first login)
2. Extract the `role` claim and set the correct `GrantedAuthority`
3. Set the **local userId** (not the JWT subject) as `Authentication.getName()`

#### Why local userId, not JWT subject?

The `sub` claim from Google looks like `"108245631982736472910"`. Our
`AccountEntity` uses a locally-generated string like `"usr_abc123"` as the owner.
All ownership checks compare `account.getOwnerId()` to `auth.getName()`, so
`getName()` must return the local userId, not the opaque Google sub.

#### Implementation

```java
@Override
public AbstractAuthenticationToken convert(Jwt jwt) {
    String subject = jwt.getSubject();

    // Extract optional claims with fallbacks
    String rawEmail = jwt.getClaimAsString("email");
    String email = rawEmail != null ? rawEmail : subject + "@mock.local";
    String rawName = jwt.getClaimAsString("name");
    String name = rawName != null ? rawName : subject;

    // Determine role (mock-auth sets this; Google tokens default to CUSTOMER)
    String roleClaim = jwt.getClaimAsString("role");
    UserRole role = "ADMIN".equalsIgnoreCase(roleClaim)
            ? UserRole.ADMIN
            : UserRole.CUSTOMER;

    // Upsert: create on first login, find on subsequent logins
    BankUserEntity localUser = users.findBySubject(subject)
            .orElseGet(() -> users.save(
                    BankUserEntity.newUser(subject, email, name, role)));

    // Build authorities from the local row's role (not the JWT claim directly)
    var authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + localUser.getRole().name()));

    // Principal name = local userId — all service ownership checks use this
    return new JwtAuthenticationToken(jwt, authorities, localUser.getUserId());
}
```

#### Verify

Start the resource server. `GET http://localhost:8082/health` should return 200
without a token (public endpoint).

`GET http://localhost:8082/api/v1/accounts` without a token should return **401**.

---

### Step 2.3 — Integration Tests: 401, 403, 404

Implement the remaining test stubs in `AccountControllerIntegrationTest`:

| Test | Verifies |
|---|---|
| `get_accounts_without_token_returns_401` | Security requires a token |
| `customer_hitting_admin_endpoint_returns_403` | URL-level ADMIN guard |
| `customer_hitting_other_users_account_returns_404` | Ownership returns 404, not 403 |
| `health_is_public_and_returns_200` | `permitAll()` on /health works |

Run:
```bash
mvn test -Dtest=AccountControllerIntegrationTest
```

---

### Step 2.4 — Smoke Test: Resource Server Endpoints

With all infrastructure running and the resource server started, log in via the
front-end or use the `banking.http` file to obtain a session cookie and CSRF token,
then:

```http
### Must return 401
GET http://localhost:8082/api/v1/accounts

### After login via BFF — must return your accounts
GET http://localhost:8081/api/v1/accounts
```

---

## Phase 3 — BFF: OAuth2 Login & Token Forwarding

### Step 3.1 — Configure SecurityFilterChain (BFF)

**File**: `bff/src/main/java/com/example/bff/config/SecurityConfig.java`  
**Method**: `securityFilterChain(HttpSecurity http)`

#### Concept

The BFF is the **only** service the browser talks to. It is **stateful** (session-based)
and uses CSRF protection. The browser authenticates once via OAuth2 login. After that,
the BFF session holds the user's tokens and every API call carries a session cookie
and CSRF token.

#### Why is this different from the resource server?

| Concern | Resource Server | BFF |
|---|---|---|
| State | Stateless JWT | Session-based |
| CSRF | Disabled | Cookie-based (`XSRF-TOKEN`) |
| CORS | Disabled | Vite proxy handles it |
| Auth mechanism | Bearer JWT | Session cookie |
| Login | No login endpoint | OAuth2 redirect to mock-auth/Google |

#### Implementation

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // 1. What's public
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
            .requestMatchers("/login/**", "/oauth2/**").permitAll()
            .requestMatchers("/health", "/error").permitAll()
            .anyRequest().authenticated()
        )
        // 2. Return 401 for unauthenticated /api/** — prevents 302 redirect on REST calls
        .exceptionHandling(ex -> ex
            .defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                new AntPathRequestMatcher("/api/**")
            )
        )
        // 3. OAuth2 login — Spring auto-creates /oauth2/authorization/{id}
        //    and /login/oauth2/code/{id} callback endpoints
        .oauth2Login(oauth2 -> oauth2
            .defaultSuccessUrl(frontendBaseUrl + "/", true)
            // CookieOAuth2AuthorizationRequestRepository stores PKCE state in a
            // cookie, not the server session, to avoid state-mismatch errors
            .authorizationEndpoint(authz -> authz
                .authorizationRequestRepository(new CookieOAuth2AuthorizationRequestRepository())
            )
        )
        // 4. Logout
        .logout(logout -> logout
            .logoutSuccessUrl(frontendBaseUrl + "/")
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID")
        )
        // 5. CSRF — cookie-based so the SPA's JS can read the token
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
        )
        // 6. CSRF eager-load filter — forces XSRF-TOKEN cookie to be written on GET requests
        //    Without this, the cookie never appears until the first POST and logout breaks
        .addFilterAfter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                            FilterChain chain) throws ServletException, IOException {
                CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
                if (token != null) {
                    token.getToken(); // triggers cookie write
                }
                chain.doFilter(req, res);
            }
        }, CsrfFilter.class);

    return http.build();
}
```

> **Tip**: The CSRF eager-load filter (step 6) is a known Spring Security 6 quirk.
> Without it, the `XSRF-TOKEN` cookie only appears after the first state-changing
> request, and logout always returns 403.

---

### Step 3.2 — Implement WebClient with OIDC Bearer Filter

**File**: `bff/src/main/java/com/example/bff/config/WebClientConfig.java`  
**Method**: `resourceServerWebClient(...)`

#### Concept

Every BFF controller method calls the resource server via a `WebClient`. Before
sending the request, the WebClient must attach the logged-in user's OIDC id_token
as a `Bearer` header — that's how the resource server knows who the user is.

#### Why id_token, not access_token?

- Google's `access_token` is an opaque token (`ya29.xxx`), not a JWT. The resource
  server's `JwtDecoder` cannot validate it.
- Both Google and mock-auth issue the `id_token` as a signed JWT.
- mock-auth's `TokenCustomizer` adds the custom `"role"` claim to the id_token,
  so role-based access control works correctly.

#### Implementation

```java
@Bean
public WebClient resourceServerWebClient(
        @Value("${bank.resource-server.base-url}") String baseUrl) {

    // Filter function that runs before every outgoing request
    ExchangeFilterFunction oidcBearerFilter = (request, next) -> {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof OAuth2AuthenticationToken oauthToken
                && oauthToken.getPrincipal() instanceof OidcUser oidcUser) {
            // User is logged in via OIDC — attach the id_token as a Bearer header
            String idToken = oidcUser.getIdToken().getTokenValue();
            ClientRequest withBearer = ClientRequest.from(request)
                    .headers(h -> h.setBearerAuth(idToken))
                    .build();
            return next.exchange(withBearer);
        }

        // No OIDC session — pass through without a token
        // (SecurityConfig will reject it with 401)
        return next.exchange(request);
    };

    return WebClient.builder()
            .baseUrl(baseUrl)
            .filter(oidcBearerFilter)
            .build();
}
```

---

### Step 3.3 — Smoke Test: Full Login Flow

Start all three backend modules and the Vite frontend, then:

1. Open `http://localhost:5173` — you should see the login page.
2. Click **Demo Login (alice)**.  
   You are redirected to `http://localhost:9000/login`.  
   Enter `alice` / `alice`.
3. After login you should land on the accounts dashboard.
4. The browser URL should be back at `http://localhost:5173/`.
5. Open the Network tab — requests to `/api/v1/accounts` should return 200 with JSON.

If the login redirect does not work, check:
- `application.yml` in the BFF — `redirect-uri` must match what you registered
  in mock-auth's client configuration.
- `app.frontend-base-url` must be `http://localhost:5173` in the BFF `application.yml`.

---

## Phase 4 — Resource Server: Transfer Feature

### Step 4.1 — Implement TRANSFER_OUT

**File**: `TransactionService.java`  
**Method**: `applyTransferOut(AccountEntity source, NewTransactionRequest req, String callerUserId)`

This is the most complex method. It has two distinct paths: internal and external.

#### Shared validation (both paths)

```java
// 1. Counterparty is required for transfers
if (req.counterparty() == null || req.counterparty().isBlank()) {
    throw new BusinessRuleException("counterparty is required for TRANSFER_OUT");
}

// 2. Source account must have enough funds
requireFunds(source, req.amount());
```

#### Determine if the transfer is internal or external

```java
List<AccountEntity> ownedAccounts = accounts.findByOwnerId(callerUserId);
boolean isInternal = ownedAccounts.stream()
        .anyMatch(a -> a.getAccountId().equals(req.counterparty()));
```

An "internal" transfer moves money between two accounts owned by the **same user**.

#### Internal transfer path

```java
if (isInternal) {
    AccountEntity destination = ownedAccounts.stream()
            .filter(a -> a.getAccountId().equals(req.counterparty()))
            .findFirst()
            .orElseThrow();

    // Generate a shared group ID so the two rows can be linked
    String transferGroupId = "grp_" + UUID.randomUUID();

    // Debit source
    source.setBalance(source.getBalance().subtract(req.amount()));
    accounts.save(source);
    TransactionEntity outRow = persistRow(source.getAccountId(),
            TransactionType.TRANSFER_OUT, req.amount(),
            TransactionStatus.COMPLETED,
            destination.getAccountId(), transferGroupId, req.description());

    // Credit destination
    destination.setBalance(destination.getBalance().add(req.amount()));
    accounts.save(destination);
    TransactionEntity inRow = persistRow(destination.getAccountId(),
            TransactionType.TRANSFER_IN, req.amount(),
            TransactionStatus.COMPLETED,
            source.getAccountId(), transferGroupId, req.description());

    return List.of(TransactionDto.from(outRow), TransactionDto.from(inRow));
}
```

#### External transfer path (via PaymentService + WireMock)

```java
} else {
    String idempotencyKey = UUID.randomUUID().toString();
    try {
        // Calls WireMock (see wiremock-stubs/mappings/)
        paymentService.submitExternalTransfer(
                source.getAccountId(), req.counterparty(),
                req.amount(), "USD", idempotencyKey);

        // Only debit AFTER the payment processor confirms success
        source.setBalance(source.getBalance().subtract(req.amount()));
        accounts.save(source);
        TransactionEntity row = persistRow(source.getAccountId(),
                TransactionType.TRANSFER_OUT, req.amount(),
                TransactionStatus.COMPLETED,
                req.counterparty(), null, req.description());
        return List.of(TransactionDto.from(row));

    } catch (PaymentProcessorException e) {
        // Payment failed — record the attempt but do NOT debit
        TransactionEntity row = persistRow(source.getAccountId(),
                TransactionType.TRANSFER_OUT, req.amount(),
                TransactionStatus.FAILED,
                req.counterparty(), null, req.description());
        throw e; // Re-throw so GlobalExceptionHandler maps it to 502
    }
}
```

> **Critical safety rule**: The balance debit on the external path happens
> **inside the `try` block, after a successful processor call**. If the processor
> throws, the account is never touched. This is a core financial integrity requirement.

---

### Step 4.2 — Write Unit Tests (Day 2 set)

Implement the remaining stubs in `TransactionServiceTest`:

| Test method | What it verifies |
|---|---|
| `internal_transfer_creates_two_rows_with_same_transfer_group_id` | Two rows, linked, correct balances |
| `external_transfer_success_completes_and_debits` | Processor called, balance deducted |
| `external_transfer_failure_persists_failed_row_rethrows_and_does_not_debit` | No debit on failure |

Run:
```bash
mvn test -Dtest=TransactionServiceTest
```

All 7 tests should be green.

---

### Step 4.3 — Integration Tests: Transfer scenarios

Implement the remaining stubs in `AccountControllerIntegrationTest`:

| Test method | What it verifies |
|---|---|
| `internal_transfer_returns_201_with_two_transaction_rows` | Two-row response with linked groupId |
| `external_transfer_processor_503_returns_502` | PaymentProcessorException → HTTP 502 |

Run:
```bash
mvn test -Dtest=AccountControllerIntegrationTest
```

All 7 integration tests should be green.

---

### Step 4.4 — Smoke Test: WireMock External Transfer

WireMock is configured to return **200** for transfers to `EXT-ACCT-001` and
**503** for transfers to `EXT-ACCT-FAIL`.

Using the frontend or the `.http` file:

```http
### Should return 201 COMPLETED
POST http://localhost:8081/api/v1/transactions
Content-Type: application/json
X-XSRF-TOKEN: {{csrfToken}}

{
  "accountId": "{{yourAccountId}}",
  "type": "TRANSFER_OUT",
  "amount": 10.00,
  "counterparty": "EXT-ACCT-001",
  "description": "WireMock happy path"
}

### Should return 502 PAYMENT_PROCESSOR_ERROR
POST http://localhost:8081/api/v1/transactions
Content-Type: application/json
X-XSRF-TOKEN: {{csrfToken}}

{
  "accountId": "{{yourAccountId}}",
  "type": "TRANSFER_OUT",
  "amount": 10.00,
  "counterparty": "EXT-ACCT-FAIL",
  "description": "WireMock failure path"
}
```

Check the WireMock admin to verify the calls were received:
```http
GET http://localhost:8089/__admin/requests
```

---

## Phase 5 — Frontend: Connect the UI

The React frontend (`frontend/src/`) has its structure in place — routing, components,
and CSS are all provided. Your job is to implement the data-fetching layer and the
page components that use it.

**Start the frontend**:
```bash
cd frontend
npm install
npm run dev
```

Navigate to `http://localhost:5173`. The app will not work yet — `apiFetch` throws
immediately. Work through the steps below in order.

---

### Step 5.1 — Implement the API Client

**File**: `frontend/src/api/apiClient.js`

This file is the entire HTTP surface of the frontend. Every API call goes through
`apiFetch`. It handles CSRF tokens, Content-Type, and 401 errors consistently so
page components never have to think about them.

#### Part A — `readCsrfToken()`

Spring Security writes an `XSRF-TOKEN` cookie that JavaScript can read (it is **not**
`HttpOnly`). The browser sends cookies automatically on same-origin fetches, but
you must **also** send the value as an `X-XSRF-TOKEN` request header on mutations
(POST, PUT, DELETE, PATCH). This is the Spring double-submit CSRF pattern.

```js
function readCsrfToken() {
  return document.cookie
    .split("; ")
    .find((row) => row.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
}
```

#### Part B — `apiFetch(path, init)`

```js
export async function apiFetch(path, init = {}) {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers ?? {});

  // Default Content-Type for bodies
  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json");
  }

  // Attach CSRF token for mutating requests
  if (method !== "GET" && method !== "HEAD") {
    const csrf = readCsrfToken();
    if (csrf) headers.set("X-XSRF-TOKEN", csrf);
  }

  const res = await fetch(path, {
    ...init,
    headers,
    credentials: "same-origin",   // always send cookies
  });

  if (res.status === 401) {
    throw new ApiError(401, { detail: "Unauthorized" });
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body);
  }

  return res.status === 204 ? undefined : res.json();
}
```

#### Why throw on 401 instead of redirecting?

The original naïve approach was: *on 401, `window.location = "/"`.* That caused an
infinite loop — the page would load, call `/api/v1/users/me`, get 401, redirect to `/`,
repeat forever. Throwing `ApiError(401)` lets the `useMe` hook catch it and set
`user = null`, which makes `AppLayout` render the sign-in page — no redirect needed.

#### Verify

After implementing both parts, reload `http://localhost:5173`. The sign-in page should
appear (because `useMe` is still a stub). Move on to Step 5.2.

---

### Step 5.2 — Implement `useMe`

**File**: `frontend/src/hooks/useMe.js`

```js
useEffect(() => {
  apiFetch("/api/v1/users/me")
    .then(setUser)
    .catch(() => setUser(null))
    .finally(() => setLoading(false));
}, []);
```

The `[]` dependency array means this runs once on mount. `AppLayout` calls `useMe`
and shows a loading spinner until `loading` becomes `false`, then either shows the
sign-in page (if `user === null`) or the authenticated layout.

#### Verify

Reload — you should see the sign-in page. Click **Sign in (Demo)**, log in as
`alice` / `alice`. After login you should land on the accounts dashboard. The accounts
list will be blank because `AccountsPage` is still a stub.

---

### Step 5.3 — Implement `AccountsPage`

**File**: `frontend/src/routes/AccountsPage.jsx`

```jsx
useEffect(() => {
  listAccounts()
    .then(setAccounts)
    .catch((e) => setError(e.message));
}, []);

if (error) return <p className="error">Could not load accounts: {error}</p>;
if (!accounts) return <p>Loading accounts…</p>;
if (accounts.length === 0) return <p>You have no accounts yet.</p>;

return (
  <section>
    <h1>Your accounts</h1>
    <ul className="accounts">
      {accounts.map((a) => (
        <AccountCard key={a.accountId} account={a} />
      ))}
    </ul>
  </section>
);
```

#### Three states the rubric grades

| State | When | What to render |
|---|---|---|
| **Loading** | `accounts === null` (no error) | `<p>Loading accounts…</p>` |
| **Error** | `error !== null` | `<p className="error">Could not load accounts: {error}</p>` |
| **Empty** | `accounts.length === 0` | `<p>You have no accounts yet.</p>` |

#### Verify

After login, your account list should appear. Click an account — it will show a blank
page until Step 5.4.

---

### Step 5.4 — Implement `AccountDetailPage`

**File**: `frontend/src/routes/AccountDetailPage.jsx`

```jsx
useEffect(() => {
  Promise.all([getAccount(accountId), getTransactions(accountId)])
    .then(([a, t]) => { setAccount(a); setTransactions(t); })
    .catch((e) => setError(e.message));
}, [accountId]);

if (error) return <p className="error">{error}</p>;
if (!account || !transactions) return <p>Loading…</p>;

return (
  <section>
    <h1>{account.accountType} — {account.currency} {account.balance}</h1>
    <p>
      <Link to="/transactions/new">New transaction</Link>
    </p>
    <h2>Transactions</h2>
    <TransactionList transactions={transactions} />
  </section>
);
```

#### Why `Promise.all`?

Without it, you would need two sequential `useEffect` / `await` pairs, doubling the
round-trip time. `Promise.all` fires both requests simultaneously and only commits to
state when both complete — or errors immediately if either fails.

#### Verify

Clicking an account should show its balance and transaction table.

---

### Step 5.5 — Implement `NewTransactionPage`

**File**: `frontend/src/routes/NewTransactionPage.jsx`

```jsx
async function handleSubmit(formData) {
  setError(null);
  setSubmitting(true);
  try {
    await submitTransaction(formData);
    navigate(`/accounts/${formData.accountId}`);
  } catch (e) {
    setError(e.message);
  } finally {
    setSubmitting(false);
  }
}

if (!accounts.length) return <p>Loading accounts…</p>;

return (
  <TransactionForm
    accounts={accounts}
    onSubmit={handleSubmit}
    submitting={submitting}
    error={error}
  />
);
```

#### Design notes

- The loading guard (`if (!accounts.length)`) prevents `TransactionForm` from
  mounting with an empty `accounts` array. Without it, `accounts[0]?.accountId`
  evaluates to `undefined`, and the first POST would send `accountId: ""`.
- `submitTransaction` calls `apiFetch` which automatically adds the CSRF header.
  You do not need to do anything special here.
- On success, `navigate(...)` moves the user to the account detail page where
  the new transaction row appears.

#### Verify

Submit a deposit — you should be redirected to the account detail page with the
new transaction row visible.

---

### Step 5.6 — Implement Frontend Tests (`AccountCard.test.jsx`)

**File**: `frontend/src/components/AccountCard.test.jsx`

```jsx
it("renders account type", () => {
  render(
    <MemoryRouter>
      <AccountCard account={mockAccount} />
    </MemoryRouter>
  );
  expect(screen.getByText("CHECKING")).toBeInTheDocument();
});

it("renders currency and balance", () => {
  render(
    <MemoryRouter>
      <AccountCard account={mockAccount} />
    </MemoryRouter>
  );
  expect(screen.getByText(/USD 1500.00/)).toBeInTheDocument();
});

it("renders a link to the account detail page", () => {
  render(
    <MemoryRouter>
      <AccountCard account={mockAccount} />
    </MemoryRouter>
  );
  expect(screen.getByRole("link")).toHaveAttribute("href", "/accounts/acc_1");
});
```

#### Why `MemoryRouter`?

`AccountCard` renders a `<Link>` from React Router. Without a router context, React
Router throws. `MemoryRouter` provides that context in tests without touching the
browser's actual URL.

#### Run the tests

```bash
cd frontend
npm test
```

All 3 tests should be green.

---

## Phase 6 — Security Hardening Checklist

Before submitting your capstone, verify every item below. See
[4-security-decisions.md](4-security-decisions.md) for rationale.

| # | Check | How to verify |
|---|---|---|
| 1 | No CSRF token on GET requests — `GET /api/v1/accounts` works without `X-XSRF-TOKEN` | `curl http://localhost:8081/api/v1/accounts` (with session) |
| 2 | POST without CSRF token returns **403** | Remove `X-XSRF-TOKEN` from a POST |
| 3 | Unauthenticated request to `/api/v1/accounts` returns **401** (not 302) | `curl http://localhost:8081/api/v1/accounts` |
| 4 | Customer JWT cannot access `/api/v1/admin/users` — returns **403** | Log in as alice, try the admin endpoint |
| 5 | Alice cannot see Bob's accounts — returns **404** | Use alice's session to request a known Bob account ID |
| 6 | Withdrawal exceeding balance returns **422** with `INSUFFICIENT_FUNDS` | POST WITHDRAWAL for more than the balance |
| 7 | Payment processor `apiKey` is never logged | Review PaymentService — only `e.getClass().getSimpleName()` is logged |
| 8 | TRANSFER_OUT to failed processor returns **502** | Use `EXT-ACCT-FAIL` counterparty |
| 9 | Idempotency key is unique per external transfer attempt | Review `applyTransferOut` — uses `UUID.randomUUID()` |
| 10 | All SQL uses parameterised queries — no string concatenation | Review repositories — all use Spring Data `@Query` or derived queries |

---

## Reference: Key Files

| File | Purpose |
|---|---|
| `resource-server/.../service/TransactionService.java` | **You implement**: deposit, withdrawal, transfer |
| `resource-server/.../security/JwtAuthConverter.java` | **You implement**: JWT → local user mapping |
| `resource-server/.../config/SecurityConfig.java` | **You implement**: stateless JWT filter chain |
| `bff/.../config/SecurityConfig.java` | **You implement**: session + CSRF + OAuth2 login chain |
| `bff/.../config/WebClientConfig.java` | **You implement**: OIDC Bearer filter |
| `resource-server/.../service/TransactionServiceTest.java` | **You write**: 7 unit tests |
| `resource-server/.../controller/AccountControllerIntegrationTest.java` | **You write**: 7 integration tests |
| `docs/2-architecture.md` | System architecture with sequence diagrams |
| `docs/4-security-decisions.md` | Security ADRs — explains every security choice |
| `http-tests/banking.http` | REST Client test file for manual smoke testing |
| `wiremock-stubs/mappings/` | WireMock stub definitions for the payment processor |

---

## Reference: Ports & Credentials

| Service | URL | Notes |
|---|---|---|
| mock-auth | `http://localhost:9000` | OIDC issuer |
| resource-server | `http://localhost:8082` | JWT-protected REST API |
| BFF | `http://localhost:8081` | Session-based proxy |
| Frontend (Vite) | `http://localhost:5173` | React SPA |
| WireMock | `http://localhost:8089` | Payment processor stub |
| Oracle (standalone) | `localhost:1522` | Service `XEPDB1` |
| Oracle (Docker) | `localhost:1521` | Service `XEPDB1` |
| Kafka | `localhost:9092` | Bootstrap server |

| Demo user | Password | Role |
|---|---|---|
| `alice` | `alice` | CUSTOMER |
| `admin` | `admin` | ADMIN |

| Database | Username | Password |
|---|---|---|
| `XEPDB1` | `bankapp` | `bankapp_password` |
