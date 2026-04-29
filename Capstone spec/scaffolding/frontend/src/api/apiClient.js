/**
 * API client — the entire auth surface in JavaScript.
 *
 * Same-origin: all requests go through the Vite proxy to the BFF on :8080.
 * The browser sends the JSESSIONID cookie automatically (same-origin).
 * No tokens, no localStorage, no oidc-client-ts.
 *
 * CSRF: Spring sets an XSRF-TOKEN cookie (JS-readable). On mutations
 * (POST, PUT, DELETE, PATCH), we read it and send X-XSRF-TOKEN.
 *
 * On 401: throw ApiError so callers (useMe etc.) can set user=null and
 * render sign-in options. No redirect — avoids infinite reload loops.
 */

function readCsrfToken() {
  // TODO (Frontend Step 1a): Parse the XSRF-TOKEN cookie from document.cookie.
  //
  // document.cookie is a single string of "; "-separated "name=value" pairs.
  // Split on "; ", find the entry that startsWith("XSRF-TOKEN="),
  // then extract the value after "=" with split("=")[1].
  // Return undefined if the cookie isn't found.
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.detail || body?.title || `HTTP ${status}`);
    this.status = status;
    this.body = body;
  }
}

export async function apiFetch(path, init = {}) {
  // TODO (Frontend Step 1b): Implement the fetch wrapper.
  //
  // Step-by-step:
  //   1. Get the HTTP method:   const method = (init.method ?? "GET").toUpperCase()
  //   2. Build headers:         const headers = new Headers(init.headers ?? {})
  //   3. Default Content-Type:  if init.body exists and "Content-Type" is not already set,
  //                             headers.set("Content-Type", "application/json")
  //   4. CSRF header:           if method is not "GET" and not "HEAD", call readCsrfToken().
  //                             If a token is returned, headers.set("X-XSRF-TOKEN", csrf)
  //   5. Fetch:                 const res = await fetch(path, { ...init, headers, credentials: "same-origin" })
  //   6. 401 guard:             if (res.status === 401) throw new ApiError(401, { detail: "Unauthorized" })
  //   7. Error guard:           if (!res.ok) { parse body with res.json().catch(() => ({})); throw new ApiError(res.status, body) }
  //   8. Return body:           return res.status === 204 ? undefined : res.json()
  throw new Error("apiFetch: not yet implemented");
}
