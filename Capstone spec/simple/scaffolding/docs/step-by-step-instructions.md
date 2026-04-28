# Bootcamp Capstone: Step-by-Step Instructions

This guide breaks down the completion of the scaffolding into a logical workflow that mirrors your 3-Day Plan. Use this alongside the main spec documents.

## Day 1 - Get the data flowing
*Theme: Backend reads/writes Oracle, SPA shows accounts. No real auth yet.*

**Morning: Database & Entities**
- **Entities:** Open `backend/src/main/java/com/example/banking/model/`. Finish mapping `AccountEntity.java` to its table (`BANK_ACCOUNTS`) and map any other entities required. Complete the columns based on the database schema.
- **Repositories:** Create or update Spring Data JPA repositories in `backend/src/main/java/com/example/banking/repository/` for your entities.

**Afternoon: Read APIs & Basic Transactions**
- **Account APIs:** 
  - Open `backend/src/main/java/com/example/banking/service/AccountService.java`. Replace the `return null;` placeholders to correctly fetch accounts by owner and enforce ownership checks using `orElseThrow(new ResourceNotFoundException(...))`.
  - Open `backend/src/main/java/com/example/banking/controller/AccountController.java`. Replace the `Collections.emptyList()` and `return null;` stubs by calling the appropriate methods on the `AccountService` and `TransactionService`.
- **Deposit & Withdrawal:** 
  - Open `backend/src/main/java/com/example/banking/service/TransactionService.java` and locate the `submit()` method. Implement the logic to handle `DEPOSIT` and `WITHDRAWAL` transaction types. Note: You must check for sufficient funds for withdrawals, update the account balance, and save the transaction row to the database.
  - Open `backend/src/main/java/com/example/banking/controller/TransactionController.java` and implement the logic inside the `create()` method. Once you have populated the `created` transaction list from the service, uncomment the `Location` header code at the bottom of the method to return a standard `201` response.

## Day 2 - Lock it down
*Theme: Real Google login, ownership and roles enforced, secrets out of the repo.*

**Morning: Security & Authentication (BFF Pattern)**
- **Google OAuth:** Set up a Google OAuth 2.0 Web Application client in your Google Cloud Console. Update your `backend/.env` file with the Client ID and Client Secret. Ensure your redirect URI targets the backend BFF endpoint.
- **Backend Security:** 
  - Open `backend/src/main/java/com/example/banking/config/SecurityConfig.java`. We've scaffolded a stateful `SecurityFilterChain` acting as an OAuth2 Client. Replace the `.anyRequest().permitAll()` placeholder with the proper HTTP authorization rules (e.g., `ROLE_ADMIN` required for `/api/v1/admin/**`).
  - Configure the OAuth2 Login and ensure `CustomOidcUserService` correctly maps roles for your users.
  - Implement CSRF protection. Since the BFF assigns a session cookie, add a `CookieCsrfTokenRepository` to securely relay tokens between the API and the React SPA.
  - **Authorization:** Ensure your endpoints are properly secured. Ownership should be validated in the service layer against the user ID extracted from the `Authentication` principal.
- **Frontend Auth Integration:**
  - **API Client:** Open `frontend/src/api/apiClient.js`. Configure Axios to send `withCredentials: true` so the browser attaches the `JSESSIONID` cookie automatically. Use an interceptor to attach the `X-XSRF-TOKEN` header reading from your browser's cookies.
  - **Protected Routes:** Open `frontend/src/auth/RequireAuth.jsx`. Swap out the stub logic to actually ping the `/api/v1/users/me` endpoint to verify if the user has an active session cookie, and redirect to `/oauth2/authorization/google` if unauthenticated.

**Afternoon: Advanced Transactions & Events**
- **Internal Transfers:** Update `backend/src/main/java/com/example/banking/service/TransactionService.java` to handle `TRANSFER_OUT` requests. If it's an internal transfer (transferring between two accounts owned by the same user), you must save TWO transaction rows (one for the debit, one for the credit) in a single `@Transactional` method.
- **External Transfers:** If it's a transfer to an external system, use the `PaymentService` to make a secure downstream REST call to the Payment Processor (you can use WireMock to stub this during development). Handle successes and failures (e.g. `PaymentProcessorException`).
- **Kafka Event Publishing:** 
  - **Producer Configuration:** Verify your Spring Kafka configuration in `backend/src/main/resources/application.yml`.
  - **Publishing:** Update `backend/src/main/java/com/example/banking/controller/TransactionController.java` inside the `create()` method. Iterate over the saved transactions, generate a `TransactionEvent`, and publish it to the Kafka broker. *Make sure this happens in the controller to ensure the database transaction has successfully committed before the event is sent.*

## Day 3 - Prove it and demo it
*Theme: Tests, scans, polish, demo.*

**Morning: Testing & Verification**
- **Unit Tests:** Write robust unit tests for your domain logic, especially inside `backend/src/test/java/com/example/banking/service/TransactionServiceTest.java`.
- **Integration Tests:** Write `@SpringBootTest` integration tests in `backend/src/test/java/` to verify authentication rules, HTTP status codes, and database persistence.
- **SAST:** Run SAST (e.g. Checkmarx) scans against your codebase. Document your findings in `docs/sast-findings.md` as required by the rubric.

**Afternoon: Dynamic Scanning & Polish**
- **DAST:** Run DAST baseline scans and test your API against specific vulnerabilities. Document your custom payloads in `docs/dast-payloads.md`.
- **Final Polish:** Sweep for any left-over TODOs, finish any README instructions for the backend/frontend servers, and prepare the demo flow.




