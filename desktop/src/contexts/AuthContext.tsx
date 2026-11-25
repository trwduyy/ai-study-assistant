import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import authService from "../services/authService";
import tokenStorage from "../services/tokenStorage";
import { AuthResponse, LoginRequest, RegisterRequest, User } from "../types";

interface AuthContextValue {
    user: User | null;
    accessToken: string | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    login: (data: LoginRequest) => Promise<void>;
    register: (data: RegisterRequest) => Promise<void>;
    logout: () => Promise<void>;
    updateUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function saveSession(session: AuthResponse): void {
    tokenStorage.setSession(session.accessToken, session.refreshToken, session.user);
}

export const AuthProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
    const [user, setUser] = useState<User | null>(() => tokenStorage.getUser());
    const [accessToken, setAccessToken] = useState<string | null>(() => tokenStorage.getAccessToken());
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => tokenStorage.subscribe(() => {
        setAccessToken(tokenStorage.getAccessToken());
        setUser(tokenStorage.getUser());
    }), []);

    useEffect(() => {
        const restoreSession = async (): Promise<void> => {
            if (!tokenStorage.getAccessToken()) {
                setIsLoading(false);
                return;
            }
            try {
                tokenStorage.setUser(await authService.getCurrentUser());
            } catch {
                tokenStorage.clear();
            } finally {
                setIsLoading(false);
            }
        };
        void restoreSession();
    }, []);

    const login = useCallback(async (data: LoginRequest): Promise<void> => {
        saveSession(await authService.login(data));
    }, []);

    const register = useCallback(async (data: RegisterRequest): Promise<void> => {
        await authService.register(data);
        await login({ email: data.email, password: data.password });
    }, [login]);

    const logout = useCallback(async (): Promise<void> => {
        const refreshToken = tokenStorage.getRefreshToken();
        try {
            if (refreshToken) {
                await authService.logout(refreshToken);
            }
        } catch {
            // The local session must always be cleared, including offline logout.
        } finally {
            tokenStorage.clear();
        }
    }, []);

    const updateUser = useCallback((updatedUser: User): void => {
        tokenStorage.setUser(updatedUser);
    }, []);

    const value = useMemo<AuthContextValue>(() => ({
        user,
        accessToken,
        isAuthenticated: Boolean(accessToken && user),
        isLoading,
        login,
        register,
        logout,
        updateUser
    }), [accessToken, isLoading, login, logout, register, user, updateUser]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth(): AuthContextValue {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error("useAuth phải được sử dụng bên trong AuthProvider.");
    }
    return context;
}

export default AuthContext;
