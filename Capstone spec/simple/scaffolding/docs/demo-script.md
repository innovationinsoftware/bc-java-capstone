# Demo Script

1. **(2 min) Architecture intro.** We will present the high-level `architecture.md` diagram and explain how the JWT is requested via PKCE and safely held by the React SPA and ultimately verified without a local database request by the Spring Boot backend via JWKS.
2. **(3 min) Login flow.** We will hit the frontend, click "Sign in with Google", grant authorization, and watch the redirect callback occur natively. We will display the JWT securely residing in `sessionStorage` in Chrome Developer Tools.
3. **(5 min) Customer flow.** We will present the Account Listing view. We will drill down into an account, click "New transaction", submit an internal transfer, and see the UI automatically reflect the new total. We will swap to the terminal running the Kafka consumer to verify that real-time events have been emitted and are streaming successfully. 
4. **(2 min) Admin flow.** We will open the application using an admin user's credentials to showcase the successful `Admin Users List` view. Then, to test boundary protection, we'll hit the same endpoint forcefully as a normal user to display the strict `403 Forbidden` response.
5. **(2 min) SAST highlight.** We will open `sast-findings.md` and review how we addressed the Hardcoded Credential vulnerability by injecting properties entirely via env variables.
6. **(2 min) DAST highlight.** We will discuss our bulk transaction probe that validated the `@Transactional` isolation preventing negative balances entirely when concurrent transactions attempt to withdraw more funds than the account balance.
