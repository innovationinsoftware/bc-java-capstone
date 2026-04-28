import { createContext, useContext, useEffect, useState } from "react";
import { apiFetch } from "../api/apiClient.js";

/**
 * Auth context for the BFF (Backend-for-Frontend) model.
 *
 * There is no token in the browser. The backend session cookie is sent
 * automatically by the browser on every request. This context simply
 * asks the backend "who are you?" on mount and caches the answer.
 *
 * Shape of `user` when authenticated:
 *   { userId, email, name, role }   (mirrors UserDto from the backend)
 *
 * `user` is null when unauthenticated or while loading.
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser]       = useState(undefined); // undefined = still loading
  const [loading, setLoading] = useState(true);

  async function refresh() {
    try {
      const me = await apiFetch("/api/v1/users/me");
      setUser(me);
    } catch (e) {
      // 401 means no session — user is not logged in. Any other error is
      // unexpected but we treat it the same way to avoid infinite redirects.
      setUser(null);
    } finally {
      setLoading(false);
    }
  }

  // Check auth state once on mount (e.g. after a page refresh).
  useEffect(() => { refresh(); }, []);

  return (
    <AuthContext.Provider value={{ user, loading, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

/** Convenience hook. Usage: const { user, loading } = useAuthContext(); */
export function useAuthContext() {
  return useContext(AuthContext);
}
