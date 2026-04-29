import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getAccount, getTransactions } from "../api/accounts.js";
import TransactionList from "../components/TransactionList.jsx";

/**
 * Account detail + transaction history. The :accountId param is always a
 * string — never assume it's a number even if the backend uses numeric IDs.
 */
export default function AccountDetailPage() {
  const { accountId } = useParams();
  const [account, setAccount] = useState(null);
  const [transactions, setTransactions] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    // TODO (Frontend Step 4a): Fetch both the account and its transactions in parallel.
    //
    // Use Promise.all to avoid two sequential round-trips:
    //   Promise.all([getAccount(accountId), getTransactions(accountId)])
    //     .then(([a, t]) => { setAccount(a); setTransactions(t); })
    //     .catch((e) => setError(e.message));
    //
    // accountId comes from useParams() above — include it in the dependency array.
  }, [accountId]);

  // TODO (Frontend Step 4b): Render the page.
  //
  // Guard states:
  //   error:   return <p className="error">{error}</p>
  //   loading: return <p>Loading…</p>   (when !account || !transactions)
  //
  // Loaded: return a <section> containing:
  //   <h1>{account.accountType} — {account.currency} {account.balance}</h1>
  //   <p><Link to="/transactions/new">New transaction</Link></p>
  //   <h2>Transactions</h2>
  //   <TransactionList transactions={transactions} />
  return null; // replace with real render
}
