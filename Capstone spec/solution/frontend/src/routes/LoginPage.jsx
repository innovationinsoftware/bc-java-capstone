import { Navigate } from "react-router-dom";
import { useAuthContext } from "../auth/AuthContext.jsx";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

/**
 * Login page for the BFF model.
 *
 * "Sign in with Google" does a hard redirect to the backend's OAuth2
 * authorization endpoint. Spring Security intercepts that URL and starts
 * the server-side auth code flow with Google.
 *
 * The flow:
 *   1. Browser → GET http://localhost:8081/oauth2/authorization/google
 *   2. Spring redirects → Google login page
 *   3. User authenticates → Google → POST http://localhost:8081/login/oauth2/code/google
 *   4. Spring exchanges code for tokens, creates BANK_USERS row via
 *      CustomOAuth2UserService, sets HttpOnly JSESSIONID cookie
 *   5. Spring redirects browser → http://localhost:5173/
 *   6. React mounts, AuthContext calls /api/v1/users/me → session valid → show app
 */
export default function LoginPage() {
  const { user, loading } = useAuthContext();

  // Already logged in — skip the login page.
  if (!loading && user) return <Navigate to="/" replace />;

  function signIn() {
    // Hard navigation — the browser follows the redirect chain to Google
    // and back without React Router interfering.
    window.location.assign(`${API_BASE}/oauth2/authorization/google`);
  }

  return (
    <div className="login">
      <h1>Banking</h1>
      <p>Sign in to view your accounts.</p>
      <button onClick={signIn}>Sign in with Google</button>
    </div>
  );
}
