# Security Decisions

## Authentication (Backend-for-Frontend)
We implemented the Backend-for-Frontend (BFF) security pattern with Google OAuth 2.0 Authorization Code Flow.
- **Frontend Storage:** The React SPA never sees or stores any authentication tokens (no JWTs in LocalStorage or SessionStorage).
- **Backend Validation & Sessions:** Our Spring Boot backend acts as the OAuth2 Client. It exchanges the authorization code for tokens, validates them, and establishes a stateful, secure session with the browser using an HttpOnly JSESSIONID cookie.

## Authorization
- **Role-Based Access Control:** Role mapping is extracted from a specific claim/attribute during the OAuth2 login flow (e.g. inside CustomOidcUserService). Admin users are granted ROLE_ADMIN, and standard users get ROLE_USER.
- **Resource Ownership:** The AccountService and TransactionService utilize the Authentication principal's ID (sub claim) to ensure users can only ever access or transfer money from their *own* associated account records in the database.

## Attack Mitigations
- **Cross-Site Scripting (XSS):** Because we use HttpOnly session cookies, JavaScript cannot access the session identifier. This severely limits the blast radius of any XSS vulnerability.
- **Cross-Site Request Forgery (CSRF):** Since we rely on Cookies for session state, we re-enabled Spring Security's CSRF protection using a CookieCsrfTokenRepository. The backend issues an XSRF-TOKEN cookie (readable by JS), and the React SPA echoes it back in the X-XSRF-TOKEN header on every mutating request (POST, PUT, DELETE).
- **Cross-Origin Resource Sharing (CORS):** The backend only allows the specific SPA_ORIGIN (e.g. http://localhost:5173) and specifically permits llowCredentials(true) so cookies are sent across origins.
- **Injections (SQL):** Spring Data JPA provides automatic parameterized queries to prevent SQL injections.
