export type UserRole = "STUDENT" | "TEACHER" | "ADMIN";

export type UserStatus = "ACTIVE" | "LOCKED" | "DISABLED";

export interface User {
    id: number;
    email: string;
    fullName: string;
    role: UserRole;
    status: UserStatus;
    avatarUrl: string | null;
    version: number;
    createdAt?: string;
    updatedAt?: string;
}

export interface LoginRequest {
    email: string;
    password: string;
}

export interface RegisterRequest {
    email: string;
    password: string;
    fullName: string;
}

export interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
    user: User;
}

export interface TokenRefreshRequest {
    refreshToken: string;
}

export interface TokenRefreshResponse {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
}

export interface LogoutRequest {
    refreshToken: string;
}

export interface UpdateProfileRequest {
    fullName: string;
    avatarUrl?: string | null;
    version: number;
}

export interface ChangePasswordRequest {
    currentPassword: string;
    newPassword: string;
}
