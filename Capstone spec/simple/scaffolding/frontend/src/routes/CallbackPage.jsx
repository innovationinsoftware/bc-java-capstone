import { useAuth } from "react-oidc-context";
import { Navigate } from "react-router-dom";

/**
 * Google redirects here with ?code=...&state=... after consent.
 * react-oidc-context handles the code exchange behind the scenes; we just
 * wait for it to finish and then send the user to the app.
 */
export default function CallbackPage() {
  const auth = useAuth();

  if (auth.error) return <p className="error">Login error: {auth.error.message}</p>;
  if (auth.isLoading) return <p>Finishing sign-in…</p>;
  if (auth.isAuthenticated) return <Navigate to="/" replace />;
  return <p>Sign-in did not complete. <a href="/login">Try again.</a></p>;
}
