import { User } from "oidc-client-ts";

/**
 * Single place that knows about auth headers. Components and resource
 * modules call this; nothing else in the app calls fetch directly.
 *
 * We read the access token straight from sessionStorage (where
 * oidc-client-ts puts it) instead of threading the auth context through
 * every API call. The key format is what oidc-client-ts uses by default.
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

function readAccessToken() {
  const authority = "https://accounts.google.com";
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
  const key = `oidc.user:${authority}:${clientId}`;
  const raw = sessionStorage.getItem(key);
  if (!raw) return null;
  const user = User.fromStorageString(raw);
  return user.expired ? null : user.access_token;
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.detail || body?.title || `HTTP ${status}`);
    this.status = status;
    this.body = body;
  }
}

export async function apiFetch(path, init = {}) {
  const token = readAccessToken();
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers ?? {}),
    },
  });

  if (res.status === 401) {
    // Stale or missing token — drop it and bounce to login.
    sessionStorage.clear();
    window.location.assign("/login");
    throw new ApiError(401, { detail: "Unauthorized" });
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body);
  }

  return res.status === 204 ? undefined : res.json();
}
