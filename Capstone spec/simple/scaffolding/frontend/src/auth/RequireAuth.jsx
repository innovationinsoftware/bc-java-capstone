import { useAuth } from "react-oidc-context";
import { Navigate, useLocation } from "react-router-dom";

/**
 * Guards routes that require a logged-in user. If not authenticated,
 * redirects to /login and remembers where the user was trying to go.
 */
export default function RequireAuth({ children }) {
  const auth = useAuth();
  const location = useLocation();

  if (auth.isLoading) return <p>Loading…</p>;

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return children;
}
