import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { listAccounts } from "../api/accounts.js";
import { submitTransaction } from "../api/transactions.js";
import TransactionForm from "../components/TransactionForm.jsx";

/**
 * Submit a transaction. Loads accounts, delegates form rendering and field
 * state to TransactionForm, handles the API call and navigation here.
 */
export default function NewTransactionPage() {
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    listAccounts().then(setAccounts);
  }, []);

  async function handleSubmit(formData) {
    // TODO (Frontend Step 5a): Submit the transaction and navigate on success.
    //
    // 1. setError(null) and setSubmitting(true)
    // 2. try { await submitTransaction(formData); navigate(`/accounts/${formData.accountId}`); }
    // 3. catch (e) { setError(e.message); }
    // 4. finally { setSubmitting(false); }
  }

  // TODO (Frontend Step 5b): Render a loading guard, then the form.
  //
  // If accounts haven't loaded yet (accounts.length === 0), return:
  //   <p>Loading accounts…</p>
  //
  // Otherwise return:
  //   <TransactionForm
  //     accounts={accounts}
  //     onSubmit={handleSubmit}
  //     submitting={submitting}
  //     error={error}
  //   />
  return null; // replace with real render
}
