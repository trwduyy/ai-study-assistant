import React, { createContext, useContext, useEffect, useState } from "react";
import authService from "../services/authService";
import { LoginRequest, RegisterRequest, User } from "../types";

interface AuthContextType {
    user: User | null;
    token: string | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    login: (data: LoginRequest) => Promise<void>;
    register: (data: RegisterRequest) => Promise<void>;
    logout: () => void;
    updateUser: (updatedUser: User) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [user, setUser] = useState<User | null>(() => {
        const savedUser = localStorage.getItem("user");
        return savedUser ? JSON.parse(savedUser) : null;
    });
    const [token, setToken] = useState<string | null>(() => localStorage.getItem("token"));
    const [isLoading, setIsLoading] = useState<boolean>(true);

    useEffect(() => {
        const verifyAuth = async () => {
            const savedToken = localStorage.getItem("token");
            if (savedToken) {
                try {
                    const currentUser = await authService.getCurrentUser();
                    setUser(currentUser);
                    localStorage.setItem("user", JSON.stringify(currentUser));
                } catch {
                    localStorage.removeItem("token");
                    localStorage.removeItem("user");
                    setUser(null);
                    setToken(null);
                }
            }
            setIsLoading(false);
        };

        verifyAuth();
    }, []);

    const login = async (data: LoginRequest) => {
        const response = await authService.login(data);
        setToken(response.token);
        setUser(response.user);
        localStorage.setItem("token", response.token);
        localStorage.setItem("user", JSON.stringify(response.user));
    };

    const register = async (data: RegisterRequest) => {
        await authService.register(data);
        // Automatically login after successful registration
        await login({
            username: data.username,
            password: data.password
        });
    };

    const logout = () => {
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        setUser(null);
        setToken(null);
        window.location.href = "/login";
    };

    const updateUser = (updatedUser: User) => {
        setUser(updatedUser);
        localStorage.setItem("user", JSON.stringify(updatedUser));
    };

    return (
        <AuthContext.Provider
            value={{
                user,
                token,
                isAuthenticated: !!token && !!user,
                isLoading,
                login,
                register,
                logout,
                updateUser
            }}
        >
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = (): AuthContextType => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error("useAuth must be used within an AuthProvider");
    }
    return context;
};

export default AuthContext;
