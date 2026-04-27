import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "react-oidc-context";

/**
 * Shared chrome for authenticated pages: header with nav and sign-out.
 * The active page renders into <Outlet/>.
 */
export default function AppLayout() {
  const auth = useAuth();

  return (
    <div className="app">
      <header className="app__header">
        <Link to="/" className="app__brand">Banking</Link>
        <nav className="app__nav">
          <NavLink to="/" end>Accounts</NavLink>
          <NavLink to="/transactions/new">New transaction</NavLink>
          <NavLink to="/admin/users">Admin</NavLink>
        </nav>
        <button
          className="app__signout"
          onClick={() => auth.signoutRedirect().catch(() => {
            sessionStorage.clear();
            window.location.assign("/login");
          })}
        >
          Sign out
        </button>
      </header>
      <main className="app__main">
        <Outlet />
      </main>
    </div>
  );
}
