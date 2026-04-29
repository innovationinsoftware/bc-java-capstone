import { AuthProvider as OidcAuthProvider } from 'react-oidc-context';

const oidcConfig = {
    // TODO: Set authority to "https://accounts.google.com"
    authority: "REPLACE_ME", 
    // TODO: Set client_id from environment variables (import.meta.env.VITE_GOOGLE_CLIENT_ID)
    client_id: "REPLACE_ME",
    // TODO: Set redirect_uri to the callback route of the application (e.g. "http://localhost:5173/callback")
    redirect_uri: window.location.origin + "/callback",
    // Google needs these standard OIDC scopes
    scope: "openid email profile",
};

export const AuthProvider = ({ children }) => {
    // TODO: Wrap standard children with <OidcAuthProvider {...oidcConfig}>
    return <>{children}</>;
};
