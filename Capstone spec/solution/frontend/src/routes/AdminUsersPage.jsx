import { useEffect, useState } from "react";
import { listUsers } from "../api/users.js";

/** Admin-only. The route is gated by RequireRole; the API is gated by hasRole('ADMIN'). */
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
