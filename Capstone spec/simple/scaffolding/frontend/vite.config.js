import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Vite proxy: makes the BFF (port 8080) appear at the same origin as the SPA
// (port 5173) from the browser's perspective. The session cookie set by the
// BFF is scoped to localhost:5173 because Vite proxies the response unmodified.
//
// Without this proxy, every API call would be cross-origin and CORS would
// apply. The BFF pattern's whole architectural premise is same-origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api":    { target: "http://localhost:8080", changeOrigin: false },
      "/login":  { target: "http://localhost:8080", changeOrigin: false },
      "/logout": { target: "http://localhost:8080", changeOrigin: false },
      "/oauth2": { target: "http://localhost:8080", changeOrigin: false },
    },
  },
});
