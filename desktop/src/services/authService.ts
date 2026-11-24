import apiClient from "./apiClient";
import {
    ApiResponse,
    AuthResponse,
    ChangePasswordRequest,
    LoginRequest,
    RegisterRequest,
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

    async getCurrentUser(): Promise<User> {
        const response = await apiClient.get<ApiResponse<User>>("/users/me");
        return response.data.data;
    },

    async changePassword(data: ChangePasswordRequest): Promise<void> {
        await apiClient.put<ApiResponse<null>>("/users/me/password", data);
    },

    async updateProfile(data: UpdateProfileRequest): Promise<User> {
        const response = await apiClient.put<ApiResponse<User>>("/users/me/profile", data);
        return response.data.data;
    }
};

export default authService;
