import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuthContext } from "../auth/AuthContext.jsx";
import { apiFetch } from "../api/apiClient.js";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

/**
 * Shared chrome for authenticated pages.
 *
 * Sign-out in the BFF model:
 *   POST /logout to the backend (CSRF-protected by Spring Security).
 *   Spring invalidates the session and deletes the JSESSIONID cookie.
 *   Then Spring redirects the browser to http://localhost:5173/login.
 *
 *   We POST via apiFetch so the X-XSRF-TOKEN header is included automatically.
 */
export default function AppLayout() {
  const { user } = useAuthContext();

  async function signOut() {
    try {
      // POST /logout is handled by Spring Security's LogoutFilter.
      // apiFetch adds X-XSRF-TOKEN automatically on POST.
      await apiFetch("/logout", { method: "POST" });
    } finally {
      // Spring redirects us to /login after session invalidation.
      // In case the redirect doesn't fire (network error), navigate manually.
      window.location.assign("/login");
    }
  }

  return (
    <div className="app">
      <header className="app__header">
        <Link to="/" className="app__brand">Banking</Link>
        <nav className="app__nav">
          <NavLink to="/" end>Accounts</NavLink>
          <NavLink to="/transactions/new">New transaction</NavLink>
          {user?.role === "ADMIN" && <NavLink to="/admin/users">Admin</NavLink>}
        </nav>
        <button className="app__signout" onClick={signOut}>
          Sign out
        </button>
      </header>
      <main className="app__main">
        <Outlet />
      </main>
    </div>
  );
}
