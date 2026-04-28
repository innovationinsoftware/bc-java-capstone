import { apiFetch } from "./apiClient.js";

export const getMe = () => apiFetch("/api/v1/users/me");

export const listUsers = () => apiFetch("/api/v1/admin/users");
