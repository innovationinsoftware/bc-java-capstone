import { useEffect, useState } from "react";
import { listUsers } from "../api/users.js";

/**
 * Admin-only. The nav link is hidden for non-admins (UX, see AppLayout's
 * useMe()). The actual access control is on the Resource Server — a
 * non-admin who navigates here directly gets a 403 from the API and the
 * page renders an error.
 */
export default function AdminUsersPage() {
  const [users, setUsers] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    listUsers().then(setUsers).catch((e) => setError(e.message));
  }, []);

  if (error) return <p className="error">{error}</p>;
  if (!users) return <p>Loading…</p>;

  return (
    <section>
      <h1>Users</h1>
      <table className="users">
        <thead>
          <tr><th>ID</th><th>Email</th><th>Name</th><th>Role</th></tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.userId}>
              <td>{u.userId}</td>
              <td>{u.email}</td>
              <td>{u.displayName}</td>
              <td>{u.role}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
