import { Routes, Route } from "react-router-dom";
import AppLayout from "./routes/AppLayout.jsx";
import AccountsPage from "./routes/AccountsPage.jsx";
import AccountDetailPage from "./routes/AccountDetailPage.jsx";
import NewTransactionPage from "./routes/NewTransactionPage.jsx";
import AdminUsersPage from "./routes/AdminUsersPage.jsx";
import NotFoundPage from "./routes/NotFoundPage.jsx";

/**
 * Routes are flat — no /login or /callback. Spring on the BFF handles those
 * URLs (/oauth2/authorization/mock-auth, /login/oauth2/code/mock-auth).
 *
 * If an API call returns 401, apiClient redirects the browser to the BFF's
 * login URL; Spring brings the user back here automatically.
 */
export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<AccountsPage />} />
        <Route path="/accounts/:accountId" element={<AccountDetailPage />} />
        <Route path="/transactions/new" element={<NewTransactionPage />} />
        <Route path="/admin/users" element={<AdminUsersPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
