import { useAuth } from "react-oidc-context";
import { Navigate } from "react-router-dom";

export default function LoginPage() {
  const auth = useAuth();

  if (auth.isAuthenticated) return <Navigate to="/" replace />;

  return (
    <div className="login">
      <h1>Banking</h1>
      <p>Sign in to view your accounts.</p>
      <button onClick={() => auth.signinRedirect()}>
        Sign in with Google
      </button>
      {auth.error && <p className="error">Sign-in failed: {auth.error.message}</p>}
    </div>
  );
}
