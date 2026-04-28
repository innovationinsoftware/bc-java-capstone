import { Navigate, useLocation } from "react-router-dom";
import { useAuthContext } from "./AuthContext.jsx";

/**
 * Guards routes that require a logged-in user.
 *
 * In the BFF model "authenticated" means the backend session is valid,
 * which is determined by the /api/v1/users/me call in AuthContext.
 *
 * While the check is in flight (loading=true) a spinner is shown so the
 * user doesn't see a flash of the login page on page refresh.
 */
export default function RequireAuth({ children }) {
  const { user, loading } = useAuthContext();
  const location = useLocation();

  if (loading) return <p className="loading">Loading…</p>;

  if (!user) {
    // Not authenticated — redirect to /login and remember where we were.
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return children;
}
