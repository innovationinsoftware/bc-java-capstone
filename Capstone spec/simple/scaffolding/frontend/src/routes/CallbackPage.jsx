import { useAuth } from 'react-oidc-context';
import { Navigate } from 'react-router-dom';

/**
 * Handles the redirect from Google OAuth.
 */
export const CallbackPage = () => {
    const auth = useAuth();

    // TODO: If the auth request has an error (auth.error), display the error message.
    
    if (auth.isAuthenticated) {
        // TODO: Redirect the user back to the application root ("/")
        return null; // REPLACE THIS
    }

    return <div>Verifying authentication code...</div>;
};
