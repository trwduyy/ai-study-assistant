import axios, {
    AxiosError,
    AxiosHeaders,
    AxiosRequestConfig,
    InternalAxiosRequestConfig
} from "axios";
import { ApiResponse, TokenRefreshResponse } from "../types";
import tokenStorage from "./tokenStorage";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/v1";

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
    _retry?: boolean;
}

interface FailedQueueItem {
    resolve: (token: string) => void;
    reject: (error: Error) => void;
}

export const apiClient = axios.create({
    baseURL: API_BASE_URL,
    timeout: 30000,
    headers: {
        "Content-Type": "application/json"
    }
});

let isRefreshing = false;
let failedQueue: FailedQueueItem[] = [];

function processQueue(error: Error | null, newAccessToken: string | null): void {
    failedQueue.forEach((item) => {
        if (error) {
            item.reject(error);
        } else if (newAccessToken) {
            item.resolve(newAccessToken);
        } else {
            item.reject(new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."));
        }
    });
    failedQueue = [];
}

function redirectToLogin(): void {
    const currentHash = window.location.hash;
    if (!currentHash.startsWith("#/login") && !currentHash.startsWith("#/register")) {
        window.location.hash = "#/login";
    }
}

function isTechnicalMessage(message: string): boolean {
    const lower = message.toLowerCase();
    return (
        lower.includes("exception") ||
        lower.includes("sql") ||
        lower.includes("stacktrace") ||
        lower.includes("java.") ||
        lower.includes("org.springframework") ||
        lower.includes("request failed with status code") ||
        lower.includes("network error") ||
        lower.includes("timeout of")
    );
}

export function extractFriendlyErrorMessage(error: unknown): string {
    if (axios.isAxiosError<ApiResponse<null>>(error)) {
        const status = error.response?.status;
        const apiError = error.response?.data?.error;

        if (apiError?.fields && apiError.fields.length > 0) {
            const firstFieldMsg = apiError.fields[0]?.message;
            if (firstFieldMsg && !isTechnicalMessage(firstFieldMsg)) {
                return firstFieldMsg;
            }
        }

        if (apiError?.message && !isTechnicalMessage(apiError.message)) {
            return apiError.message;
        }

        if (error.code === "ERR_NETWORK" || !error.response) {
            return "Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng và thử lại.";
        }
        if (error.code === "ECONNABORTED") {
            return "Yêu cầu mất quá nhiều thời gian để phản hồi. Vui lòng thử lại sau.";
        }

        switch (status) {
            case 400:
                return "Dữ liệu gửi lên chưa hợp lệ. Vui lòng kiểm tra lại.";
            case 401:
                return "Phiên đăng nhập đã hết hạn hoặc thông tin xác thực không đúng.";
            case 403:
                return "Bạn không có quyền thực hiện thao tác này.";
            case 404:
                return "Không tìm thấy dữ liệu được yêu cầu.";
            case 409:
                return "Dữ liệu vừa được cập nhật ở nơi khác hoặc bị trùng lặp. Vui lòng tải lại.";
            case 422:
                return "Không thể xử lý yêu cầu theo quy tắc hiện tại.";
            case 429:
                return "Bạn thao tác quá nhanh hoặc đã chạm giới hạn sử dụng. Vui lòng chờ giây lát.";
            case 502:
                return "Dịch vụ liên kết bên ngoài đang gặp sự cố. Vui lòng thử lại sau.";
            case 504:
                return "Dịch vụ xử lý quá thời gian cho phép. Vui lòng thử lại sau.";
            default:
                return "Hệ thống đang bận hoặc gặp sự cố tạm thời. Vui lòng thử lại sau.";
        }
    }

    if (error instanceof Error && error.message && !isTechnicalMessage(error.message)) {
        return error.message;
    }

    return "Đã có lỗi xảy ra. Vui lòng thử lại sau.";
}

export class ApiRequestError extends Error {
    public readonly status?: number;
    public readonly code?: string;

    constructor(message: string, status?: number, code?: string) {
        super(message);
        this.name = "ApiRequestError";
        this.status = status;
        this.code = code;
    }
}

export function buildApiRequestError(error: unknown): ApiRequestError {
    const message = extractFriendlyErrorMessage(error);
    if (axios.isAxiosError<ApiResponse<null>>(error)) {
        return new ApiRequestError(
            message,
            error.response?.status,
            error.response?.data?.error?.code
        );
    }
    return new ApiRequestError(message);
}

export function withIdempotencyKey(config: AxiosRequestConfig = {}): AxiosRequestConfig {
    const headers = AxiosHeaders.from(config.headers as Record<string, string> | undefined);
    if (!headers.has("Idempotency-Key")) {
        headers.set("Idempotency-Key", crypto.randomUUID());
    }
    return {
        ...config,
        headers
    };
}

export function createIdempotencyHeaders(): Record<string, string> {
    return {
        "Idempotency-Key": crypto.randomUUID()
    };
}

// Request interceptor: Attach Bearer accessToken
apiClient.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        const accessToken = tokenStorage.getAccessToken();
        if (accessToken) {
            config.headers = AxiosHeaders.from(config.headers);
            config.headers.set("Authorization", `Bearer ${accessToken}`);
        }
        return config;
    },
    (error: AxiosError) => Promise.reject(new Error(extractFriendlyErrorMessage(error)))
);

