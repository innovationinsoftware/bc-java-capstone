# 05 — React Frontend

## Stack

- **Vite** + **React 18** + JavaScript or TypeScript (TS recommended; the slides demo it).
- **react-router-dom v6** — `BrowserRouter`, `Routes`, `Route`, `Link`, `NavLink`, `Outlet`, `useParams`, `useNavigate`, `useLocation`. All covered in Module 9.
- **OIDC client** — `oidc-client-ts` with `react-oidc-context`, or a similar maintained library. **Do not implement PKCE yourself.**
- **HTTP** — `fetch` is fine; `axios` is fine. Pick one and use it consistently.
- **Styling** — plain CSS or CSS modules. Don't pull in a UI library you've never used; you don't have time.

## Routes

| Path | Layout | Auth | Component | Purpose |
|---|---|---|---|---|
| `/login` | none | public | `LoginPage` | "Sign in with Google" button |
| `/callback` | none | public | `CallbackPage` | Handles the OAuth redirect, exchanges code for token, redirects to `/` |
| `/` | `AppLayout` | authenticated | `AccountsPage` | List the user's accounts |
| `/accounts/:accountId` | `AppLayout` | authenticated, ownership | `AccountDetailPage` | One account + its transactions |
| `/transactions/new` | `AppLayout` | authenticated | `NewTransactionPage` | Form to submit a transaction |
| `/admin/users` | `AppLayout` | role `ADMIN` | `AdminUsersPage` | List users (visible only to admins) |
| `*` | none | n/a | `NotFoundPage` | 404 |

This is exactly the layout-route pattern from Module 9 slides 17–18: `<Route element={<AppLayout/>}>` wraps the authenticated pages, login/callback live outside it.

## Components

The component tree your scaffold expects (rough):

```
src/
├── main.tsx                     // Vite entry
├── App.tsx                      // <BrowserRouter><AuthProvider>...</AuthProvider></BrowserRouter>
├── auth/
│   ├── AuthProvider.tsx         // wraps react-oidc-context
│   ├── RequireAuth.tsx          // redirects to /login if not authenticated
│   └── RequireRole.tsx          // redirects to / if role mismatch
├── routes/
│   ├── AppLayout.tsx            // header (with logout) + nav + <Outlet/>
│   ├── LoginPage.tsx
│   ├── CallbackPage.tsx
│   ├── AccountsPage.tsx
│   ├── AccountDetailPage.tsx
│   ├── NewTransactionPage.tsx
│   ├── AdminUsersPage.tsx
│   └── NotFoundPage.tsx
├── components/
│   ├── AccountCard.tsx
│   ├── TransactionList.tsx
│   ├── TransactionForm.tsx
│   └── ErrorBanner.tsx
├── api/
│   ├── apiClient.ts             // fetch wrapper, attaches Bearer token
│   ├── accounts.ts              // listAccounts(), getAccount(id), getTransactions(id)
│   ├── transactions.ts          // submitTransaction(payload)
│   └── users.ts                 // getMe(), listUsers() (admin)
└── hooks/
    ├── useApi.ts                // small wrapper that returns {data, loading, error}
    └── useAuthUser.ts           // returns the current user profile + role
```

## Auth integration

Wrap the app in your OIDC provider once:

```tsx
// auth/AuthProvider.tsx
const oidcConfig: AuthProviderProps = {
  authority: "https://accounts.google.com",
  client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID,
  redirect_uri: "http://localhost:5173/callback",
  response_type: "code",
  scope: "openid email profile",
  // PKCE is on by default in oidc-client-ts
};

export function AuthProvider({ children }: { children: ReactNode }) {
  return <OidcAuthProvider {...oidcConfig}>{children}</OidcAuthProvider>;
}
```

Use a `RequireAuth` component to guard the layout route:

```tsx
function RequireAuth({ children }: { children: ReactElement }) {
  const auth = useAuth();
  const location = useLocation();
  if (auth.isLoading) return <Spinner />;
  if (!auth.isAuthenticated) return <Navigate to="/login" state={{ from: location }} replace />;
  return children;
}
```

`RequireRole` is similar but checks `useAuthUser().role`.

## Calling the API

Centralise the auth header in **one** place:

```ts
// api/apiClient.ts
import { getAccessToken } from "../auth/tokens";

const BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getAccessToken();
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Authorization: token ? `Bearer ${token}` : "",
      ...(init.headers ?? {}),
    },
  });
  if (res.status === 401) {
    redirectToLogin();          // clear stale token, go to /login
    throw new Error("Unauthorized");
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    throw new ApiError(res.status, problem);
  }
  return (res.status === 204 ? undefined : await res.json()) as T;
}
```

Then per-resource modules call `apiFetch`:

```ts
// api/accounts.ts
export const listAccounts = () => apiFetch<Account[]>("/api/v1/accounts");
export const getAccount = (id: string) => apiFetch<Account>(`/api/v1/accounts/${id}`);
export const getTransactions = (id: string) =>
  apiFetch<Transaction[]>(`/api/v1/accounts/${id}/transactions`);
```

Components call those, never `fetch` directly. The rubric grades extracting calls into reusable hooks/services.

## UX expectations (Meets criteria)

For each fetch, the UI must show:

- **Loading state** — spinner or skeleton while the request is in flight.
- **Empty state** — "No accounts yet" / "No transactions yet" when the response is `[]`.
- **Error state** — a banner with a useful message if `apiFetch` throws. Don't show the raw error JSON.
- **Disabled-during-submit** — `<button disabled={submitting}>Submit</button>` on `TransactionForm`. Otherwise users double-click and create duplicate transactions.

For `TransactionForm`:

- Client-side validation **must match** the backend Bean Validation rules in [API Contract](./03-api-contract.md). Don't allow `amount` to be negative or zero in the UI.
- After a successful submit, navigate back to the source account detail page (use `useNavigate`).
- Show the server's error envelope's `detail` field if the backend rejects the submission (e.g., insufficient funds).

## Admin views

Build them, but they don't need to be polished. A plain `<table>` of users with `userId`, `email`, `displayName`, `role` is enough. The point is to demonstrate that:

- The route is gated on role at the **client side** (better UX — no flash of admin content).
- The API is gated on role at the **server side** (security — a non-admin who hits the URL directly gets 403).

If you only do client-side gating, it counts as failing the security rubric — UI gates are not security.

## Logout

Provide a "Sign out" button in the header. It should:

- Clear `sessionStorage`.
- Call `auth.signoutRedirect()` (oidc-client-ts) to redirect to Google's logout, or just navigate to `/login`. Either is acceptable for the capstone.

## What about token refresh?

Google access tokens last 1 hour. The capstone's expected behaviour on expiry is **redirect to login**, not silent refresh. The "Exceeds" rubric line about refresh-token rotation is achievable if you have time, but not required for "Meets."

## Common React pitfalls (graded)

The Module 9 slides explicitly listed these — and the rubric watches for them:

- **Don't use `<a href="...">` for in-app links.** Use `<Link to="...">` from react-router. Plain anchors trigger a full page reload and lose state.
- **Don't `navigate(...)` during render.** Only inside event handlers or `useEffect`.
- **Don't drop the `*` route.** Without it, bad URLs render a blank page silently.
- **Don't put the access token in `localStorage`** or in URL query strings.
- **Treat route params as strings** (they always are). Convert with `Number(id)` if you need an int.

Next: [Kafka Events](./06-kafka-events.md).
