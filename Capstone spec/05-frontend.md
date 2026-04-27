# 05 — React Frontend

Because the capstone uses the **BFF pattern** (see [Security](./04-security.md)), the frontend is dramatically simpler than a pure-SPA build. There is no OAuth library, no `AuthProvider`, no `RequireAuth`/`RequireRole`, no `CallbackPage`. Spring Security gates the API at the network layer; the SPA just makes calls and reacts to 401s.

## Stack

- **Vite** + **React 18** + JavaScript or TypeScript.
- **react-router-dom v6** — `BrowserRouter`, `Routes`, `Route`, `Link`, `NavLink`, `Outlet`, `useParams`, `useNavigate`. All covered in Module 9.
- **HTTP** — `fetch` with `credentials: 'same-origin'`. No `axios` needed; no auth library.
- **Styling** — plain CSS or CSS modules.

## Routes

| Path | Layout | Auth | Component | Purpose |
|---|---|---|---|---|
| `/` | `AppLayout` | session | `AccountsPage` | List the user's accounts |
| `/accounts/:accountId` | `AppLayout` | session, ownership | `AccountDetailPage` | One account + its transactions |
| `/transactions/new` | `AppLayout` | session | `NewTransactionPage` | Form to submit a transaction |
| `/admin/users` | `AppLayout` | session, role ADMIN | `AdminUsersPage` | List users (visible only to admins) |
| `*` | `AppLayout` | n/a | `NotFoundPage` | 404 |

Notice what's gone:

- No `/login` — login is `<a href="/oauth2/authorization/mock-auth">Sign in</a>`. That URL is a Spring endpoint on the BFF, not a React route.
- No `/callback` — Spring handles the OAuth callback at `/login/oauth2/code/mock-auth`. The browser never lands on a React route during the auth flow.

## Same-origin via Vite proxy (dev)

```js
// frontend/vite.config.js
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api':    { target: 'http://localhost:8080', changeOrigin: true },
      '/login':  { target: 'http://localhost:8080', changeOrigin: true },
      '/logout': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
```

From the browser's point of view, everything is on `localhost:5173`. The session cookie set by the BFF is `localhost:5173`-scoped. CORS does not apply.

For production: `npm run build` produces `dist/`. Copy it into `backend/bff/src/main/resources/static/` and the BFF serves it directly.

## Component tree

```
src/
├── main.jsx                     // Vite entry, BrowserRouter
├── App.jsx                      // Routes — flat, no auth providers
├── routes/
│   ├── AppLayout.jsx            // header (with sign in/out) + nav + <Outlet/>
│   ├── AccountsPage.jsx
│   ├── AccountDetailPage.jsx
│   ├── NewTransactionPage.jsx
│   ├── AdminUsersPage.jsx
│   └── NotFoundPage.jsx
├── components/
│   ├── AccountCard.jsx
│   ├── TransactionList.jsx
│   ├── TransactionForm.jsx
│   └── ErrorBanner.jsx
├── api/
│   ├── apiClient.js             // fetch wrapper, CSRF, 401 handling
│   ├── accounts.js
│   ├── transactions.js
│   └── users.js
└── hooks/
    └── useMe.js                 // returns { user, loading, error }; null user = signed out
```

Compared to the pure-SPA design, gone: `auth/AuthProvider.jsx`, `auth/RequireAuth.jsx`, `auth/RequireRole.jsx`, `routes/LoginPage.jsx`, `routes/CallbackPage.jsx`.

## The API client

One module, one place that knows about cookies and CSRF:

```js
// api/apiClient.js
const LOGIN_URL = "/oauth2/authorization/mock-auth";

function readCsrfToken() {
  return document.cookie
    .split('; ')
    .find(row => row.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.detail || body?.title || `HTTP ${status}`);
    this.status = status;
    this.body = body;
  }
}

export async function apiFetch(path, init = {}) {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers ?? {});
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json');
  }
  if (method !== 'GET' && method !== 'HEAD') {
    const csrf = readCsrfToken();
    if (csrf) headers.set('X-XSRF-TOKEN', csrf);
  }

  const res = await fetch(path, {
    ...init,
    headers,
    credentials: 'same-origin',
  });

  if (res.status === 401) {
    // No session — bounce to login. Spring will redirect back here after auth.
    window.location.assign(LOGIN_URL);
    throw new ApiError(401, { detail: 'Unauthorized' });
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body);
  }
  return res.status === 204 ? undefined : res.json();
}
```

