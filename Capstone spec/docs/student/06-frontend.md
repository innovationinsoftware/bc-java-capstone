# 06 — Frontend

Day 2 afternoon. The React shell renders, routes work, components compile,
and `NewTransactionPage` is pre-built. You will fill in the data-fetching
layer (`apiFetch`, `useMe`) and the two page components that read account
data.

Re-read [`../05-frontend.md`](../05-frontend.md) before starting. The
"Stack", "The API client", and "Knowing who's signed in" sections matter most.

## Task 6.1 — `apiFetch` and `readCsrfToken`

**File:** `frontend/src/api/apiClient.js`

This is the entire authentication surface in JavaScript. Every API call goes
through `apiFetch`. Get this right and the rest of the frontend writes itself.

**`readCsrfToken()`** — parse the `XSRF-TOKEN` cookie from `document.cookie`.

- `document.cookie` is a single string, "; "-separated `name=value` pairs.
- Split on `"; "`, find the entry that `startsWith("XSRF-TOKEN=")`, take the
  value after the `=`.
- Return `undefined` if not found. Do **not** throw — the cookie may not yet
  exist on the very first request.

**`apiFetch(path, init = {})`** — the wrapper:

1. Determine method (`init.method ?? "GET"`) and uppercase it.
2. Build a `Headers` from `init.headers ?? {}`.
3. Default `Content-Type: application/json` when there's a body and no
   Content-Type was set.
4. For mutating methods (`POST | PUT | DELETE | PATCH`), read the CSRF token
   and set `X-XSRF-TOKEN` if a token was found. Skip on `GET` and `HEAD`.
5. Call `fetch(path, { ...init, headers, credentials: "same-origin" })`.
6. On 401, throw `new ApiError(401, { detail: "Unauthorized" })`. Do **not**
   redirect from here. (Why? See "Why throw on 401" below.)
7. On any other non-OK status, parse the response body as JSON (catch parse
   errors, default to `{}`) and throw `new ApiError(status, body)`.
8. Return `undefined` for 204 No Content; otherwise return `res.json()`.

**Why throw on 401 instead of redirecting?**

Naïve approach: on 401, `window.location = "/login"`. Result: page loads,
calls `/api/v1/users/me`, gets 401, redirects to `/login`, page loads, calls
`/api/v1/users/me`, gets 401, redirect, page loads… infinite loop.

Throwing lets `useMe` catch the error and set `user = null`. `AppLayout`
renders the sign-in page. No navigation happens — but the user clearly sees
a sign-in link. The browser only navigates when the user clicks it.

> **Note:** the example in [`../05-frontend.md`](../05-frontend.md) does
> both — calls `window.location.assign(LOGIN_URL)` *and* throws. The
> scaffolding's apiClient takes the safer throw-only path, documented in
> the file's own Javadoc. Follow the scaffolding pattern.

**Why `same-origin` and not `include`?**

Vite proxies `/api`, `/login`, `/logout`, `/oauth2` to the BFF on `:8081`.
From the browser's point of view everything is on `localhost:5173` — same
origin. `same-origin` is safer than `include` because it doesn't send cookies
to actual cross-origin URLs by accident. (`vite.config.js` already declares
those proxies.)

## Task 6.2 — `useMe`

**File:** `frontend/src/hooks/useMe.js`

Three lines of work in the `useEffect`:

- Call `apiFetch("/api/v1/users/me")`.
- `.then(setUser)` — store the user object.
- `.catch(() => setUser(null))` — on error (including 401), null out the user.
- `.finally(() => setLoading(false))` — always clear the loading flag.

The `useState`s are already declared. Don't add other state.

`AppLayout` calls `useMe()` and shows a loading spinner until `loading` is
false, then either the sign-in page (`user === null`) or the authenticated
layout. Don't add navigation logic to the hook itself.

## Task 6.3 — `AccountsPage`

**File:** `frontend/src/routes/AccountsPage.jsx`

The state hooks are already declared. You implement:

1. **`useEffect`** — call `listAccounts()`, store in state, catch errors into
   `error` state.
2. **Render** — four states (the rubric explicitly grades all four):
   - **Error:** `error !== null` → an error banner with the message.
   - **Loading:** `accounts === null && error === null` → "Loading accounts…".
   - **Empty:** `accounts.length === 0` → "You have no accounts yet." (Empty
     ≠ loading; check `accounts === null` for loading and `accounts.length === 0`
     for empty.)
   - **Loaded:** render an `<h1>` and a `<ul>` of `<AccountCard>` components,
     keyed by `account.accountId`.

The `<AccountCard>` component is already implemented. Just import it and use
it.

## Task 6.4 — `AccountDetailPage`

**File:** `frontend/src/routes/AccountDetailPage.jsx`

You need both the account record **and** its transactions. Don't make two
sequential requests — issue them in parallel with `Promise.all`:

```text
Promise.all([getAccount(id), getTransactions(id)])
  .then(([a, t]) => ...)
  .catch(...)
```

`accountId` comes from `useParams()`. Add it to the `useEffect` dependency
array — otherwise React Router warnings fire when the user clicks a different
account.

Render states:

- **Error:** show the message.
- **Loading:** when either account or transactions is null.
- **Loaded:** an `<h1>` with type/currency/balance, a `<Link>` to
  `/transactions/new`, and a `<TransactionList>` of the transactions.

`<TransactionList>` is already implemented.

## Task 6.5 — End-to-end smoke test

With the backend up:

1. Sign in as `alice`.
2. AccountsPage shows alice's accounts (you may need to seed them — see
   `scaffolding/docs/1-setup.md` "Seeding Demo Accounts").
3. Click an account. Detail page shows balance + transactions.
4. Click "New transaction." (The pre-built `NewTransactionPage` opens.)
   Submit a deposit of 25.00.
5. You land on the account detail page; the new row is at the top of the
   transaction list.
6. Try a withdrawal larger than the balance. The form shows the server's
   error message (`INSUFFICIENT_FUNDS`); the navigation does **not** happen.
7. Submit an internal transfer between two of your accounts. The detail page
   shows the matching `TRANSFER_OUT` row; the other account shows the
   `TRANSFER_IN`.

If any of this is broken, the issue is almost always one of:

- Forgetting `credentials: "same-origin"` in `apiFetch`.
- Missing CSRF header on POSTs.
- Misconfigured Vite proxy.

## Common pitfalls (graded)

- Plain `<a href="...">` for in-app links. Use `<Link to="...">`. The exception
  is the sign-in link — that one **must** be a real anchor because it leaves
  the SPA. (See `AppLayout.jsx` for the precedent.)
- `navigate(...)` during render. Only use it inside event handlers or `useEffect`.
- Dropping the `*` route. The shell already handles 404s — don't remove it.
- Treating `useParams()` values as numbers. They are always strings.

## Done when

- [ ] Sign in as alice end-to-end works.
- [ ] AccountsPage and AccountDetailPage render the right state for loading
      / empty / error / loaded.
- [ ] Submitting a deposit, withdrawal, internal transfer, and external
      transfer all work in the SPA.
- [ ] DevTools still shows no tokens (re-verify after frontend changes).

Next: [07-security-validation.md](./07-security-validation.md).
