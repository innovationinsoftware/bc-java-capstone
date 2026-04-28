import { Navigate } from "react-router-dom";
import { useAuthContext } from "./AuthContext.jsx";

/**
 * Guards routes that require a specific role (e.g. "ADMIN").
 *
 * In the BFF model the role is loaded from the backend via AuthContext
 * (from /api/v1/users/me). No separate fetch is needed here — the role
 * is already available by the time RequireRole renders, because RequireAuth
 * wraps it and waits for the context to load first.
 *
 * The backend still enforces the role via @PreAuthorize — this guard is
 * purely for UX (avoiding a visible 403 flash on screen).
 */
export default function RequireRole({ role, children }) {
  const { user } = useAuthContext();

  // user is guaranteed non-null here (RequireAuth rendered first).
  if (user?.role !== role) return <Navigate to="/" replace />;

  return children;
}
