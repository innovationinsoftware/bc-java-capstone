import { Routes, Route } from "react-router-dom";
import RequireAuth from "./auth/RequireAuth.jsx";
import RequireRole from "./auth/RequireRole.jsx";
import AppLayout from "./routes/AppLayout.jsx";
import LoginPage from "./routes/LoginPage.jsx";
import CallbackPage from "./routes/CallbackPage.jsx";
import AccountsPage from "./routes/AccountsPage.jsx";
import AccountDetailPage from "./routes/AccountDetailPage.jsx";
import NewTransactionPage from "./routes/NewTransactionPage.jsx";
import AdminUsersPage from "./routes/AdminUsersPage.jsx";
import NotFoundPage from "./routes/NotFoundPage.jsx";

/**
 * Route map. Layout route wraps every authenticated page so they share the
 * AppLayout chrome (header + nav). LoginPage and CallbackPage sit OUTSIDE
 * the layout so they render on a clean page.
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
      <Route path="/callback" element={<CallbackPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
