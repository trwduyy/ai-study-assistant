import apiClient from "./apiClient";
import {
    ApiResponse,
    AuthResponse,
    ChangePasswordRequest,
    LoginRequest,
    LogoutRequest,
    RegisterRequest,
    TokenRefreshResponse,
    UpdateProfileRequest,
    User
} from "../types";

export const authService = {
    async login(data: LoginRequest): Promise<AuthResponse> {
        const response = await apiClient.post<ApiResponse<AuthResponse>>("/auth/login", data);
        return response.data.data;
    },

    async register(data: RegisterRequest): Promise<User> {
        const response = await apiClient.post<ApiResponse<User>>("/auth/register", data);
        return response.data.data;
    },

    async refresh(refreshToken: string): Promise<TokenRefreshResponse> {
        const response = await apiClient.post<ApiResponse<TokenRefreshResponse>>("/auth/refresh", {
            refreshToken
        });
        return response.data.data;
    },

    async logout(refreshToken: string): Promise<void> {
        const payload: LogoutRequest = { refreshToken };
        await apiClient.post<ApiResponse<null>>("/auth/logout", payload);
    },

    async getCurrentUser(): Promise<User> {
        const response = await apiClient.get<ApiResponse<User>>("/users/me");
        return response.data.data;
    },

    async updateProfile(data: UpdateProfileRequest): Promise<User> {
        const response = await apiClient.put<ApiResponse<User>>("/users/me", data);
        return response.data.data;
    },

    async changePassword(data: ChangePasswordRequest): Promise<void> {
        await apiClient.put<ApiResponse<null>>("/users/me/password", data);
    }
};

export default authService;
