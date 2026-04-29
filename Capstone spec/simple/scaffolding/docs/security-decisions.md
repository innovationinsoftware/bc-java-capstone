# Security Decisions

## Authentication
> [!NOTE] 
> Detail your authentication approach. How did you implement the Backend-for-Frontend (BFF) pattern with Google OAuth 2.0? Where are tokens validated and how are stateful sessions maintained? Why are tokens kept out of the React SPA?

## Authorization
> [!NOTE] 
> How are you mapping roles inside your OIDC Custom User Service? How do you ensure users can only access their own accounts? What happens when a user attempts to fetch someone else's resource?

## Attack Mitigations
> [!NOTE] 
> Describe the common attacks (CSRF, XSS, SQLi, CORS misconfigurations) you mitigated. Given you are using session cookies, explain specifically how you configured Spring Security to mitigate CSRF attacks (e.g. CookieCsrfTokenRepository).
