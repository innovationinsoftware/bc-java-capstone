# 04 — Resource Server Security

Day 1 afternoon. The Resource Server's security filter chain is **already
configured for you** — you can see it in
`resource-server/.../config/SecurityConfig.java`. Read it during the
codebase tour. What you implement is the converter that maps a validated
JWT to a local user record, then the integration tests that prove the
whole boundary behaves.

Re-read [`../04-security.md`](../04-security.md) before starting. The
Resource Server is the **stateless** half of the BFF design — no cookies,
no CSRF, no sessions, no CORS. Only Bearer JWTs. The pre-built
`SecurityConfig` reflects that: stateless session policy, CSRF disabled,
two-layer admin gate (URL filter + `@PreAuthorize`).

Spend a few minutes reading the SecurityConfig before starting. The demo
graders will ask you why each line is there.

## Task 4.1 — Implement JwtAuthConverter

**File:** `resource-server/src/main/java/com/example/banking/security/JwtAuthConverter.java`
**Method:** `convert(Jwt jwt)`

This is the single most important piece of security code in the Resource
Server. The Javadoc above the TODO lists the seven steps. Concept first:

When Spring validates a Bearer token, it calls this converter to build the
`Authentication` object that lands in the security context. You will:

1. Read `sub`, `email`, `name`, and the custom `role` claim from the JWT.
2. Look up the local `BANK_USERS` row by `subject`. If absent, create one
   (this is the "first-login auto-provisioning" path).
3. Build the `GrantedAuthority` list from the **stored row's** role — not the
   JWT claim directly. (Why? An admin can be demoted in the database. Trust
   the database, not the token.)
4. Return a `JwtAuthenticationToken` whose principal name is the **local
   userId**, not the JWT subject.

**Critical:** the principal name (`Authentication.getName()`) must be the
local userId. Every service-layer ownership check compares
`account.getOwnerId()` to `authentication.getName()`. If you put the JWT
subject there, all the seeded accounts will appear "not yours" because the
ownership IDs don't match.

**Hints:**

- `jwt.getSubject()` returns the `sub` claim.
- `jwt.getClaimAsString("email")` is null-safe for missing claims; provide
  fallbacks (`subject + "@mock.local"` works).
- `BankUserEntity.newUser(subject, email, name, role)` is the factory.
- Compare the role claim case-insensitively (`equalsIgnoreCase("ADMIN")`).

## Task 4.2 — Smoke test the security boundary

Restart the Resource Server, log in via the SPA as `alice` / `password`,
and confirm:

| Request | Expected |
|---|---|
| `GET http://localhost:8081/health` | **200** |
| `GET http://localhost:8081/api/v1/accounts` (no token, direct to RS) | **401** |
| `GET http://localhost:8080/api/v1/accounts` (via BFF, signed in) | **200**, alice's accounts |
| `GET http://localhost:8080/api/v1/admin/users` (alice = CUSTOMER) | **403** |

If a `CUSTOMER` gets 200 on the admin endpoint, the JWT's `role` claim is
not making it through to your converter — check that mock-auth is issuing
the token (it is) and that you're reading `role` correctly.

If alice's accounts come back empty, your principal name is wrong (you set
the JWT subject instead of the local userId). The accounts exist; they're
just owned by a different `OWNER_ID` than the one you're filtering on.

## Task 4.3 — Integration tests for the security boundary

**File:** `resource-server/src/test/java/com/example/banking/controller/AccountControllerIntegrationTest.java`

The class is already wired with `@SpringBootTest`, `@AutoConfigureMockMvc`,
`@EmbeddedKafka`, and `@MockBean`s for the service layer. Implement these
two `@Test` methods (the others — admin 403, health 200, deposit happy,
internal transfer, processor 503 — are pre-implemented for you to read):

| Test | What it proves |
|---|---|
| `get_accounts_without_token_returns_401` | Default `authenticated()` gate works |
| `customer_hitting_other_users_account_returns_404` | Ownership rule returns 404, not 403 |

**Patterns you will use:**

- A bare `mockMvc.perform(get("/api/v1/accounts"))` simulates an
  unauthenticated request.
- `.with(jwt().jwt(j -> j.subject("...").claim("email","...").claim("role","CUSTOMER")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))`
  simulates a JWT-authenticated request without spinning up the auth server.
- `when(accountService.loadOwned(eq("acc_other"), any())).thenThrow(new ResourceNotFoundException("account", "acc_other"))`
  drives the 404 path through the controller's exception handling.

**Why 404 and not 403 for non-owned resources?**
A 403 confirms that the resource exists. A 404 doesn't. Returning 404
prevents account-ID enumeration. Read the pre-implemented
`customer_hitting_admin_endpoint_returns_403` test for the contrast — admin
endpoints openly reject with 403 because the URL itself is public knowledge.

Run:

```bash
mvn -pl backend/resource-server test -Dtest=AccountControllerIntegrationTest
```

The full suite (your two + the pre-implemented ones) should be green.

## Done when

- [ ] `JwtAuthConverter.convert` returns a token whose principal is the local userId.
- [ ] First-login auto-provisioning works (a new `BANK_USERS` row appears
      after a fresh user signs in for the first time).
- [ ] All four smoke-test rows in 4.2 behave as expected.
- [ ] Both integration tests you wrote are green; the pre-implemented ones
      also pass.

Next: [05-transfers.md](./05-transfers.md).
