import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import { ApiResponse } from "../types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/v1";

export const apiClient = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        "Content-Type": "application/json"
    }
});

// Request interceptor: Attach JWT token if available
apiClient.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        const token = localStorage.getItem("token");
        if (token && config.headers) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error: AxiosError) => Promise.reject(error)
);

// Response interceptor: Extract response data and handle unauthorized responses
apiClient.interceptors.response.use(
    (response) => response,
    (error: AxiosError<ApiResponse<null>>) => {
        if (error.response?.status === 401) {
            localStorage.removeItem("token");
            localStorage.removeItem("user");

            // Redirect to login only if not already on auth pages
            if (
                window.location.pathname !== "/login" &&
                window.location.pathname !== "/register"
            ) {
                window.location.href = "/login";
            }
        }

        const customMessage = error.response?.data?.message || error.message || "Đã có lỗi xảy ra";
        return Promise.reject(new Error(customMessage));
    }
);

export default apiClient;
