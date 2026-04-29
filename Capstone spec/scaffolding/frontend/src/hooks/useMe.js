import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiClient.js";

/**
 * Hook to fetch the current user's profile.
 *
 * Returns { user, loading }:
 *   - user is null if not signed in (401 bounces to login via apiFetch)
 *   - loading is true until the first response settles
 *
 * Used by AppLayout to show "Sign in" vs "Sign out (alice)" in the header,
 * and by AdminUsersPage to conditionally show the admin nav link.
 */
export function useMe() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // TODO (Frontend Step 2): Fetch the current user's profile.
    //
    // Call apiFetch("/api/v1/users/me")
    //   .then(setUser)               — store the returned user object in state
    //   .catch(() => setUser(null))  — on any error (including 401), set user to null
    //   .finally(() => setLoading(false)); — always clear the loading flag when done
  }, []);

  return { user, loading };
}
