import { apiFetch } from "./apiClient.js";

export const listAccounts = () => apiFetch("/api/v1/accounts");

export const getAccount = (accountId) => apiFetch(`/api/v1/accounts/${accountId}`);

export const getTransactions = (accountId) =>
  apiFetch(`/api/v1/accounts/${accountId}/transactions`);
