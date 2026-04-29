import { useAuth } from 'react-oidc-context';
import { Navigate } from 'react-router-dom';

/**
 * A wrapper component that checks if a user is authenticated.
 * If not, it redirects them to the /login page.
 * If they are loading, it displays a loading message.
 */
export const RequireAuth = ({ children }) => {
    // TODO: use the useAuth() hook to get auth state
    const auth = { isAuthenticated: false, isLoading: false }; // REPLACE THIS

    if (auth.isLoading) {
        return <div>Logging in...</div>;
    }

    if (!auth.isAuthenticated) {
        // TODO: Redirect to /login using React Router's <Navigate> component.
        return null; // REPLACE THIS
    }

    return children;
};
