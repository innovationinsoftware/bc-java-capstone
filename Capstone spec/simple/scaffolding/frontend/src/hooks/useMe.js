import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiClient.js";

/**
 * Returns { user, loading } where user is { userId, email, displayName, role }.
 *
 * If the user is not signed in, apiFetch's 401 handler will already have
 * redirected to the BFF login URL — so you typically only see user=null on
 * the first render before the call settles, or when the call genuinely
 * errored (network down).
 */
export function useMe() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    apiFetch("/api/v1/users/me")
      .then(u => { if (!cancelled) setUser(u); })
      .catch(() => { /* 401 already redirected; ignore other errors */ })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  return { user, loading };
}
