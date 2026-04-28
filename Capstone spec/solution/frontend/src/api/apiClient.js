/**
 * Single fetch wrapper for the BFF (Backend-for-Frontend) model.
 *
 * Authentication is handled entirely via the JSESSIONID cookie that Spring
 * Security sets after the OAuth2 login flow. No token is stored or sent
 * manually — the browser attaches the cookie automatically.
 *
 * CSRF protection:
 * Spring sets an XSRF-TOKEN cookie (readable by JS) after the first request.
 * On every mutating request (POST, PUT, DELETE, PATCH) we read that cookie
 * and echo it back as the X-XSRF-TOKEN header. GET requests are safe-method
 * so they don't need the CSRF token.
 *
 * credentials: "include" is required so the browser sends the JSESSIONID
 * cookie even though the API origin (localhost:8081) differs from the SPA
 * origin (localhost:5173). The backend CORS config allows credentials.
 *
 * NOTE: No automatic redirect on 401. The AuthContext detects "no session"
 * and sets user=null; RequireAuth handles the redirect to /login via React
 * Router. This avoids an infinite redirect loop on initial page load.
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

/** Read the XSRF-TOKEN cookie Spring sets. Returns null if not yet set. */
function getCsrfToken() {
  const cookie = document.cookie
    .split(";")
    .find((c) => c.trim().startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.split("=")[1]) : null;
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.detail || body?.title || `HTTP ${status}`);
    this.status = status;
    this.body = body;
  }
}

export async function apiFetch(path, init = {}) {
  const method = (init.method ?? "GET").toUpperCase();
  const isMutating = ["POST", "PUT", "DELETE", "PATCH"].includes(method);

  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: "include", // always send the JSESSIONID session cookie
    headers: {
      "Content-Type": "application/json",
      // Echo the CSRF token on mutating requests — Spring rejects without it.
      ...(isMutating ? { "X-XSRF-TOKEN": getCsrfToken() } : {}),
      ...(init.headers ?? {}),
    },
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body);
  }

  return res.status === 204 ? undefined : res.json();
}