That's the entire auth surface in JavaScript. No tokens, no localStorage, no oidc-client-ts.

Per-resource modules wrap `apiFetch`:

```js
// api/accounts.js
export const listAccounts = () => apiFetch('/api/v1/accounts');
export const getAccount = (id) => apiFetch(`/api/v1/accounts/${id}`);
export const getTransactions = (id) => apiFetch(`/api/v1/accounts/${id}/transactions`);
```

```js
// api/transactions.js
export const submitTransaction = (payload) =>
  apiFetch('/api/v1/transactions', { method: 'POST', body: JSON.stringify(payload) });
```

## Knowing who's signed in

The SPA renders the same `AppLayout` whether you're signed in or not. To show "Sign in" vs "Sign out (alice)" in the header, call `/api/v1/users/me`:

- Returns the user object → render the user menu.
- Returns 401 → the `apiFetch` 401 handler bounces to login. So in practice you only see this after the user has signed out from another tab.

For the very first render before any API call has happened, just render the user menu in a "loading" state and let the first `useMe()` call settle it.

```js
// hooks/useMe.js
import { useEffect, useState } from 'react';
import { apiFetch } from '../api/apiClient.js';

export function useMe() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiFetch('/api/v1/users/me')
      .then(setUser)
      .catch(() => setUser(null))   // 401 has already bounced to login; this catches network errors
      .finally(() => setLoading(false));
  }, []);

  return { user, loading };
}
```

## Header: sign in / sign out

```jsx
function HeaderUserMenu() {
  const { user, loading } = useMe();

  if (loading) return <span>…</span>;
  if (!user) {
    return <a href="/oauth2/authorization/mock-auth">Sign in</a>;
  }
  return (
    <form method="POST" action="/logout">
      <input type="hidden" name="_csrf" value={readCsrfToken()} />
      <span>{user.email}</span>
      <button type="submit">Sign out</button>
    </form>
  );
}
```

That's it. No `signinRedirect()`, no `signoutRedirect()`, no auth context. The browser navigates to the BFF; Spring does the rest.

## UX expectations (Meets criteria)

For each fetch:

- **Loading** — spinner or skeleton.
- **Empty** — "No accounts yet" / "No transactions yet" when the response is `[]`.
- **Error** — banner with a useful message. Don't show the raw error JSON.
- **Disabled-during-submit** — `<button disabled={submitting}>Submit</button>` on `TransactionForm`.

For `TransactionForm`:

- Client-side validation matches the backend's Bean Validation rules.
- After a successful submit, navigate back to the source account detail page (`useNavigate`).
- Show the server's error envelope's `detail` field if the backend rejects (e.g., `INSUFFICIENT_FUNDS`).

## Admin views

Build an `AdminUsersPage` component as a plain table. Two-layer gating:

- **Server side (security):** `/api/v1/admin/users` requires `ROLE_ADMIN` on the Resource Server, plus the BFF proxies it (so the BFF *also* requires the user to be authenticated). A non-admin gets 403; the SPA renders an error.
- **Client side (UX):** the `/admin/users` link in the nav is hidden if `useMe().user.role !== 'ADMIN'`. This is purely UX — if a non-admin types the URL directly, the API still returns 403.

Do not rely on client-side gating for security. The rubric grades both layers.

## Common React pitfalls (graded)

The Module 9 slides explicitly listed these:

- **Don't use `<a href="...">` for in-app links.** Use `<Link to="...">` from react-router. Plain anchors trigger a full page reload. (Exception: the **sign-in link** must be `<a>` because it leaves the SPA entirely.)
- **Don't `navigate(...)` during render.** Only inside event handlers or `useEffect`.
- **Don't drop the `*` route.** Bad URLs render blank otherwise.
- **Treat route params as strings.** Convert with `Number(id)` only after checking presence.

Next: [Kafka Events](./06-kafka-events.md).
