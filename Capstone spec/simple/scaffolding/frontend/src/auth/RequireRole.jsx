import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { getMe } from "../api/users.js";

/**
 * Guards routes that require a specific role (e.g. "ADMIN"). Asks the
 * backend who the caller is — the backend is the source of truth for the
 * role, not the JWT. UI gating is for UX; the API still enforces it.
 */
export default function RequireRole({ role, children }) {
  const [me, setMe] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    getMe()
      .then(setMe)
      .catch(setError);
  }, []);

  if (error) return <p>Could not check your role.</p>;
  if (!me) return <p>Checking permissions…</p>;
  if (me.role !== role) return <Navigate to="/" replace />;

  return children;
}
