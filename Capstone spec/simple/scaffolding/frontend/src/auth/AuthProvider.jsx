import { AuthProvider as OidcAuthProvider } from "react-oidc-context";
import { WebStorageStateStore } from "oidc-client-ts";

/**
 * Wraps the app in an OAuth2/OIDC provider configured for Google.
 * Uses Authorization Code + PKCE (default in oidc-client-ts).
 *
 * Tokens live in sessionStorage — clears on tab close, lower exposure
 * than localStorage on a shared machine. Never localStorage.
 */
const oidcConfig = {
  authority: "https://accounts.google.com",
  client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID,
  redirect_uri: window.location.origin + "/callback",
  post_logout_redirect_uri: window.location.origin + "/login",
  response_type: "code",
  scope: "openid email profile",
  loadUserInfo: false,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  // Drop the ?code=...&state=... from the URL after the redirect.
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

export function AuthProvider({ children }) {
  return <OidcAuthProvider {...oidcConfig}>{children}</OidcAuthProvider>;
}
