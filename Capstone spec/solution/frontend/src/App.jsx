import { Routes, Route } from "react-router-dom";
import RequireAuth from "./auth/RequireAuth.jsx";
import RequireRole from "./auth/RequireRole.jsx";
import AppLayout from "./routes/AppLayout.jsx";
import LoginPage from "./routes/LoginPage.jsx";
import AccountsPage from "./routes/AccountsPage.jsx";
import AccountDetailPage from "./routes/AccountDetailPage.jsx";
import NewTransactionPage from "./routes/NewTransactionPage.jsx";
import AdminUsersPage from "./routes/AdminUsersPage.jsx";
import NotFoundPage from "./routes/NotFoundPage.jsx";

/**
 * Route map.
 *
 * In the BFF model there is no /callback route — Spring Security handles
 * the OAuth2 redirect at /login/oauth2/code/google on the backend, then
 * redirects the browser to / directly.
 */
export default function App() {
  return (
    <Routes>
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route path="/" element={<AccountsPage />} />
        <Route path="/accounts/:accountId" element={<AccountDetailPage />} />
        <Route path="/transactions/new" element={<NewTransactionPage />} />
        <Route
          path="/admin/users"
          element={<RequireRole role="ADMIN"><AdminUsersPage /></RequireRole>}
        />
      </Route>
      <Route path="/login" element={<LoginPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
