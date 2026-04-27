// One place that knows about cookies, CSRF, and 401 handling.
// Components and resource modules call this; nothing else in the app
// touches fetch directly.
//
// No tokens. The BFF holds them server-side. The browser only carries
// the session cookie (sent automatically) and the CSRF cookie (which we
// echo as X-XSRF-TOKEN on mutations).

const LOGIN_URL = "/oauth2/authorization/mock-auth";

export function readCsrfToken() {
  return document.cookie
    .split("; ")
    .find(row => row.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
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
  const headers = new Headers(init.headers ?? {});
  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json");
  }
  if (method !== "GET" && method !== "HEAD") {
    const csrf = readCsrfToken();
    if (csrf) headers.set("X-XSRF-TOKEN", csrf);
  }

  const res = await fetch(path, {
    ...init,
    headers,
    credentials: "same-origin",
  });

  if (res.status === 401) {
    // No session (or it expired). Bounce to BFF login. After auth, Spring
    // sends the user back to '/' and the cookie is set.
    window.location.assign(LOGIN_URL);
    throw new ApiError(401, { detail: "Unauthorized" });
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body);
  }
  return res.status === 204 ? undefined : res.json();
}