function isAuthEndpoint(url?: string): boolean {
    if (!url) {
        return false;
    }
    return (
        url.includes("/auth/login") ||
        url.includes("/auth/register") ||
        url.includes("/auth/refresh") ||
        url.includes("/auth/logout")
    );
}

// Response interceptor: Single-flight Auto-Refresh Token & normalized Vietnamese errors
apiClient.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ApiResponse<null>>) => {
        const originalRequest = error.config as RetryableRequestConfig | undefined;
        const status = error.response?.status;
        const errorCode = error.response?.data?.error?.code;

        const shouldAttemptRefresh =
            status === 401 &&
            originalRequest &&
            !originalRequest._retry &&
            !isAuthEndpoint(originalRequest.url) &&
            errorCode === "TOKEN_EXPIRED";

        if (shouldAttemptRefresh) {
            const refreshToken = tokenStorage.getRefreshToken();
            if (!refreshToken) {
                tokenStorage.clear();
                redirectToLogin();
                return Promise.reject(buildApiRequestError(error));
            }

            if (isRefreshing) {
                return new Promise<string>((resolve, reject) => {
                    failedQueue.push({ resolve, reject });
                }).then((newAccessToken) => {
                    originalRequest._retry = true;
                    originalRequest.headers = AxiosHeaders.from(originalRequest.headers);
                    originalRequest.headers.set("Authorization", `Bearer ${newAccessToken}`);
                    return apiClient(originalRequest);
                });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const refreshResponse = await axios.post<ApiResponse<TokenRefreshResponse>>(
                    `${API_BASE_URL}/auth/refresh`,
                    { refreshToken },
                    {
                        headers: {
                            "Content-Type": "application/json"
                        },
                        timeout: 15000
                    }
                );

                const tokenPayload = refreshResponse.data?.data;
                if (!tokenPayload?.accessToken || !tokenPayload?.refreshToken) {
                    throw new Error("Không nhận được phiên làm việc mới.");
                }

                tokenStorage.setTokens(tokenPayload.accessToken, tokenPayload.refreshToken);
                processQueue(null, tokenPayload.accessToken);

                originalRequest.headers = AxiosHeaders.from(originalRequest.headers);
                originalRequest.headers.set("Authorization", `Bearer ${tokenPayload.accessToken}`);
                return apiClient(originalRequest);
            } catch {
                const sessionExpiredError = new Error(
                    "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
                );
                processQueue(sessionExpiredError, null);
                tokenStorage.clear();
                redirectToLogin();
                return Promise.reject(sessionExpiredError);
            } finally {
                isRefreshing = false;
            }
        }

        if (status === 401 && !isAuthEndpoint(originalRequest?.url)) {
            tokenStorage.clear();
            redirectToLogin();
        }

        return Promise.reject(buildApiRequestError(error));
    }
);

export default apiClient;
