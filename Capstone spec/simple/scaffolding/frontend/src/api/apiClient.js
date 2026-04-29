import axios from 'axios';
import { User } from 'oidc-client-ts';

const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',
});

// TODO: Create an Axios Request Interceptor.
// 1. Read the OIDC user string from sessionStorage (e.g. sessionStorage.getItem(oidc.user: + authority + : + clientId))
// 2. Parse it into a User object
// 3. If user.access_token exists, add Authorization: Bearer \ to config.headers
// 4. Return the merged config.

apiClient.interceptors.request.use(
    (config) => {
        // REPLACE THIS with your token loading logic
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

export default apiClient;
