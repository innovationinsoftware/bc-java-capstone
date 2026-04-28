import { Link, NavLink, Outlet } from "react-router-dom";
import { useMe } from "../hooks/useMe.js";
import { readCsrfToken } from "../api/apiClient.js";

/**
 * Shared chrome for every page: header with nav + sign-in/sign-out.
 *
 * Sign in is a plain anchor — clicking it leaves the SPA and lets Spring
 * Security drive the OAuth flow on the BFF. Sign out is a form POST so
 * the CSRF token can be sent.
 */
export default function AppLayout() {
  const { user, loading } = useMe();
  const isAdmin = user?.role === "ADMIN";

  return (
    <div className="app">
      <header className="app__header">
        <Link to="/" className="app__brand">Banking</Link>
        <nav className="app__nav">
          <NavLink to="/" end>Accounts</NavLink>
          <NavLink to="/transactions/new">New transaction</NavLink>
          {isAdmin && <NavLink to="/admin/users">Admin</NavLink>}
        </nav>
        <div className="app__user">
          {loading ? (
            <span>…</span>
          ) : user ? (
            <form method="POST" action="/logout" className="app__logout">
              <input type="hidden" name="_csrf" value={readCsrfToken() ?? ""} />
              <span>{user.email}</span>
              <button type="submit">Sign out</button>
            </form>
          ) : (
            <a href="/oauth2/authorization/mock-auth">Sign in</a>
          )}
        </div>
      </header>
      <main className="app__main">
        <Outlet />
      </main>
    </div>
  );
}
