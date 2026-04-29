import { useEffect, useState } from "react";
import { listAccounts } from "../api/accounts.js";
import AccountCard from "../components/AccountCard.jsx";

/**
 * Lists the caller's own accounts. Shows loading, empty, and error states
 * (the rubric grades all three).
 */
export default function AccountsPage() {
  const [accounts, setAccounts] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    // TODO (Frontend Step 3a): Load all accounts via listAccounts().
    //
    // Call listAccounts()
    //   .then(setAccounts)           — store the array in state
    //   .catch((e) => setError(e.message)); — store the error message
  }, []);

  // TODO (Frontend Step 3b): Render the correct state.
  //
  // The rubric grades all three non-loaded states:
  //   1. Error:   accounts === null && error !== null
  //               → return <p className="error">Could not load accounts: {error}</p>
  //   2. Loading: accounts === null (no error)
  //               → return <p>Loading accounts…</p>
  //   3. Empty:   accounts.length === 0
  //               → return <p>You have no accounts yet.</p>
  //   4. Loaded:  return a <section> with <h1>Your accounts</h1> and
  //               a <ul className="accounts"> where each item is:
  //               <AccountCard key={a.accountId} account={a} />
  return null; // replace with real render
}
