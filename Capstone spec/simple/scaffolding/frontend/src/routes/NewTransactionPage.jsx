import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { listAccounts } from "../api/accounts.js";
import { submitTransaction } from "../api/transactions.js";

/**
 * Submit a transaction. The form mirrors the backend's validation rules:
 *   - amount > 0
 *   - counterparty required only for TRANSFER_OUT
 * Disable the submit button while in flight to avoid duplicate submits.
 */
export default function NewTransactionPage() {
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState([]);
  const [accountId, setAccountId] = useState("");
  const [type, setType] = useState("DEPOSIT");
  const [amount, setAmount] = useState("");
  const [counterparty, setCounterparty] = useState("");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    listAccounts().then((a) => {
      setAccounts(a);
      if (a.length > 0) setAccountId(a[0].accountId);
    });
  }, []);

  const counterpartyRequired = type === "TRANSFER_OUT";

  async function onSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await submitTransaction({
        accountId,
        type,
        amount: Number(amount),
        counterparty: counterpartyRequired ? counterparty : null,
        description: description || null,
      });
      navigate(`/accounts/${accountId}`);
    } catch (e) {
      setError(e.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="tx-form">
      <h1>New transaction</h1>

      <label>
        Account
        <select value={accountId} onChange={(e) => setAccountId(e.target.value)} required>
          {accounts.map((a) => (
            <option key={a.accountId} value={a.accountId}>
              {a.accountType} ({a.currency} {a.balance})
            </option>
          ))}
        </select>
      </label>

      <label>
        Type
        <select value={type} onChange={(e) => setType(e.target.value)} required>
          <option value="DEPOSIT">Deposit</option>
          <option value="WITHDRAWAL">Withdrawal</option>
          <option value="TRANSFER_OUT">Transfer</option>
        </select>
      </label>

      <label>
        Amount
        <input
          type="number" step="0.01" min="0.01" required
          value={amount} onChange={(e) => setAmount(e.target.value)}
        />
      </label>

      {counterpartyRequired && (
        <label>
          Counterparty account ID
          <input
            type="text" required
            value={counterparty}
            onChange={(e) => setCounterparty(e.target.value)}
          />
        </label>
      )}

      <label>
        Description (optional)
        <input
          type="text" maxLength={255}
          value={description} onChange={(e) => setDescription(e.target.value)}
        />
      </label>

      {error && <p className="error">{error}</p>}

      <button type="submit" disabled={submitting}>
        {submitting ? "Submitting…" : "Submit"}
      </button>
    </form>
  );
}
